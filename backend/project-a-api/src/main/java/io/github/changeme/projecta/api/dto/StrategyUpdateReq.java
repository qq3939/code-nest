package io.github.changeme.projecta.api.dto;

/** 字段为空表示不修改。取值见 CountStrategy、FeedMode、CacheMode 的 value()。 */
public record StrategyUpdateReq(String countStrategy, String feedMode, String cacheMode) {
}
