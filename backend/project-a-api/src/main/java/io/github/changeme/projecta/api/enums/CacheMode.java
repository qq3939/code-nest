package io.github.changeme.projecta.api.enums;

import java.util.Arrays;

/** 缓存模式，见 SPEC 9.4。none 只作对照组，禁止设为默认。 */
public enum CacheMode {
    NONE("none"),
    REDIS("redis"),
    TWO_LEVEL("two-level");

    private final String value;

    CacheMode(String value) {
        this.value = value;
    }

    /** 配置文件和接口里使用的取值，例如 redis-batch。 */
    public String value() {
        return value;
    }

    public static CacheMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(v -> v.value.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown CacheMode: " + value));
    }
}
