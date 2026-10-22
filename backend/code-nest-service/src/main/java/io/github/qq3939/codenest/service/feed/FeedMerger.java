package io.github.qq3939.codenest.service.feed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多来源归并分页，纯函数，便于单元测试（FeedMergerTest）。
 * 每个来源都已按分数（发布时间）倒序取了 size + offset + 1 条；这里合并、去重、统一排序，
 * 跳过上一页在同一时间戳上已经返回的 offset 条，取 size 条，并算出新游标。
 */
public final class FeedMerger {

    public record Entry(long articleId, long score) {
    }

    public record Result(List<Entry> page, FeedCursor next, boolean hasMore) {
    }

    /** 同分时按文章 ID 倒序，保证每次排序结果完全一致，offset 才有意义。 */
    static final Comparator<Entry> ORDER = Comparator.comparingLong(Entry::score).reversed()
            .thenComparing(Comparator.comparingLong(Entry::articleId).reversed());

    private FeedMerger() {
    }

    public static Result merge(List<List<Entry>> sources, FeedCursor cursor, int size) {
        Map<Long, Entry> unique = new LinkedHashMap<>();
        for (List<Entry> source : sources) {
            for (Entry e : source) {
                // 同一篇文章可能同时出现在收件箱和大 V 发件箱里（例如模式切换后），只保留一份
                unique.merge(e.articleId(), e, (a, b) -> a.score() >= b.score() ? a : b);
            }
        }
        List<Entry> sorted = new ArrayList<>(unique.values());
        sorted.sort(ORDER);

        int skip = 0;
        if (!cursor.isFirst()) {
            List<Entry> rest = new ArrayList<>();
            for (Entry e : sorted) {
                if (e.score() > cursor.maxScore()) {
                    continue;
                }
                if (e.score() == cursor.maxScore() && skip < cursor.offset()) {
                    skip++;
                    continue;
                }
                rest.add(e);
            }
            sorted = rest;
        }
        boolean hasMore = sorted.size() > size;
        List<Entry> page = hasMore ? sorted.subList(0, size) : sorted;
        return new Result(List.copyOf(page), nextCursor(page, cursor), hasMore);
    }

    /** 新游标：本页最后一条的分数，以及本页里和它同分的条数（若与上一游标同分，还要加上之前已跳过的）。 */
    static FeedCursor nextCursor(List<Entry> page, FeedCursor cursor) {
        if (page.isEmpty()) {
            return cursor;
        }
        long last = page.get(page.size() - 1).score();
        int same = (int) page.stream().filter(e -> e.score() == last).count();
        if (last == cursor.maxScore()) {
            same += cursor.offset();
        }
        return new FeedCursor(last, same);
    }
}
