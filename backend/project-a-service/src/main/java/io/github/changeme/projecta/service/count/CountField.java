package io.github.changeme.projecta.service.count;

/** article_stat 的四个计数字段。hashField 是 Redis Hash 里的字段名，key 是 XML 里 choose 判断用的取值。 */
public enum CountField {
    READ("read"),
    LIKE("like"),
    COLLECT("collect"),
    COMMENT("comment");

    private final String key;

    CountField(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
