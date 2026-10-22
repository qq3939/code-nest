package io.github.qq3939.codenest.service.stat;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.qq3939.codenest.api.enums.ArticleStatus;
import io.github.qq3939.codenest.api.vo.DailyStatVO;
import io.github.qq3939.codenest.api.vo.DashboardVO;
import io.github.qq3939.codenest.core.redis.RedisKeys;
import io.github.qq3939.codenest.service.article.entity.ArticleDO;
import io.github.qq3939.codenest.service.article.mapper.ArticleMapper;
import io.github.qq3939.codenest.service.stat.entity.DailyStatDO;
import io.github.qq3939.codenest.service.stat.mapper.DailyStatMapper;
import io.github.qq3939.codenest.service.user.entity.UserDO;
import io.github.qq3939.codenest.service.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 看板数据。日活用 HyperLogLog：每天一个 key，固定约 12 KB，统计误差约 0.81%，
 * 相比 Set 存全部用户 ID，内存不随用户数增长——看板只需要趋势，不需要精确值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatService {
    public static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Duration KEEP = Duration.ofDays(35);

    private final StringRedisTemplate redis;
    private final RedisKeys keys;
    private final DailyStatMapper dailyStatMapper;
    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;

    /** 登录用户按 ID 去重，匿名访客按 IP 去重。 */
    public void recordVisit(Long userId, String ip) {
        try {
            String key = keys.uvDay(LocalDate.now().format(DAY));
            Long added = redis.opsForHyperLogLog().add(key, userId != null ? "u:" + userId : "ip:" + ip);
            if (added != null && added == 1) {
                redis.expire(key, KEEP);
            }
        } catch (Exception e) {
            // 统计失败不能影响正常请求
            log.debug("record visit failed", e);
        }
    }

    public void incrDaily(String field, long delta) {
        try {
            String key = keys.statDaily(LocalDate.now().format(DAY));
            redis.opsForHash().increment(key, field, delta);
            redis.expire(key, KEEP);
        } catch (Exception e) {
            log.debug("incr daily stat failed", e);
        }
    }

    /** 每天 00:10 把昨天的数据落库，供看板长期展示。 */
    public void archiveDay(LocalDate date) {
        DailyStatVO vo = compute(date);
        DailyStatDO row = dailyStatMapper.selectOne(Wrappers.<DailyStatDO>lambdaQuery().eq(DailyStatDO::getStatDate, date));
        boolean exists = row != null;
        if (!exists) {
            row = new DailyStatDO();
            row.setStatDate(date);
        }
        row.setDau(vo.dau());
        row.setNewUsers(vo.newUsers());
        row.setNewArticles(vo.newArticles());
        row.setReads(vo.reads());
        row.setLikes(vo.likes());
        try {
            if (exists) {
                dailyStatMapper.updateById(row);
            } else {
                dailyStatMapper.insert(row);
            }
        } catch (DuplicateKeyException e) {
            log.info("daily stat of {} already archived", date);
        }
    }

    public DashboardVO dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(29);
        Map<LocalDate, DailyStatDO> archived = dailyStatMapper.selectList(Wrappers.<DailyStatDO>lambdaQuery()
                        .ge(DailyStatDO::getStatDate, from))
                .stream().collect(Collectors.toMap(DailyStatDO::getStatDate, Function.identity(), (a, b) -> a));
        List<DailyStatVO> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DailyStatDO row = archived.get(d);
            // 今天和尚未归档的日子实时算；已归档的直接用库里的值
            days.add(row != null && !d.equals(today)
                    ? new DailyStatVO(d, row.getDau(), row.getNewUsers(), row.getNewArticles(), row.getReads(), row.getLikes())
                    : compute(d));
        }
        long totalUsers = userMapper.selectCount(null);
        long totalArticles = articleMapper.selectCount(Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getStatus, ArticleStatus.PUBLISHED));
        long pending = articleMapper.selectCount(Wrappers.<ArticleDO>lambdaQuery()
                .eq(ArticleDO::getStatus, ArticleStatus.PENDING));
        return new DashboardVO(days, totalUsers, totalArticles, pending);
    }

    private DailyStatVO compute(LocalDate date) {
        String day = date.format(DAY);
        Long dau = redis.opsForHyperLogLog().size(keys.uvDay(day));
        Map<Object, Object> daily = redis.opsForHash().entries(keys.statDaily(day));
        var start = date.atStartOfDay();
        var end = date.plusDays(1).atStartOfDay();
        long newUsers = userMapper.selectCount(Wrappers.<UserDO>lambdaQuery()
                .ge(UserDO::getCreateTime, start).lt(UserDO::getCreateTime, end));
        long newArticles = articleMapper.selectCount(Wrappers.<ArticleDO>lambdaQuery()
                .ge(ArticleDO::getPublishTime, start).lt(ArticleDO::getPublishTime, end));
        return new DailyStatVO(date, dau == null ? 0 : dau, newUsers, newArticles,
                parse(daily.get("reads")), parse(daily.get("likes")));
    }

    private static long parse(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }
}
