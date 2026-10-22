package io.github.qq3939.codenest.service.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.qq3939.codenest.api.common.PageResult;
import io.github.qq3939.codenest.api.vo.AuditLogVO;
import io.github.qq3939.codenest.api.vo.UserBriefVO;
import io.github.qq3939.codenest.service.admin.entity.AdminAuditLogDO;
import io.github.qq3939.codenest.service.admin.mapper.AdminAuditLogMapper;
import io.github.qq3939.codenest.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 管理员操作日志：审核、封禁、下架等"会影响别人"的操作都要留痕，出了争议能追溯是谁、何时、为什么。 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditLogMapper mapper;
    private final UserService userService;

    public void log(Long adminId, String action, String targetType, Long targetId, String reason) {
        AdminAuditLogDO row = new AdminAuditLogDO();
        row.setAdminId(adminId);
        row.setAction(action);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setReason(reason == null ? "" : reason);
        mapper.insert(row);
    }

    public PageResult<AuditLogVO> page(String action, long page, long size) {
        Page<AdminAuditLogDO> result = mapper.selectPage(Page.of(page, size), Wrappers.<AdminAuditLogDO>lambdaQuery()
                .eq(action != null && !action.isBlank(), AdminAuditLogDO::getAction, action)
                .orderByDesc(AdminAuditLogDO::getId));
        Map<Long, UserBriefVO> admins = userService.briefs(result.getRecords().stream().map(AdminAuditLogDO::getAdminId).toList());
        return new PageResult<>(result.getRecords().stream().map(r -> new AuditLogVO(r.getId(), admins.get(r.getAdminId()),
                r.getAction(), r.getTargetType(), r.getTargetId(), r.getReason(), r.getCreateTime())).toList(),
                result.getTotal(), page, size);
    }
}
