-- 初始分类和少量敏感词示例。ID 手工指定为小整数，避免和雪花 ID 冲突。
INSERT INTO category (id, name, sort) VALUES
    (1, '后端', 10),
    (2, '前端', 20),
    (3, '人工智能', 30),
    (4, '数据库', 40),
    (5, '运维', 50),
    (6, '算法', 60),
    (7, '面试', 70),
    (8, '其他', 99);

-- 三个级别各给一个占位词，方便直接测试审核流程；真实词库在管理端维护
INSERT INTO sensitive_word (id, word, level, enabled) VALUES
    (1, '违禁示例词', 'BLOCK', 1),
    (2, '待审示例词', 'REVIEW', 1),
    (3, '替换示例词', 'REPLACE', 1);
