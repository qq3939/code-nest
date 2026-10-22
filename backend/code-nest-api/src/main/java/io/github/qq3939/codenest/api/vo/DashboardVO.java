package io.github.qq3939.codenest.api.vo;

import java.util.List;

/** days 按日期升序，最后一天是"今天到目前为止"的实时数据。 */
public record DashboardVO(List<DailyStatVO> days, long totalUsers, long totalArticles, long pendingArticles) {
}
