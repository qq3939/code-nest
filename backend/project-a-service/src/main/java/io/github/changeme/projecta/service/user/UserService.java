package io.github.changeme.projecta.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.api.dto.ChangePasswordReq;
import io.github.changeme.projecta.api.dto.UpdateProfileReq;
import io.github.changeme.projecta.api.enums.UserRole;
import io.github.changeme.projecta.api.enums.UserStatus;
import io.github.changeme.projecta.api.vo.UserBriefVO;
import io.github.changeme.projecta.api.vo.UserProfileVO;
import io.github.changeme.projecta.api.vo.UserVO;
import io.github.changeme.projecta.core.exception.BizException;
import io.github.changeme.projecta.service.user.entity.UserDO;
import io.github.changeme.projecta.service.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final UserStatService userStatService;

    public UserDO require(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public UserVO me(Long userId) {
        return UserConverter.toVO(require(userId));
    }

    public UserVO updateProfile(Long userId, UpdateProfileReq req) {
        UserDO user = require(userId);
        user.setNickname(req.nickname());
        user.setBio(req.bio() == null ? "" : req.bio());
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userMapper.updateById(user);
        return UserConverter.toVO(user);
    }

    /** 改密码后作废所有刷新令牌：其他设备上的登录随之失效（访问令牌最多再活 30 分钟）。 */
    public void changePassword(Long userId, ChangePasswordReq req) {
        UserDO user = require(userId);
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(user);
        authService.revokeAll(userId);
    }

    public UserProfileVO profile(Long userId, boolean followed) {
        UserDO user = require(userId);
        return new UserProfileVO(UserConverter.toBrief(user), userStatService.refreshAndGet(userId), followed,
                user.getCreateTime());
    }

    public UserBriefVO brief(Long userId) {
        return briefs(java.util.List.of(userId)).get(userId);
    }

    /** 批量查用户简要信息，列表页用它避免 N+1 查询；查不到的 ID 返回占位用户，前端不用判空。 */
    public Map<Long, UserBriefVO> briefs(Collection<Long> userIds) {
        var ids = new HashSet<>(userIds);
        ids.removeIf(Objects::isNull);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserBriefVO> result = new HashMap<>(userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(UserDO::getId, UserConverter::toBrief, (a, b) -> a)));
        ids.forEach(id -> result.computeIfAbsent(id, UserBriefVO::unknown));
        return result;
    }

    public Map<Long, UserDO> byIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(UserDO::getId, Function.identity()));
    }

    /** 启动时根据 ADMIN_USERNAME / ADMIN_PASSWORD 创建第一个管理员，已存在则跳过。 */
    public void ensureAdmin(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("ADMIN_USERNAME / ADMIN_PASSWORD not set, skip admin init");
            return;
        }
        boolean exists = userMapper.exists(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, username));
        if (exists) {
            return;
        }
        UserDO admin = new UserDO();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setNickname("管理员");
        admin.setAvatarUrl("");
        admin.setBio("");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.NORMAL);
        userMapper.insert(admin);
        log.info("admin account {} created", username);
    }
}
