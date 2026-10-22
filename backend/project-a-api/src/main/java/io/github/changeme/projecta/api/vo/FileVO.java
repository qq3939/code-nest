package io.github.changeme.projecta.api.vo;

/** url 是经 Nginx /files/ 访问的相对路径，前端直接拿来用。 */
public record FileVO(String url, String objectName) {
}
