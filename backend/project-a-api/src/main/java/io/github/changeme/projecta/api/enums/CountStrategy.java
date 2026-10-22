package io.github.changeme.projecta.api.enums;

import java.util.Arrays;

/** 计数策略，见 SPEC 9.2。naive 只作对照组，禁止设为默认。 */
public enum CountStrategy {
    NAIVE("naive"),
    ATOMIC_SQL("atomic-sql"),
    REDIS_BATCH("redis-batch");

    private final String value;

    CountStrategy(String value) {
        this.value = value;
    }

    /** 配置文件和接口里使用的取值，例如 redis-batch。 */
    public String value() {
        return value;
    }

    public static CountStrategy fromValue(String value) {
        return Arrays.stream(values())
                .filter(v -> v.value.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown CountStrategy: " + value));
    }
}
