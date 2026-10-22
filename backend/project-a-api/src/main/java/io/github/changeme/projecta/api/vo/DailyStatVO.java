package io.github.changeme.projecta.api.vo;

import java.time.LocalDate;

public record DailyStatVO(LocalDate date, long dau, long newUsers, long newArticles, long reads, long likes) {
}
