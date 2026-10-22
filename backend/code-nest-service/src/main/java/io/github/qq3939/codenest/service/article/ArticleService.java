package io.github.qq3939.codenest.service.article;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.dto.ArticleSaveReq;
import io.github.qq3939.codenest.api.enums.ArticleStatus;
import io.github.qq3939.codenest.api.enums.SensitiveLevel;
import io.github.qq3939.codenest.api.event.EventTypes;
import io.github.qq3939.codenest.api.event.Payloads;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.core.trie.SensitiveTrie;
import io.github.qq3939.codenest.core.util.Times;
import io.github.qq3939.codenest.service.article.entity.ArticleContentDO;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleContentMapper;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.cache.CacheService;
import io.github.qq3939.codenest.service.count.mapper.ArticleStatMapper;
import io.github.qq3939.codenest.service.outbox.OutboxService;
import io.github.qq3939.codenest.service.sensitive.SensitiveWordService;
import io.github.qq3939.codenest.service.user.UserStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章的写操作和状态流转：
 * <pre>
 *   DRAFT --发布--> PUBLISHED（或命中审核词 -> PENDING）
 *   PENDING --通过--> PUBLISHED，--驳回--> DRAFT
 *   PUBLISHED --修改命中审核词--> PENDING，--下架--> OFFLINE --恢复--> PUBLISHED
 * </pre>
 * 状态变化涉及的下游（Feed、搜索、排行、通知）都通过 outbox 事件解耦，和文章数据在同一个事务里提交。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleMapper articleMapper;
    private final ArticleContentMapper contentMapper;
    private final ArticleStatMapper articleStatMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final SensitiveWordService sensitiveWordService;
    private final SubmitTokenService submitTokenService;
    private final OutboxService outboxService;
    private final CacheService cacheService;
    private final ArticleStatusCache statusCache;
    private final UserStatService userStatService;
    private final RedisKeys keys;

    public ArticleDO require(Long articleId) {
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    public ArticleDO requirePublished(Long articleId) {
        ArticleDO article = require(articleId);
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return article;
    }

    /** 新建一篇草稿。编辑器第一次保存时调用，之后用 saveDraft 自动保存。 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, ArticleSaveReq req) {
        if (req.categoryId() != null) {
            categoryService.requireExists(req.categoryId());
        }
        ArticleDO article = new ArticleDO();
        article.setAuthorId(userId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setDeleted(0);
        applyFields(article, req);
        articleMapper.insert(article);
        ArticleContentDO content = new ArticleContentDO();
        content.setArticleId(article.getId());
        content.setContent(nullToEmpty(req.content()));
        contentMapper.insert(content);
        articleStatMapper.insertIgnore(article.getId());
        tagService.replaceArticleTags(article.getId(), tagService.ensureTags(req.tags()));
        return article.getId();
    }

    /** 自动保存：只允许草稿，不做发布校验，避免写到一半因为缺字段保存失败。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long userId, Long articleId, ArticleSaveReq req) {
        ArticleDO article = requireOwned(userId, articleId);
        if (article.getStatus() != ArticleStatus.DRAFT) {
            throw new BizException(ErrorCode.ARTICLE_STATUS_INVALID, "已发布的文章请使用保存修改");
        }
        if (req.categoryId() != null) {
            categoryService.requireExists(req.categoryId());
        }
        saveFields(article, req);
        evictCaches(articleId);
    }

    /** 发布草稿。Idempotency-Token 在最前面消费，保证并发的重复请求只有一个能往下走。 */
    @Transactional(rollbackFor = Exception.class)
    public ArticleStatus publish(Long userId, Long articleId, ArticleSaveReq req, String idempotencyToken) {
        submitTokenService.consume(idempotencyToken);
        ArticleDO article = requireOwned(userId, articleId);
        if (article.getStatus() != ArticleStatus.DRAFT) {
            throw new BizException(ErrorCode.ARTICLE_STATUS_INVALID);
        }
        if (req != null) {
            applyFields(article, req);
        }
        String content = req != null && req.content() != null ? req.content() : loadContent(articleId);
        checkPublishable(article, content);
        ReviewResult review = review(article, content);
        content = review.content();
        if (review.needReview()) {
            article.setStatus(ArticleStatus.PENDING);
        } else {
            article.setStatus(ArticleStatus.PUBLISHED);
            article.setPublishTime(Times.now());
        }
        persist(article, content, req == null ? null : req.tags());
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            onPublished(article, false);
        } else {
            notifyAuthor(article.getAuthorId(), "你的文章《" + article.getTitle() + "》包含需要审核的内容，审核通过后会自动发布");
        }
        evictCaches(articleId);
        return article.getStatus();
    }

    /** 修改已发布（或待审、已下架）的文章。已发布的文章改完命中审核词，会被撤下进入待审。 */
    @Transactional(rollbackFor = Exception.class)
    public ArticleStatus update(Long userId, Long articleId, ArticleSaveReq req) {
        ArticleDO article = requireOwned(userId, articleId);
        if (article.getStatus() == ArticleStatus.DRAFT) {
            saveDraft(userId, articleId, req);
            return ArticleStatus.DRAFT;
        }
        applyFields(article, req);
        String content = req.content() != null ? req.content() : loadContent(articleId);
        checkPublishable(article, content);
        ReviewResult review = review(article, content);
        ArticleStatus before = article.getStatus();
        if (before == ArticleStatus.PUBLISHED && review.needReview()) {
            article.setStatus(ArticleStatus.PENDING);
        }
        persist(article, review.content(), req.tags());
        if (before == ArticleStatus.PUBLISHED) {
            if (article.getStatus() == ArticleStatus.PENDING) {
                onRemoved(article);
                notifyAuthor(article.getAuthorId(), "你修改后的文章《" + article.getTitle() + "》需要重新审核");
            } else {
                outboxService.save(EventTypes.ARTICLE_UPDATED, Payloads.ArticleEvent.of(article.getId(), article.getAuthorId()));
            }
        }
        evictCaches(articleId);
        return article.getStatus();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long articleId, boolean asAdmin) {
        ArticleDO article = asAdmin ? require(articleId) : requireOwned(userId, articleId);
        articleMapper.deleteById(articleId);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            onRemoved(article);
        }
        evictCaches(articleId);
    }

    // ---------------- 管理端状态流转 ----------------

    @Transactional(rollbackFor = Exception.class)
    public ArticleDO approve(Long articleId) {
        ArticleDO article = requireStatus(articleId, ArticleStatus.PENDING);
        boolean restored = article.getPublishTime() != null;
        article.setStatus(ArticleStatus.PUBLISHED);
        if (!restored) {
            article.setPublishTime(Times.now());
        }
        articleMapper.updateById(article);
        onPublished(article, restored);
        notifyAuthor(article.getAuthorId(), "你的文章《" + article.getTitle() + "》已通过审核并发布");
        evictCaches(articleId);
        return article;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleDO reject(Long articleId, String reason) {
        ArticleDO article = requireStatus(articleId, ArticleStatus.PENDING);
        article.setStatus(ArticleStatus.DRAFT);
        articleMapper.updateById(article);
        notifyAuthor(article.getAuthorId(), "你的文章《" + article.getTitle() + "》未通过审核，已退回草稿。原因：" + reasonText(reason));
        evictCaches(articleId);
        return article;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleDO offline(Long articleId, String reason) {
        ArticleDO article = requireStatus(articleId, ArticleStatus.PUBLISHED);
        article.setStatus(ArticleStatus.OFFLINE);
        articleMapper.updateById(article);
        onRemoved(article);
        notifyAuthor(article.getAuthorId(), "你的文章《" + article.getTitle() + "》已被下架。原因：" + reasonText(reason));
        evictCaches(articleId);
        return article;
    }

    @Transactional(rollbackFor = Exception.class)
    public ArticleDO restore(Long articleId) {
        ArticleDO article = requireStatus(articleId, ArticleStatus.OFFLINE);
        article.setStatus(ArticleStatus.PUBLISHED);
        articleMapper.updateById(article);
        onPublished(article, true);
        notifyAuthor(article.getAuthorId(), "你的文章《" + article.getTitle() + "》已恢复上架");
        evictCaches(articleId);
        return article;
    }

    // ---------------- 内部方法 ----------------

    private void onPublished(ArticleDO article, boolean restored) {
        userStatService.increaseArticle(article.getAuthorId(), 1);
        outboxService.save(EventTypes.ARTICLE_PUBLISHED,
                new Payloads.ArticleEvent(article.getId(), article.getAuthorId(), restored));
    }

    private void onRemoved(ArticleDO article) {
        userStatService.increaseArticle(article.getAuthorId(), -1);
        outboxService.save(EventTypes.ARTICLE_REMOVED, Payloads.ArticleEvent.of(article.getId(), article.getAuthorId()));
    }

    private void notifyAuthor(Long authorId, String content) {
        outboxService.save(EventTypes.SYSTEM_NOTICE, new Payloads.SystemNoticeEvent(authorId, content));
    }

    private record ReviewResult(boolean needReview, String content) {
    }

    /** 敏感词三级处理：拦截级直接拒绝；审核级进入待审；替换级把命中部分换成 *。 */
    private ReviewResult review(ArticleDO article, String content) {
        String all = article.getTitle() + "\n" + article.getSummary() + "\n" + content;
        List<SensitiveTrie.Hit> hits = sensitiveWordService.check(all);
        if (hits.stream().anyMatch(h -> h.level() == SensitiveLevel.BLOCK)) {
            String words = hits.stream().filter(h -> h.level() == SensitiveLevel.BLOCK).map(SensitiveTrie.Hit::word)
                    .distinct().limit(3).reduce((a, b) -> a + "、" + b).orElse("");
            throw new BizException(ErrorCode.ARTICLE_BLOCKED_WORD, "内容包含违禁词：" + words);
        }
        boolean needReview = hits.stream().anyMatch(h -> h.level() == SensitiveLevel.REVIEW);
        article.setTitle(sensitiveWordService.replace(article.getTitle(), SensitiveLevel.REPLACE));
        article.setSummary(sensitiveWordService.replace(article.getSummary(), SensitiveLevel.REPLACE));
        return new ReviewResult(needReview, sensitiveWordService.replace(content, SensitiveLevel.REPLACE));
    }

    private void checkPublishable(ArticleDO article, String content) {
        if (isBlank(article.getTitle())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "标题不能为空");
        }
        if (isBlank(content)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "正文不能为空");
        }
        categoryService.requireExists(article.getCategoryId());
        if (isBlank(article.getSummary())) {
            article.setSummary(autoSummary(content));
        }
    }

    private void saveFields(ArticleDO article, ArticleSaveReq req) {
        applyFields(article, req);
        persist(article, req.content(), req.tags());
    }

    private void persist(ArticleDO article, String content, List<String> tags) {
        articleMapper.updateById(article);
        if (content != null) {
            ArticleContentDO row = new ArticleContentDO();
            row.setArticleId(article.getId());
            row.setContent(content);
            contentMapper.updateById(row);
        }
        if (tags != null) {
            tagService.replaceArticleTags(article.getId(), tagService.ensureTags(tags));
        }
    }

    private static void applyFields(ArticleDO article, ArticleSaveReq req) {
        if (req.title() != null) {
            article.setTitle(req.title().trim());
        }
        if (req.summary() != null) {
            article.setSummary(req.summary().trim());
        }
        if (req.coverUrl() != null) {
            article.setCoverUrl(req.coverUrl());
        }
        if (req.categoryId() != null) {
            article.setCategoryId(req.categoryId());
        }
        if (article.getTitle() == null) {
            article.setTitle("");
        }
        if (article.getSummary() == null) {
            article.setSummary("");
        }
        if (article.getCoverUrl() == null) {
            article.setCoverUrl("");
        }
        if (article.getCategoryId() == null) {
            article.setCategoryId(0L);
        }
    }

    private String loadContent(Long articleId) {
        ArticleContentDO row = contentMapper.selectById(articleId);
        return row == null ? "" : row.getContent();
    }

    private ArticleDO requireOwned(Long userId, Long articleId) {
        ArticleDO article = require(articleId);
        if (!article.getAuthorId().equals(userId)) {
            throw new BizException(ErrorCode.ARTICLE_NO_PERMISSION);
        }
        return article;
    }

    private ArticleDO requireStatus(Long articleId, ArticleStatus expected) {
        ArticleDO article = require(articleId);
        if (article.getStatus() != expected) {
            throw new BizException(ErrorCode.ARTICLE_STATUS_INVALID);
        }
        return article;
    }

    private void evictCaches(Long articleId) {
        cacheService.evict(keys.articleCache(articleId));
        statusCache.evict(articleId);
    }

    /** 没填摘要时取正文前 120 个字符，并去掉常见的 Markdown 标记。 */
    static String autoSummary(String markdown) {
        String text = markdown.replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*`_~\\-|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 120 ? text.substring(0, 120) + "…" : text;
    }

    private static String reasonText(String reason) {
        return isBlank(reason) ? "未说明" : reason;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
