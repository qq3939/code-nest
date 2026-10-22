package io.github.qq3939.codenest.service.sensitive;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.common.PageResult;
import io.github.qq3939.codenest.api.dto.SensitiveWordSaveReq;
import io.github.qq3939.codenest.api.enums.SensitiveLevel;
import io.github.qq3939.codenest.api.vo.SensitiveWordVO;
import io.github.qq3939.codenest.core.exception.BizException;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.core.trie.SensitiveTrie;
import io.github.qq3939.codenest.service.sensitive.entity.SensitiveWordDO;
import io.github.qq3939.codenest.service.sensitive.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词：启动时从库里加载、构建前缀树；管理端修改后通过 Redis Pub/Sub 广播 sensitive:refresh，
 * 每个实例收到后各自重建——多实例部署时，只改本机内存的话其他实例感知不到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {
    private final SensitiveWordMapper mapper;
    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final RedisMessageListenerContainer listenerContainer;

    /** volatile 保证重建后其他线程立即看到新树；树本身构建后不可变，所以读的时候不用加锁。 */
    private volatile SensitiveTrie trie = SensitiveTrie.build(Map.of());

    @PostConstruct
    void init() {
        reload();
        listenerContainer.addMessageListener((message, pattern) -> reload(),
                new ChannelTopic(keys.channelSensitiveRefresh()));
    }

    public void reload() {
        Map<String, SensitiveLevel> words = new LinkedHashMap<>();
        mapper.selectList(Wrappers.<SensitiveWordDO>lambdaQuery().eq(SensitiveWordDO::getEnabled, 1))
                .forEach(w -> words.put(w.getWord(), w.getLevel()));
        trie = SensitiveTrie.build(words);
        log.info("sensitive words reloaded, size={}", words.size());
    }

    public List<SensitiveTrie.Hit> check(String text) {
        return trie.findAll(text);
    }

    /** 评论只做替换：所有级别都替换成 *，不拦截也不送审，避免影响讨论体验。 */
    public String replaceAll(String text) {
        return trie.replace(text, SensitiveLevel.values());
    }

    public String replace(String text, SensitiveLevel... levels) {
        return trie.replace(text, levels);
    }

    public PageResult<SensitiveWordVO> page(String keyword, long page, long size) {
        Page<SensitiveWordDO> result = mapper.selectPage(Page.of(page, size), Wrappers.<SensitiveWordDO>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), SensitiveWordDO::getWord, keyword)
                .orderByDesc(SensitiveWordDO::getId));
        return new PageResult<>(result.getRecords().stream().map(SensitiveWordService::toVO).toList(),
                result.getTotal(), page, size);
    }

    public SensitiveWordVO create(SensitiveWordSaveReq req) {
        SensitiveWordDO row = new SensitiveWordDO();
        row.setWord(req.word().trim());
        row.setLevel(req.level());
        row.setEnabled(req.enabled() ? 1 : 0);
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.SENSITIVE_WORD_EXISTS);
        }
        broadcast();
        return toVO(row);
    }

    public SensitiveWordVO update(Long id, SensitiveWordSaveReq req) {
        SensitiveWordDO row = mapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        row.setWord(req.word().trim());
        row.setLevel(req.level());
        row.setEnabled(req.enabled() ? 1 : 0);
        try {
            mapper.updateById(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.SENSITIVE_WORD_EXISTS);
        }
        broadcast();
        return toVO(row);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
        broadcast();
    }

    private void broadcast() {
        redis.convertAndSend(keys.channelSensitiveRefresh(), "1");
    }

    private static SensitiveWordVO toVO(SensitiveWordDO w) {
        return new SensitiveWordVO(w.getId(), w.getWord(), w.getLevel().name(), w.getEnabled() == 1);
    }
}
