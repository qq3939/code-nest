package io.github.changeme.projecta.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 关注流三种模式（SPEC 9.3 验收）：push / pull / hybrid 下同一个用户分页读到的结果完全一致，
 * 不重复、不遗漏；取关和删文在读时被过滤。测试配置把大 V 阈值调成 3 个粉丝。
 */
class FeedIT extends IntegrationTestBase {

    @AfterEach
    void restoreDefault() {
        ok(call(HttpMethod.PUT, "/api/admin/dev/strategies", admin(), Map.of("feedMode", "hybrid")));
    }

    @Test
    void allModesReturnSameSequenceWithoutDuplicates() throws Exception {
        TestUser reader = newUser();
        TestUser normal = newUser();
        TestUser bigV = newUser();
        follow(reader, normal);
        follow(reader, bigV);
        for (int i = 0; i < 3; i++) {
            follow(newUser(), bigV);
        }

        List<Long> published = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            published.add(publishArticle(normal, "普通作者 " + i));
            Thread.sleep(5);
            published.add(publishArticle(bigV, "大V " + i));
            Thread.sleep(5);
        }
        List<Long> expected = new ArrayList<>(published);
        java.util.Collections.reverse(expected);

        for (String mode : List.of("hybrid", "push", "pull")) {
            ok(call(HttpMethod.PUT, "/api/admin/dev/strategies", admin(), Map.of("feedMode", mode)));
            await().atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> assertThat(readAll(reader, 3)).as("mode=" + mode).isEqualTo(expected));
        }
    }

    @Test
    void unfollowAndDeleteAreFiltered() throws Exception {
        TestUser reader = newUser();
        TestUser a = newUser();
        TestUser b = newUser();
        follow(reader, a);
        follow(reader, b);
        long a1 = publishArticle(a, "A 的文章");
        Thread.sleep(5);
        long b1 = publishArticle(b, "B 的文章 1");
        Thread.sleep(5);
        long b2 = publishArticle(b, "B 的文章 2");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(readAll(reader, 10)).containsExactly(b2, b1, a1));

        ok(call(HttpMethod.DELETE, "/api/users/" + a.id() + "/follow", reader, null));
        ok(call(HttpMethod.DELETE, "/api/articles/" + b1, b, null));
        assertThat(readAll(reader, 10)).containsExactly(b2);
    }

    private void follow(TestUser follower, TestUser followee) {
        ok(call(HttpMethod.POST, "/api/users/" + followee.id() + "/follow", follower, null));
    }

    private List<Long> readAll(TestUser user, int size) {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 50; guard++) {
            String path = "/api/feed?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = ok(call(HttpMethod.GET, path, user, null));
            page.path("items").forEach(n -> ids.add(n.path("id").asLong()));
            if (!page.path("hasMore").asBoolean()) {
                break;
            }
            cursor = page.path("nextCursor").asText();
        }
        return ids;
    }
}
