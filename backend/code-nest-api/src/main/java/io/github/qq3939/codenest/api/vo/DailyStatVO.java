package io.github.qq3939.codenest.api.vo;

import java.time.LocalDate;

public record DailyStatVO(LocalDate date, long dau, long newUsers, long newArticles, long reads, long likes) {
}
