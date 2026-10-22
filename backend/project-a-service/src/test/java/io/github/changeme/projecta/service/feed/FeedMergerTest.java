package io.github.changeme.projecta.service.feed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FeedMergerTest {

    private static FeedMerger.Entry e(long id, long score) {
        return new FeedMerger.Entry(id, score);
    }

    /** 模拟 Redis：每个来源按分数倒序，取 score <= max 的前 count 条。 */
    private static List<FeedMerger.Entry> fetch(List<FeedMerger.Entry> source, FeedCursor cursor, int count) {
        return source.stream().filter(x -> x.score() <= cursor.maxScore()).sorted(FeedMerger.ORDER).limit(count).toList();
    }

    private static List<Long> pageThrough(List<List<FeedMerger.Entry>> sources, int size) {
        List<Long> seen = new ArrayList<>();
        FeedCursor cursor = FeedCursor.FIRST;
        for (int guard = 0; guard < 1000; guard++) {
            FeedCursor c = cursor;
            List<List<FeedMerger.Entry>> fetched = sources.stream().map(s -> fetch(s, c, size + c.offset() + 1)).toList();
            FeedMerger.Result r = FeedMerger.merge(fetched, cursor, size);
            r.page().forEach(x -> seen.add(x.articleId()));
            if (!r.hasMore()) {
                return seen;
            }
            // 游标要能经过编码/解码原样传回
            cursor = FeedCursor.decode(r.next().encode());
        }
        throw new AssertionError("paging did not terminate");
    }

    @Test
    void singleSourceWithTiesNoDuplicateNoMissing() {
        List<FeedMerger.Entry> inbox = new ArrayList<>();
        for (long i = 1; i <= 23; i++) {
            inbox.add(e(i, 1000 + i / 5)); // 每 5 条同一个时间戳，制造大量并列
        }
        List<Long> ids = pageThrough(List.of(inbox), 4);
        assertThat(ids).hasSize(23).doesNotHaveDuplicates();
        assertThat(ids).containsExactlyElementsOf(inbox.stream().sorted(FeedMerger.ORDER).map(FeedMerger.Entry::articleId).toList());
    }

    @Test
    void multipleSourcesMergedInOrderAndDeduplicated() {
        List<FeedMerger.Entry> inbox = List.of(e(1, 100), e(2, 90), e(3, 90), e(4, 50));
        List<FeedMerger.Entry> bigV1 = List.of(e(10, 95), e(11, 90), e(12, 10));
        List<FeedMerger.Entry> bigV2 = List.of(e(20, 90), e(3, 90), e(21, 1)); // 3 同时在收件箱里
        List<Long> ids = pageThrough(List.of(inbox, bigV1, bigV2), 2);
        assertThat(ids).containsExactly(1L, 10L, 20L, 11L, 3L, 2L, 4L, 12L, 21L);
    }

    @Test
    void randomizedSourcesNeverDuplicateOrMiss() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            List<List<FeedMerger.Entry>> sources = new ArrayList<>();
            Set<Long> all = new HashSet<>();
            int sourceCount = 1 + random.nextInt(4);
            long nextId = 1;
            for (int s = 0; s < sourceCount; s++) {
                List<FeedMerger.Entry> list = new ArrayList<>();
                int n = random.nextInt(30);
                for (int i = 0; i < n; i++) {
                    long id = nextId++;
                    list.add(e(id, 1 + random.nextInt(8)));
                    all.add(id);
                }
                sources.add(list);
            }
            List<Long> ids = pageThrough(sources, 1 + random.nextInt(6));
            assertThat(ids).doesNotHaveDuplicates();
            assertThat(new HashSet<>(ids)).isEqualTo(all);
        }
    }

    @Test
    void emptySources() {
        FeedMerger.Result r = FeedMerger.merge(List.of(List.of(), List.of()), FeedCursor.FIRST, 10);
        assertThat(r.page()).isEmpty();
        assertThat(r.hasMore()).isFalse();
    }

    @Test
    void orderComparatorIsScoreDescThenIdDesc() {
        List<FeedMerger.Entry> list = new ArrayList<>(List.of(e(1, 5), e(3, 5), e(2, 9)));
        list.sort(FeedMerger.ORDER);
        assertThat(list).extracting(FeedMerger.Entry::articleId).containsExactly(2L, 3L, 1L);
    }
}
