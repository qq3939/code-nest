-- V1：一次建出 SPEC 第 6 节的全部表。
-- 约定：主键是雪花 ID（应用侧生成，所以不用 AUTO_INCREMENT）；时间用 DATETIME(3) 保留毫秒，便于 Feed 和游标排序。
-- article_tag、article_stat、user_stat 三张表按 SPEC 用业务键做主键，不另设 id 列（见 D-003）。

CREATE TABLE user (
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(32)  NOT NULL,
    avatar_url    VARCHAR(255) NOT NULL DEFAULT '',
    bio           VARCHAR(255) NOT NULL DEFAULT '',
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户';

CREATE TABLE article (
    id           BIGINT       NOT NULL PRIMARY KEY,
    author_id    BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    summary      VARCHAR(512) NOT NULL DEFAULT '',
    cover_url    VARCHAR(255) NOT NULL DEFAULT '',
    category_id  BIGINT       NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    publish_time DATETIME(3)  NULL,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_publish (status, publish_time),
    KEY idx_author_status_publish (author_id, status, publish_time),
    KEY idx_category_status_publish (category_id, status, publish_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章（不含正文）';

-- 正文是大字段，单独一张表，列表查询不会把它读进 Buffer Pool
CREATE TABLE article_content (
    article_id  BIGINT      NOT NULL PRIMARY KEY,
    content     MEDIUMTEXT  NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章正文';

CREATE TABLE category (
    id          BIGINT      NOT NULL PRIMARY KEY,
    name        VARCHAR(32) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '分类';

CREATE TABLE tag (
    id          BIGINT      NOT NULL PRIMARY KEY,
    name        VARCHAR(32) NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '标签';

CREATE TABLE article_tag (
    article_id  BIGINT      NOT NULL,
    tag_id      BIGINT      NOT NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (article_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章-标签';

CREATE TABLE comment (
    id               BIGINT        NOT NULL PRIMARY KEY,
    article_id       BIGINT        NOT NULL,
    user_id          BIGINT        NOT NULL,
    parent_id        BIGINT        NOT NULL DEFAULT 0 COMMENT '0 表示一级评论',
    reply_to_user_id BIGINT        NOT NULL DEFAULT 0,
    content          VARCHAR(1000) NOT NULL,
    like_count       INT           NOT NULL DEFAULT 0,
    deleted          TINYINT       NOT NULL DEFAULT 0,
    create_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_article_parent_time (article_id, parent_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '评论';

-- 关系表用 status 表示有效/取消，而不是逻辑删除：逻辑删除后再点赞会撞唯一索引
CREATE TABLE like_record (
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id   BIGINT      NOT NULL,
    status      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_target (user_id, target_type, target_id),
    KEY idx_target_status (target_type, target_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '点赞';

CREATE TABLE collect_record (
    id          BIGINT      NOT NULL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    article_id  BIGINT      NOT NULL,
    status      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_article (user_id, article_id),
    KEY idx_user_status_time (user_id, status, update_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '收藏';

CREATE TABLE follow (
    id          BIGINT      NOT NULL PRIMARY KEY,
    follower_id BIGINT      NOT NULL,
    followee_id BIGINT      NOT NULL,
    status      TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_follower_followee (follower_id, followee_id),
    KEY idx_followee_status (followee_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '关注';

CREATE TABLE article_stat (
    article_id    BIGINT      NOT NULL PRIMARY KEY,
    read_count    BIGINT      NOT NULL DEFAULT 0,
    like_count    BIGINT      NOT NULL DEFAULT 0,
    collect_count BIGINT      NOT NULL DEFAULT 0,
    comment_count BIGINT      NOT NULL DEFAULT 0,
    create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '文章计数';

CREATE TABLE user_stat (
    user_id         BIGINT      NOT NULL PRIMARY KEY,
    article_count   BIGINT      NOT NULL DEFAULT 0,
    follower_count  BIGINT      NOT NULL DEFAULT 0,
    following_count BIGINT      NOT NULL DEFAULT 0,
    like_received   BIGINT      NOT NULL DEFAULT 0,
    read_received   BIGINT      NOT NULL DEFAULT 0,
    create_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户计数';

CREATE TABLE notify_message (
    id          BIGINT       NOT NULL PRIMARY KEY,
    receiver_id BIGINT       NOT NULL,
    sender_id   BIGINT       NOT NULL DEFAULT 0,
    type        VARCHAR(16)  NOT NULL,
    target_type VARCHAR(16)  NOT NULL DEFAULT '',
    target_id   BIGINT       NOT NULL DEFAULT 0,
    content     VARCHAR(512) NOT NULL DEFAULT '',
    is_read     TINYINT      NOT NULL DEFAULT 0,
    msg_id      VARCHAR(64)  NOT NULL COMMENT '消费幂等：同一条 MQ 消息只能落一条通知',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_msg_id (msg_id),
    KEY idx_receiver_read_time (receiver_id, is_read, create_time),
    KEY idx_receiver_type_id (receiver_id, type, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '通知';

CREATE TABLE outbox_message (
    id              BIGINT       NOT NULL PRIMARY KEY,
    msg_id          VARCHAR(64)  NOT NULL,
    exchange        VARCHAR(64)  NOT NULL,
    routing_key     VARCHAR(64)  NOT NULL,
    payload         JSON         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    create_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_msg_id (msg_id),
    KEY idx_status_next (status, next_retry_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '本地消息表';

CREATE TABLE dead_letter_message (
    id          BIGINT        NOT NULL PRIMARY KEY,
    msg_id      VARCHAR(64)   NOT NULL,
    queue       VARCHAR(64)   NOT NULL,
    payload     JSON          NOT NULL,
    error       VARCHAR(1000) NOT NULL DEFAULT '',
    failed_time DATETIME(3)   NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'NEW',
    create_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_status_failed (status, failed_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '死信记录';

CREATE TABLE sensitive_word (
    id          BIGINT      NOT NULL PRIMARY KEY,
    word        VARCHAR(64) NOT NULL,
    level       VARCHAR(16) NOT NULL,
    enabled     TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_word (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '敏感词';

CREATE TABLE rank_snapshot (
    id          BIGINT         NOT NULL PRIMARY KEY,
    rank_type   VARCHAR(32)    NOT NULL,
    period      VARCHAR(16)    NOT NULL COMMENT '如 20261021',
    rank_no     INT            NOT NULL,
    subject_id  BIGINT         NOT NULL,
    score       DECIMAL(16, 4) NOT NULL,
    create_time DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_type_period_no (rank_type, period, rank_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '榜单归档';

CREATE TABLE daily_stat (
    id           BIGINT      NOT NULL PRIMARY KEY,
    stat_date    DATE        NOT NULL,
    dau          BIGINT      NOT NULL DEFAULT 0,
    new_users    BIGINT      NOT NULL DEFAULT 0,
    new_articles BIGINT      NOT NULL DEFAULT 0,
    `reads`      BIGINT      NOT NULL DEFAULT 0,
    likes        BIGINT      NOT NULL DEFAULT 0,
    create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '每日统计';

CREATE TABLE admin_audit_log (
    id          BIGINT       NOT NULL PRIMARY KEY,
    admin_id    BIGINT       NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target_type VARCHAR(16)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(255) NOT NULL DEFAULT '',
    create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_admin_time (admin_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '管理员操作日志';
