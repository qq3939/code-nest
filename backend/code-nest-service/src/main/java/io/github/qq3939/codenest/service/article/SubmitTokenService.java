package io.github.qq3939.codenest.service.article;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.vo.SubmitTokenVO;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 防重复提交（SPEC 9.11）：打开编辑器时领一个令牌，发布时带上；服务端"检查并删除"成功才执行发布。
 * DEL 本身是原子的，返回 1 说明这次请求删掉了令牌——连点两次发布，只有一次能删成功。
 */
@Service
@RequiredArgsConstructor
public class SubmitTokenService {
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final RedisKeys keys;

    public SubmitTokenVO issue(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(keys.submitToken(token), String.valueOf(userId), TTL);
        return new SubmitTokenVO(token, TTL.toSeconds());
    }

    public void consume(String token) {
        if (token == null || token.isBlank() || !Boolean.TRUE.equals(redis.delete(keys.submitToken(token)))) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
    }
}
