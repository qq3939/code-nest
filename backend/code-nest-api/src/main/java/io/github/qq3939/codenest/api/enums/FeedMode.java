package io.github.qq3939.codenest.api.enums;

import java.util.Arrays;

/** Feed 模式，见 SPEC 9.3。pull 只作对照组，禁止设为默认。 */
public enum FeedMode {
    PUSH("push"),
    PULL("pull"),
    HYBRID("hybrid");

    private final String value;

    FeedMode(String value) {
        this.value = value;
    }

    /** 配置文件和接口里使用的取值，例如 redis-batch。 */
    public String value() {
        return value;
    }

    public static FeedMode fromValue(String value) {
        return Arrays.stream(values())
                .filter(v -> v.value.equalsIgnoreCase(value) || v.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown FeedMode: " + value));
    }
}
