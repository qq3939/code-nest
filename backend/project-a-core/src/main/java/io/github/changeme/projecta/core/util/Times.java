package io.github.changeme.projecta.core.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 时间工具。数据库列是 DATETIME(3)（毫秒），而 LocalDateTime.now() 带纳秒，
 * MySQL 写入时会四舍五入到毫秒——内存里的值和库里的值就差了一点点，拿来做游标比较会出错。
 * 所以写库的时间统一先截断到毫秒，见 pitfalls P-003。
 */
public final class Times {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private Times() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MILLIS);
    }

    public static long toMillis(LocalDateTime time) {
        return time.atZone(ZONE).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
    }
}
