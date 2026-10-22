package io.github.changeme.projecta.service.search;

import io.github.changeme.projecta.api.enums.ArticleStatus;
import io.github.changeme.projecta.api.vo.TagVO;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.service.article.TagService;
import io.github.changeme.projecta.service.article.entity.ArticleContentDO;
import io.github.changeme.projecta.service.article.entity.ArticleDO;
import io.github.changeme.projecta.service.article.mapper.ArticleContentMapper;
import io.github.changeme.projecta.service.article.mapper.ArticleMapper;
import io.github.changeme.projecta.service.user.UserService;
import io.github.changeme.projecta.service.user.entity.UserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 维护 ES 索引：日常由 search.queue 的消息驱动单篇 upsert/删除；造完数据后由管理端触发全量重建。
 * search.enabled=false 时所有方法直接返回，消息照常 ack，不会堆积。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
    private static final int BATCH = 500;

    private final ElasticsearchOperations operations;
    private final AppProperties props;
    private final ArticleMapper articleMapper;
    private final ArticleContentMapper contentMapper;
    private final TagService tagService;
    private final UserService userService;

    public boolean enabled() {
        return props.getSearch().isEnabled();
    }

    IndexCoordinates index() {
        return IndexCoordinates.of(props.getSearch().getIndex());
    }

    public void ensureIndex() {
        IndexOperations ops = operations.indexOps(index());
        if (!ops.exists()) {
            ops.create(Document.parse(read("es/article-settings.json")), Document.parse(read("es/article-mapping.json")));
            log.info("es index {} created", props.getSearch().getIndex());
        }
    }

    /** 按文章 ID upsert：重复消费同一条消息结果不变，天然幂等。 */
    public void upsert(Long articleId) {
        if (!enabled()) {
            return;
        }
        ArticleDO article = articleMapper.selectById(articleId);
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            // 以数据库当前状态为准：消息到达时文章已不可见，就确保索引里也没有它
            delete(articleId);
            return;
        }
        ensureIndex();
        operations.save(toDocs(List.of(article)).get(0), index());
    }

    public void delete(Long articleId) {
        if (!enabled()) {
            return;
        }
        ensureIndex();
        operations.delete(String.valueOf(articleId), index());
    }

    /** 全量重建：删掉旧索引重建，按 ID 游标每批 500 篇批量写入。返回写入的文章数。 */
    public int reindexAll() {
        if (!enabled()) {
            return 0;
        }
        IndexOperations ops = operations.indexOps(index());
        if (ops.exists()) {
            ops.delete();
        }
        ensureIndex();
        long afterId = 0;
        int total = 0;
        while (true) {
            List<ArticleDO> batch = articleMapper.selectPublishedAfterId(afterId, BATCH);
            if (batch.isEmpty()) {
                break;
            }
            operations.save(toDocs(batch), index());
            total += batch.size();
            afterId = batch.get(batch.size() - 1).getId();
        }
        ops.refresh();
        log.info("reindex finished, {} articles", total);
        return total;
    }

    private List<ArticleDoc> toDocs(List<ArticleDO> articles) {
        List<Long> ids = articles.stream().map(ArticleDO::getId).toList();
        Map<Long, String> contents = contentMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(ArticleContentDO::getArticleId, ArticleContentDO::getContent));
        Map<Long, List<TagVO>> tags = tagService.tagsOf(ids);
        Map<Long, UserDO> authors = userService.byIds(articles.stream().map(ArticleDO::getAuthorId).distinct().toList());
        return articles.stream().map(a -> {
            ArticleDoc doc = new ArticleDoc();
            doc.setId(a.getId());
            doc.setTitle(a.getTitle());
            doc.setSummary(a.getSummary());
            doc.setContent(contents.getOrDefault(a.getId(), ""));
            doc.setTags(tags.getOrDefault(a.getId(), List.of()).stream().map(TagVO::name).toList());
            doc.setAuthorId(a.getAuthorId());
            UserDO author = authors.get(a.getAuthorId());
            doc.setAuthorName(author == null ? "" : author.getNickname());
            doc.setPublishTime(a.getPublishTime());
            doc.setStatus(a.getStatus().name());
            return doc;
        }).toList();
    }

    private static String read(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
