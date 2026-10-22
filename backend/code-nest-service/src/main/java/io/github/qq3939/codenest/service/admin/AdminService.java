package io.github.qq3939.codenest.service.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.common.PageResult;
import io.github.qq3939.codenest.api.enums.UserRole;
import io.github.qq3939.codenest.api.enums.UserStatus;
import io.github.qq3939.codenest.api.vo.AdminUserVO;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.service.article.ArticleService;
import io.github.qq3939.codenest.service.user.AuthService;
import io.github.qq3939.codenest.service.user.UserConverter;
import io.github.qq3939.codenest.service.user.UserStatusService;
import io.github.qq3939.codenest.service.user.entity.UserDO;
import io.github.qq3939.codenest.service.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端的写操作：每个操作都同时写操作日志。 */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final ArticleService articleService;
    private final AdminAuditService auditService;
    private final UserMapper userMapper;
    private final UserStatusService userStatusService;
    private final AuthService authService;

    @Transactional(rollbackFor = Exception.class)
    public void approveArticle(Long adminId, Long articleId, String reason) {
        articleService.approve(articleId);
        auditService.log(adminId, "ARTICLE_APPROVE", "ARTICLE", articleId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rejectArticle(Long adminId, Long articleId, String reason) {
        articleService.reject(articleId, reason);
        auditService.log(adminId, "ARTICLE_REJECT", "ARTICLE", articleId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void offlineArticle(Long adminId, Long articleId, String reason) {
        articleService.offline(articleId, reason);
        auditService.log(adminId, "ARTICLE_OFFLINE", "ARTICLE", articleId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreArticle(Long adminId, Long articleId, String reason) {
        articleService.restore(articleId);
        auditService.log(adminId, "ARTICLE_RESTORE", "ARTICLE", articleId, reason);
    }

    public PageResult<AdminUserVO> users(String keyword, long page, long size) {
        Page<UserDO> result = userMapper.selectPage(Page.of(page, size), Wrappers.<UserDO>lambdaQuery()
                .and(keyword != null && !keyword.isBlank(), w -> w.like(UserDO::getUsername, keyword)
                        .or().like(UserDO::getNickname, keyword))
                .orderByDesc(UserDO::getId));
        return new PageResult<>(result.getRecords().stream().map(UserConverter::toAdminVO).toList(),
                result.getTotal(), page, size);
    }

    /**
     * 封禁立即生效靠两步：改写 user:status 缓存（拦截器下一个请求就拦住访问令牌），
     * 并作废全部刷新令牌（无法再换新的访问令牌）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void ban(Long adminId, Long userId, String reason) {
        UserDO user = requireUser(userId);
        if (user.getRole() == UserRole.ADMIN) {
            throw new BizException(ErrorCode.CANNOT_BAN_ADMIN);
        }
        user.setStatus(UserStatus.BANNED);
        userMapper.updateById(user);
        auditService.log(adminId, "USER_BAN", "USER", userId, reason);
        userStatusService.put(userId, UserStatus.BANNED);
        authService.revokeAll(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unban(Long adminId, Long userId, String reason) {
        UserDO user = requireUser(userId);
        user.setStatus(UserStatus.NORMAL);
        userMapper.updateById(user);
        auditService.log(adminId, "USER_UNBAN", "USER", userId, reason);
        userStatusService.put(userId, UserStatus.NORMAL);
    }

    public void log(Long adminId, String action, String targetType, Long targetId, String reason) {
        auditService.log(adminId, action, targetType, targetId, reason);
    }

    private UserDO requireUser(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
