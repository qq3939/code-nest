package io.github.qq3939.codenest.service.article;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.common.PageResult;
import io.github.qq3939.codenest.api.enums.ArticleStatus;
import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import io.github.qq3939.codenest.api.vo.ArticleDetailVO;
import io.github.qq3939.codenest.api.vo.ArticleListItemVO;
import io.github.qq3939.codenest.api.vo.HotArticleRankVO;
import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.core.security.LoginUser;
import io.github.qq3939.codenest.service.article.entity.ArticleContentDO;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleContentMapper;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.cache.CacheService;
import io.github.qq3939.codenest.service.count.CountField;
import io.github.qq3939.codenest.service.count.CountService;
import io.github.qq3939.codenest.service.follow.FollowService;
import io.github.qq3939.codenest.service.interaction.CollectService;
import io.github.qq3939.codenest.service.interaction.LikeService;
import io.github.qq3939.codenest.service.rank.RankService;
import io.github.qq3939.codenest.service.stat.StatService;
import io.github.qq3939.codenest.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章的读操作：详情（走缓存）、最新列表、热门列表、个人主页的文章和收藏。
 * 和 ArticleService（写）分开，是为了打破依赖环：详情要查点赞/收藏/关注状态，而这些服务又依赖 ArticleService。
 */
@Service
@RequiredArgsConstructor
public class ArticleQueryService {
    private static final int HOT_LIST_SIZE = 200;

    private final ArticleMapper articleMapper;
    private final ArticleContentMapper contentMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final UserService userService;
    private final CountService countService;
    private final LikeService likeService;
    private final CollectService collectService;
    private final FollowService followService;
    private final RankService rankService;
    private final StatService statService;
    private final CacheService cacheService;
    private final ArticleAssembler assembler;
    private final RedisKeys keys;
    private final AppProperties props;
    private final ObjectMapper objectMapper;

    /** 详情：缓存里拿文章本体（不含计数），计数单独读，阅读数在这里 +1。 */
    public ArticleDetailVO detail(Long articleId, LoginUser viewer) {
        JavaType type = objectMapper.constructType(ArticleCacheDTO.class);
        ArticleCacheDTO a = cacheService.get(keys.articleCache(articleId), type, props.getCache().getArticleTtl(),
                () -> loadForCache(articleId)).orElseThrow(() -> new BizException(ErrorCode.ARTICLE_NOT_FOUND));
        Long viewerId = viewer == null ? null : viewer.userId();
        boolean isAuthor = a.authorId().equals(viewerId);
        boolean published = ArticleStatus.PUBLISHED.name().equals(a.status());
        if (!published && !isAuthor && (viewer == null || !viewer.isAdmin())) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        if (published) {
            countService.increase(articleId, CountField.READ, 1);
            statService.incrDaily("reads", 1);
        }
        ArticleCountVO counts = countService.get(articleId);
        return new ArticleDetailVO(a.id(), a.title(), a.summary(), a.coverUrl(), a.content(), a.category(), a.tags(),
                a.author(), a.status(), a.publishTime(), a.updateTime(), counts,
                likeService.isArticleLiked(viewerId, articleId), collectService.isCollected(viewerId, articleId),
                followService.isFollowing(viewerId, a.authorId()));
    }

    /** 缓存未命中时的加载逻辑；返回 null 表示不存在，CacheService 会缓存空值防穿透。 */
    ArticleCacheDTO loadForCache(Long articleId) {
        ArticleDO a = articleMapper.selectById(articleId);
        if (a == null) {
            return null;
        }
        ArticleContentDO content = contentMapper.selectById(articleId);
        return new ArticleCacheDTO(a.getId(), a.getAuthorId(), a.getTitle(), a.getSummary(), a.getCoverUrl(),
                content == null ? "" : content.getContent(), a.getStatus().name(), a.getPublishTime(), a.getUpdateTime(),
                categoryService.asMap().get(a.getCategoryId()), tagService.tagsOf(List.of(articleId)).getOrDefault(articleId, List.of()),
                userService.brief(a.getAuthorId()));
    }

    public PageResult<ArticleListItemVO> latest(long page, long size, Long categoryId, Long tagId) {
        Page<ArticleDO> result = (Page<ArticleDO>) articleMapper.selectPublishedPage(Page.of(page, size), categoryId, tagId);
        return new PageResult<>(assembler.toListItems(result.getRecords()), result.getTotal(), page, size);
    }

    /**
     * 首页热门：前 200 篇的列表放在 cache:hot-list 里，用逻辑过期防击穿；
     * 分类/标签筛选在这 200 篇里做，计数每次单独刷新（列表缓存 1 分钟，计数要实时一些）。
     */
    public PageResult<ArticleListItemVO> hot(long page, long size, Long categoryId, Long tagId) {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, ArticleListItemVO.class);
        List<ArticleListItemVO> all = cacheService.getLogical(keys.hotListCache(), listType,
                props.getCache().getHotListLogicalTtl(), this::buildHotList);
        List<ArticleListItemVO> filtered = all.stream()
                .filter(a -> categoryId == null || (a.category() != null && categoryId.equals(a.category().id())))
                .filter(a -> tagId == null || a.tags().stream().anyMatch(t -> tagId.equals(t.id())))
                .toList();
        int from = (int) Math.min((page - 1) * size, filtered.size());
        int to = (int) Math.min(from + size, filtered.size());
        List<ArticleListItemVO> slice = filtered.subList(from, to);
        Map<Long, ArticleCountVO> counts = countService.batchGet(slice.stream().map(ArticleListItemVO::id).toList());
        List<ArticleListItemVO> withCounts = slice.stream().map(a -> new ArticleListItemVO(a.id(), a.title(), a.summary(),
                a.coverUrl(), a.category(), a.tags(), a.author(), a.status(), a.publishTime(), a.updateTime(),
                counts.getOrDefault(a.id(), a.counts()))).toList();
        return new PageResult<>(withCounts, filtered.size(), page, size);
    }

    List<ArticleListItemVO> buildHotList() {
        List<RankService.ScoredArticle> ids = rankService.hotArticleIds(HOT_LIST_SIZE);
        return byIdsInOrder(ids.stream().map(RankService.ScoredArticle::id).toList());
    }

    public List<HotArticleRankVO> hotRank(int limit) {
        List<RankService.ScoredArticle> scored = rankService.hotArticleIds(limit);
        Map<Long, ArticleListItemVO> items = byIdsInOrder(scored.stream().map(RankService.ScoredArticle::id).toList())
                .stream().collect(Collectors.toMap(ArticleListItemVO::id, Function.identity()));
        List<HotArticleRankVO> result = new ArrayList<>();
        int rank = 1;
        for (RankService.ScoredArticle s : scored) {
            ArticleListItemVO item = items.get(s.id());
            if (item != null) {
                result.add(new HotArticleRankVO(rank++, item, s.score()));
            }
        }
        return result;
    }

    /** 个人主页"文章"标签页：本人能看到草稿、待审、已下架，其他人只看到已发布。 */
    public PageResult<ArticleListItemVO> byAuthor(Long authorId, Long viewerId, long page, long size) {
        boolean self = authorId.equals(viewerId);
        Page<ArticleDO> result = articleMapper.selectPage(Page.of(page, size), Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getAuthorId, authorId)
                .eq(!self, ArticleDO::getStatus, ArticleStatus.PUBLISHED)
                .orderByDesc(ArticleDO::getUpdateTime));
        return new PageResult<>(assembler.toListItems(result.getRecords()), result.getTotal(), page, size);
    }

    public PageResult<ArticleListItemVO> collections(Long userId, long page, long size) {
        Page<ArticleDO> result = (Page<ArticleDO>) articleMapper.selectCollectedPage(Page.of(page, size), userId);
        return new PageResult<>(assembler.toListItems(result.getRecords()), result.getTotal(), page, size);
    }

    public PageResult<ArticleListItemVO> adminList(ArticleStatus status, String keyword, long page, long size) {
        Page<ArticleDO> result = articleMapper.selectPage(Page.of(page, size), Wrappers.<ArticleDO>lambdaQuery()
                .eq(status != null, ArticleDO::getStatus, status)
                .like(keyword != null && !keyword.isBlank(), ArticleDO::getTitle, keyword)
                .orderByDesc(ArticleDO::getUpdateTime));
        return new PageResult<>(assembler.toListItems(result.getRecords()), result.getTotal(), page, size);
    }

    /** 编辑器回显：作者本人读自己的文章（含草稿），不走缓存、不加阅读数。 */
    public ArticleDetailVO forEdit(Long userId, Long articleId) {
        ArticleCacheDTO a = loadForCache(articleId);
        if (a == null || !a.authorId().equals(userId)) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        return new ArticleDetailVO(a.id(), a.title(), a.summary(), a.coverUrl(), a.content(), a.category(), a.tags(),
                a.author(), a.status(), a.publishTime(), a.updateTime(), countService.get(articleId), false, false, false);
    }

    public List<ArticleListItemVO> byIdsInOrder(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, ArticleDO> rows = articleMapper.selectByIds(ids).stream()
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .collect(Collectors.toMap(ArticleDO::getId, Function.identity()));
        return assembler.toListItems(ids.stream().map(rows::get).filter(a -> a != null).toList());
    }
}
