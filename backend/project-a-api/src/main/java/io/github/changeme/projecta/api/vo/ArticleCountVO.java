package io.github.changeme.projecta.api.vo;

public record ArticleCountVO(long read, long like, long collect, long comment) {

    public static ArticleCountVO zero() {
        return new ArticleCountVO(0, 0, 0, 0);
    }
}
