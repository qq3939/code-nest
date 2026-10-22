package io.github.changeme.projecta.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 文章生命周期、防重复提交、敏感词三级处理、互动与通知、限流（SPEC M2/M3 验收）。 */
class ArticleFlowIT extends IntegrationTestBase {

    @Test
    void draftIsPrivateUntilPublished() {
        TestUser author = newUser();
        TestUser reader = newUser();
        long id = createDraft(author, "草稿标题", "草稿正文内容，还没有发布。");

        assertThat(code(call(HttpMethod.GET, "/api/articles/" + id, reader, null))).isEqualTo(30001);
        ok(call(HttpMethod.GET, "/api/articles/" + id + "/edit", author, null));
        // 别人的草稿按"不存在"处理，不暴露这篇文章是否存在
        assertThat(code(call(HttpMethod.GET, "/api/articles/" + id + "/edit", reader, null))).isEqualTo(30001);

        String token = submitToken(author);
        assertThat(ok(publish(author, id, token)).path("status").asText()).isEqualTo("PUBLISHED");
        JsonNode detail = ok(call(HttpMethod.GET, "/api/articles/" + id, reader, null));
        assertThat(detail.path("title").asText()).isEqualTo("草稿标题");
        assertThat(detail.path("summary").asText()).isNotBlank();
    }

    @Test
    void sameSubmitTokenOnlyWorksOnce() {
        TestUser author = newUser();
        long id = createDraft(author, "重复提交", "测试同一个令牌提交两次。");
        String token = submitToken(author);
        ok(publish(author, id, token));
        ResponseEntity<JsonNode> again = publish(author, id, token);
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(code(again)).isEqualTo(10006);
    }

    @Test
    void sensitiveWordsBlockReviewAndReplace() {
        TestUser author = newUser();

        long blocked = createDraft(author, "违禁", "正文里有违禁示例词。");
        assertThat(code(publish(author, blocked, submitToken(author)))).isEqualTo(30003);

        long review = createDraft(author, "待审", "正文里有待审示例词。");
        assertThat(ok(publish(author, review, submitToken(author))).path("status").asText()).isEqualTo("PENDING");
        TestUser other = newUser();
        assertThat(code(call(HttpMethod.GET, "/api/articles/" + review, other, null))).isEqualTo(30001);
        TestUser admin = admin();
        ok(call(HttpMethod.PUT, "/api/admin/articles/" + review + "/approve", admin, Map.of("reason", "ok")));
        ok(call(HttpMethod.GET, "/api/articles/" + review, other, null));

        long replaced = createDraft(author, "替换", "正文里有替换示例词。");
        ok(publish(author, replaced, submitToken(author)));
        String content = ok(call(HttpMethod.GET, "/api/articles/" + replaced, other, null)).path("content").asText();
        assertThat(content).doesNotContain("替换示例词").contains("*****");
    }

    @Test
    void likeCommentAndNotify() {
        TestUser author = newUser();
        TestUser fan = newUser();
        long id = publishArticle(author, "互动文章");

        ok(call(HttpMethod.POST, "/api/articles/" + id + "/like", fan, null));
        // 重复点赞是幂等的
        ok(call(HttpMethod.POST, "/api/articles/" + id + "/like", fan, null));
        ok(call(HttpMethod.POST, "/api/articles/" + id + "/collect", fan, null));
        JsonNode top = ok(call(HttpMethod.POST, "/api/articles/" + id + "/comments", fan, Map.of("content", "写得好")));
        ok(call(HttpMethod.POST, "/api/articles/" + id + "/comments", author,
                Map.of("content", "谢谢", "replyCommentId", top.path("id").asText())));

        JsonNode detail = ok(call(HttpMethod.GET, "/api/articles/" + id, fan, null));
        assertThat(detail.path("liked").asBoolean()).isTrue();
        assertThat(detail.path("collected").asBoolean()).isTrue();
        assertThat(detail.path("counts").path("like").asLong()).isEqualTo(1);
        assertThat(detail.path("counts").path("comment").asLong()).isEqualTo(2);

        JsonNode comments = ok(call(HttpMethod.GET, "/api/articles/" + id + "/comments", fan, null));
        assertThat(comments.path("items")).hasSize(1);
        JsonNode replies = ok(call(HttpMethod.GET, "/api/articles/" + id + "/comments?parentId=" + top.path("id").asText(), fan, null));
        assertThat(replies.path("items")).hasSize(1);

        // 点赞、收藏、评论 → 作者 3 条；回复 → 粉丝 1 条（异步消费，等一会）
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(ok(call(HttpMethod.GET, "/api/notifications/unread-count", author, null)).path("total").asLong()).isEqualTo(3);
            assertThat(ok(call(HttpMethod.GET, "/api/notifications/unread-count", fan, null)).path("total").asLong()).isEqualTo(1);
        });
        ok(call(HttpMethod.POST, "/api/notifications/read", author, Map.of("all", true)));
        assertThat(ok(call(HttpMethod.GET, "/api/notifications/unread-count", author, null)).path("total").asLong()).isZero();

        ok(call(HttpMethod.DELETE, "/api/articles/" + id + "/like", fan, null));
        assertThat(ok(call(HttpMethod.GET, "/api/articles/" + id, fan, null)).path("counts").path("like").asLong()).isZero();
    }

    @Test
    void deletedArticleDisappears() {
        TestUser author = newUser();
        long id = publishArticle(author, "将被删除");
        ok(call(HttpMethod.DELETE, "/api/articles/" + id, author, null));
        assertThat(code(call(HttpMethod.GET, "/api/articles/" + id, null, null))).isEqualTo(30001);
    }

    @Test
    void likeIsRateLimited() {
        TestUser author = newUser();
        TestUser fan = newUser();
        long id = publishArticle(author, "限流文章");
        int limited = 0;
        for (int i = 0; i < 10; i++) {
            HttpMethod m = i % 2 == 0 ? HttpMethod.POST : HttpMethod.DELETE;
            ResponseEntity<JsonNode> r = call(m, "/api/articles/" + id + "/like", fan, null);
            if (r.getStatusCode().value() == 429) {
                assertThat(code(r)).isEqualTo(10005);
                limited++;
            }
        }
        assertThat(limited).isPositive();
    }
}
