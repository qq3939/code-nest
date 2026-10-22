package io.github.changeme.projecta.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.dto.LoginReq;
import io.github.changeme.projecta.api.dto.RegisterReq;
import io.github.changeme.projecta.api.enums.UserRole;
import io.github.changeme.projecta.api.enums.UserStatus;
import io.github.changeme.projecta.api.vo.TokenVO;
import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.core.redis.RedisKeys;
import io.github.changeme.projecta.core.security.JwtUtil;
import io.github.changeme.projecta.service.user.entity.UserDO;
import io.github.changeme.projecta.service.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 认证：注册、登录、刷新、退出、失败锁定。
 * 双令牌设计：访问令牌（JWT，30 分钟）无状态、校验快；刷新令牌（随机串，存 Redis，7 天）可撤销。
 * 刷新令牌格式为 "userId.tokenId"，带上 userId 是为了能按用户批量作废（封禁、改密码）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final AppProperties props;
    private final UserStatusService userStatusService;

    public TokenVO register(RegisterReq req) {
        UserDO user = new UserDO();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname() == null || req.nickname().isBlank() ? req.username() : req.nickname());
        user.setAvatarUrl("");
        user.setBio("");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.NORMAL);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 先查再插有并发窗口，直接依赖唯一索引兜底更可靠
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        return issueTokens(user);
    }

    public TokenVO login(LoginReq req) {
        String failKey = keys.loginFail(req.username());
        String failCount = redis.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= props.getAuth().getMaxLoginFail()) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED);
        }
        UserDO user = userMapper.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            recordFailure(failKey);
            // 用户不存在和密码错误返回同一个提示，防止被用来探测哪些用户名已注册
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BizException(ErrorCode.USER_BANNED);
        }
        redis.delete(failKey);
        return issueTokens(user);
    }

    /** 每次刷新都轮换：旧令牌立即作废，被盗的刷新令牌最多只能用一次。 */
    public TokenVO refresh(String refreshToken) {
        ParsedRefresh parsed = parse(refreshToken);
        // DEL 的返回值就是"检查并删除"的原子结果：并发的两次刷新只有一次能成功
        Boolean deleted = redis.delete(keys.refreshToken(parsed.userId(), parsed.tokenId()));
        if (!Boolean.TRUE.equals(deleted)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        UserDO user = userMapper.selectById(parsed.userId());
        if (user == null) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BizException(ErrorCode.USER_BANNED);
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        try {
            ParsedRefresh parsed = parse(refreshToken);
            redis.delete(keys.refreshToken(parsed.userId(), parsed.tokenId()));
        } catch (BizException ignored) {
            // 退出是幂等操作，令牌本来就无效时也视为成功
        }
    }

    /** 作废某个用户的全部刷新令牌，用于封禁和修改密码。用 SCAN 而不是 KEYS，避免阻塞 Redis。 */
    public void revokeAll(Long userId) {
        List<String> found = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(keys.refreshTokenPattern(userId)).count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(found::add);
        }
        if (!found.isEmpty()) {
            redis.delete(found);
        }
        log.info("revoked {} refresh tokens of user {}", found.size(), userId);
    }

    private TokenVO issueTokens(UserDO user) {
        userStatusService.put(user.getId(), user.getStatus());
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(keys.refreshToken(user.getId(), tokenId), "1", props.getJwt().getRefreshTtl());
        String access = jwtUtil.createAccessToken(user.getId(), user.getRole());
        return new TokenVO(access, user.getId() + "." + tokenId, jwtUtil.ttlSeconds(), UserConverter.toVO(user));
    }

    private void recordFailure(String failKey) {
        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            // 第一次失败时设置过期：锁定窗口从第一次失败开始算
            redis.expire(failKey, props.getAuth().getLockDuration());
        }
    }

    private static ParsedRefresh parse(String refreshToken) {
        int dot = refreshToken == null ? -1 : refreshToken.indexOf('.');
        if (dot <= 0) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        try {
            return new ParsedRefresh(Long.parseLong(refreshToken.substring(0, dot)), refreshToken.substring(dot + 1));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private record ParsedRefresh(Long userId, String tokenId) {
    }
}
