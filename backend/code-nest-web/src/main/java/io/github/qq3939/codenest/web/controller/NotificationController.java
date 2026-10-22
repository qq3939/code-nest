package io.github.qq3939.codenest.web.controller;

import io.github.qq3939.codenest.api.common.CursorPage;
import io.github.qq3939.codenest.api.dto.MarkReadReq;
import io.github.qq3939.codenest.api.enums.NotifyType;
import io.github.qq3939.codenest.api.vo.NotifyVO;
import io.github.qq3939.codenest.api.vo.UnreadCountVO;
import io.github.qq3939.codenest.core.result.Result;
import io.github.qq3939.codenest.core.security.UserContext;
import io.github.qq3939.codenest.service.notify.NotifyService;
import io.github.qq3939.codenest.web.sse.SseHub;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Locale;

@Tag(name = "通知")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotifyService notifyService;
    private final SseHub sseHub;

    @Operation(summary = "通知列表：type=COMMENT|REPLY|LIKE|COLLECT|FOLLOW|SYSTEM，不传则全部")
    @GetMapping
    public Result<CursorPage<NotifyVO>> list(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false) Integer size) {
        NotifyType t = type == null || type.isBlank() ? null : NotifyType.valueOf(type.toUpperCase(Locale.ROOT));
        return Result.ok(notifyService.list(UserContext.requireUserId(), t, cursor, Pages.cursorSize(size)));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unread() {
        return Result.ok(new UnreadCountVO(notifyService.unreadCount(UserContext.requireUserId())));
    }

    @Operation(summary = "标记已读：传 ids 标记指定通知，all=true 全部标记")
    @PostMapping("/read")
    public Result<Void> read(@RequestBody MarkReadReq req) {
        notifyService.markRead(UserContext.requireUserId(), req);
        return Result.ok();
    }

    /** 前端用 fetch-event-source 带上 Authorization 头连接；连上后先推一次当前未读数。 */
    @Operation(summary = "SSE：实时推送未读数（事件名 unread）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = UserContext.requireUserId();
        return sseHub.connect(userId, notifyService.unreadCount(userId));
    }
}
