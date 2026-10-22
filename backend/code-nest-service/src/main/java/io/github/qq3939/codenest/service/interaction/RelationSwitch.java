package io.github.qq3939.codenest.service.interaction;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.qq3939.codenest.service.common.BaseDO;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.Supplier;

/**
 * 点赞、收藏、关注三类关系表共用的"开/关"逻辑，返回值表示状态是否真的变了——
 * 只有真的变了才去加减计数、发事件，重复点击是幂等的。
 */
public final class RelationSwitch {

    @FunctionalInterface
    public interface StatusUpdater {
        int switchStatus(Long id, int from, int to);
    }

    private RelationSwitch() {
    }

    public static <T extends BaseDO> boolean activate(BaseMapper<T> mapper, T existing, Supplier<T> creator,
                                                      StatusUpdater updater) {
        if (existing == null) {
            try {
                mapper.insert(creator.get());
                return true;
            } catch (DuplicateKeyException e) {
                // 并发的另一个请求刚插入了同一条关系，视为"已经是开启状态"
                return false;
            }
        }
        return updater.switchStatus(existing.getId(), 0, 1) == 1;
    }

    public static <T extends BaseDO> boolean deactivate(T existing, StatusUpdater updater) {
        if (existing == null) {
            return false;
        }
        return updater.switchStatus(existing.getId(), 1, 0) == 1;
    }
}
