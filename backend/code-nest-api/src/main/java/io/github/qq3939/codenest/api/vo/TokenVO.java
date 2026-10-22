package io.github.qq3939.codenest.api.vo;

/** expiresIn 是访问令牌的剩余秒数，前端据此决定何时提前刷新。 */
public record TokenVO(String accessToken, String refreshToken, long expiresIn, UserVO user) {
}
