package io.github.qq3939.codenest.service.interaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.qq3939.codenest.api.event.EventTypes;
import io.github.qq3939.codenest.api.event.Payloads;
import io.github.qq3939.codenest.service.article.ArticleService;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.common.AfterCommit;
import io.github.qq3939.codenest.service.count.CountField;
import io.github.qq3939.codenest.service.count.CountService;
import io.github.qq3939.codenest.service.interaction.entity.CollectRecordDO;
import io.github.qq3939.codenest.service.interaction.mapper.CollectRecordMapper;
import io.github.qq3939.codenest.service.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CollectService {
    private final CollectRecordMapper collectMapper;
    private final ArticleService articleService;
    private final CountService countService;
    private final OutboxService outboxService;

    @Transactional(rollbackFor = Exception.class)
    public void collect(Long userId, Long articleId) {
        ArticleDO article = articleService.requirePublished(articleId);
        if (!RelationSwitch.activate(collectMapper, find(userId, articleId), () -> newRecord(userId, articleId),
                collectMapper::switchStatus)) {
            return;
        }
        outboxService.save(EventTypes.INTERACTION_COLLECTED,
                new Payloads.CollectEvent(userId, articleId, article.getAuthorId()));
        AfterCommit.run(() -> countService.increase(articleId, CountField.COLLECT, 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void uncollect(Long userId, Long articleId) {
        if (RelationSwitch.deactivate(find(userId, articleId), collectMapper::switchStatus)) {
            AfterCommit.run(() -> countService.increase(articleId, CountField.COLLECT, -1));
        }
    }

    public boolean isCollected(Long userId, Long articleId) {
        if (userId == null) {
            return false;
        }
        CollectRecordDO row = find(userId, articleId);
        return row != null && row.getStatus() == 1;
    }

    private CollectRecordDO find(Long userId, Long articleId) {
        return collectMapper.selectOne(Wrappers.<CollectRecordDO>lambdaQuery()
                .eq(CollectRecordDO::getUserId, userId)
                .eq(CollectRecordDO::getArticleId, articleId));
    }

    private static CollectRecordDO newRecord(Long userId, Long articleId) {
        CollectRecordDO row = new CollectRecordDO();
        row.setUserId(userId);
        row.setArticleId(articleId);
        row.setStatus(1);
        return row;
    }
}
