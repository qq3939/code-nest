package io.github.qq3939.codenest.service.search;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.qq3939.codenest.api.common.PageResult;
import io.github.qq3939.codenest.api.vo.SearchHitVO;
import io.github.qq3939.codenest.api.vo.TagVO;
import io.github.qq3939.codenest.api.vo.UserBriefVO;
import io.github.qq3939.codenest.service.article.TagService;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全文搜索：ES 开启时用 multi_match + 高亮；关闭（或 ES 出错）时自动降级为 MySQL LIKE，只搜标题和摘要。
 * 降级的意义：搜索是锦上添花的功能，ES 挂了不应该让搜索页直接报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private static final String PRE = "<em>";
    private static final String POST = "</em>";

    private final ElasticsearchOperations operations;
    private final SearchIndexService indexService;
    private final ArticleMapper articleMapper;
    private final UserService userService;
    private final TagService tagService;

    public PageResult<SearchHitVO> search(String keyword, long page, long size) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return PageResult.empty(page, size);
        }
        if (indexService.enabled()) {
            try {
                return searchEs(q, page, size);
            } catch (Exception e) {
                log.warn("es search failed, fallback to mysql: {}", e.getMessage());
            }
        }
        return searchMysql(q, page, size);
    }

    private PageResult<SearchHitVO> searchEs(String q, long page, long size) {
        HighlightFieldParameters fieldParams = HighlightFieldParameters.builder()
                .withFragmentSize(100).withNumberOfFragments(1).build();
        // encoder=html：ES 默认把原文原样拼进高亮片段，标题里的 List<String> 会被前端当成标签删掉；
        // 让 ES 先转义再插入 <em>，和下面 MySQL 降级的 highlight() 约定一致，前端只需要信任 <em>（P-024）
        Highlight highlight = new Highlight(HighlightParameters.builder().withPreTags(PRE).withPostTags(POST)
                .withEncoder("html").build(),
                List.of(new HighlightField("title", HighlightFieldParameters.builder().withNumberOfFragments(0).build()),
                        new HighlightField("summary", HighlightFieldParameters.builder().withNumberOfFragments(0).build()),
                        new HighlightField("content", fieldParams)));
        NativeQuery query = NativeQuery.builder()
                .withQuery(b -> b.bool(bool -> bool
                        .must(m -> m.multiMatch(mm -> mm.query(q).fields("title^3", "summary^2", "content")))
                        .filter(f -> f.term(t -> t.field("status").value("PUBLISHED")))))
                .withHighlightQuery(new HighlightQuery(highlight, ArticleDoc.class))
                .withPageable(PageRequest.of((int) page - 1, (int) size))
                .build();
        SearchHits<ArticleDoc> hits = operations.search(query, ArticleDoc.class, indexService.index());
        List<SearchHitVO> items = hits.getSearchHits().stream().map(SearchService::toVO).toList();
        return new PageResult<>(items, hits.getTotalHits(), page, size);
    }

    private static SearchHitVO toVO(SearchHit<ArticleDoc> hit) {
        ArticleDoc d = hit.getContent();
        return new SearchHitVO(d.getId(), first(hit, "title", HtmlUtils.htmlEscape(d.getTitle())),
                first(hit, "summary", HtmlUtils.htmlEscape(nz(d.getSummary()))),
                first(hit, "content", ""), d.getTags(), d.getAuthorName(), d.getAuthorId(), d.getPublishTime());
    }

    private static String first(SearchHit<ArticleDoc> hit, String field, String fallback) {
        List<String> fragments = hit.getHighlightField(field);
        return fragments.isEmpty() ? fallback : fragments.get(0);
    }

    private PageResult<SearchHitVO> searchMysql(String q, long page, long size) {
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Page<ArticleDO> result = (Page<ArticleDO>) articleMapper.searchByLike(Page.of(page, size), escaped);
        List<ArticleDO> rows = result.getRecords();
        Map<Long, UserBriefVO> authors = userService.briefs(rows.stream().map(ArticleDO::getAuthorId).toList());
        Map<Long, List<TagVO>> tags = tagService.tagsOf(rows.stream().map(ArticleDO::getId).toList());
        List<SearchHitVO> items = rows.stream().map(a -> new SearchHitVO(a.getId(), highlight(a.getTitle(), q),
                highlight(a.getSummary(), q), "", tags.getOrDefault(a.getId(), List.of()).stream().map(TagVO::name).toList(),
                authors.get(a.getAuthorId()).nickname(), a.getAuthorId(), a.getPublishTime())).toList();
        return new PageResult<>(items, result.getTotal(), page, size);
    }

    /** 先做 HTML 转义再插入 <em>：原文里的尖括号不会被当成标签，前端只需要信任 <em>。 */
    static String highlight(String text, String keyword) {
        String escaped = HtmlUtils.htmlEscape(nz(text));
        String escapedKeyword = HtmlUtils.htmlEscape(keyword);
        Matcher m = Pattern.compile(Pattern.quote(escapedKeyword), Pattern.CASE_INSENSITIVE).matcher(escaped);
        return m.replaceAll(r -> PRE + Matcher.quoteReplacement(r.group()) + POST);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
