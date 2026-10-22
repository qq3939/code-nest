package io.github.changeme.projecta.service.interaction;

import io.github.changeme.projecta.api.common.ErrorCode;
import io.github.changeme.projecta.core.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 评论、通知用的"时间 + ID"游标，编码成不透明字符串交给前端：前端只管原样传回，不依赖其内部格式。
 * 用 (time, id) 而不只是 time：同一毫秒可能有多条记录，加上 id 才能唯一定位"上一页最后一条"。
 */
public final class CursorCodec {

    public record Position(long time, long id) {
    }

    private CursorCodec() {
    }

    public static String encode(long time, long id) {
        String raw = time + "_" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("_");
            return new Position(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (Exception e) {
            throw new BizException(ErrorCode.CURSOR_INVALID);
        }
    }
}
