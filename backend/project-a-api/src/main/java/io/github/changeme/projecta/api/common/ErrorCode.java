package io.github.changeme.projecta.api.common;

/**
 * 错误码按模块分段：1xxxx 通用，2xxxx 用户，3xxxx 文章，4xxxx 互动，5xxxx 关注和 Feed，6xxxx 通知，9xxxx 管理。
 * 同时带上 HTTP 状态码，让网关和前端不解析 body 也能区分大类（如 401 触发刷新令牌、429 提示限流）。
 */
public enum ErrorCode {
    SUCCESS(0, 200, "成功"),

    PARAM_INVALID(10001, 400, "参数错误"),
    UNAUTHORIZED(10002, 401, "请先登录"),
    FORBIDDEN(10003, 403, "没有权限"),
    NOT_FOUND(10004, 404, "资源不存在"),
    TOO_MANY_REQUESTS(10005, 429, "操作太频繁，请稍后再试"),
    DUPLICATE_SUBMIT(10006, 409, "请勿重复提交"),
    FILE_INVALID(10007, 400, "文件类型或大小不符合要求"),
    FEATURE_DISABLED(10008, 404, "该功能未开启"),
    SYSTEM_ERROR(10099, 500, "系统繁忙，请稍后再试"),

    USERNAME_EXISTS(20001, 409, "用户名已被注册"),
    BAD_CREDENTIALS(20002, 401, "用户名或密码错误"),
    ACCOUNT_LOCKED(20003, 423, "登录失败次数过多，请 15 分钟后再试"),
    USER_BANNED(20004, 403, "账号已被封禁"),
    TOKEN_EXPIRED(20005, 401, "登录已过期"),
    REFRESH_TOKEN_INVALID(20006, 401, "刷新令牌无效，请重新登录"),
    OLD_PASSWORD_WRONG(20007, 400, "原密码错误"),
    USER_NOT_FOUND(20008, 404, "用户不存在"),

    ARTICLE_NOT_FOUND(30001, 404, "文章不存在"),
    ARTICLE_NO_PERMISSION(30002, 403, "只能操作自己的文章"),
    ARTICLE_BLOCKED_WORD(30003, 400, "内容包含违禁词"),
    ARTICLE_STATUS_INVALID(30004, 409, "文章当前状态不允许该操作"),
    CATEGORY_NOT_FOUND(30005, 400, "分类不存在"),
    CATEGORY_EXISTS(30006, 409, "分类已存在"),
    TAG_NOT_FOUND(30007, 404, "标签不存在"),

    COMMENT_NOT_FOUND(40001, 404, "评论不存在"),
    COMMENT_NO_PERMISSION(40002, 403, "只能删除自己的评论"),

    CANNOT_FOLLOW_SELF(50001, 400, "不能关注自己"),
    CURSOR_INVALID(50002, 400, "游标格式错误"),

    NOTIFY_NOT_FOUND(60001, 404, "通知不存在"),

    DEAD_LETTER_NOT_FOUND(90001, 404, "死信记录不存在"),
    STRATEGY_INVALID(90002, 400, "未知的策略取值"),
    SENSITIVE_WORD_EXISTS(90003, 409, "敏感词已存在"),
    CANNOT_BAN_ADMIN(90004, 400, "不能封禁管理员");

    private final int code;
    private final int httpStatus;
    private final String message;

    ErrorCode(int code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
