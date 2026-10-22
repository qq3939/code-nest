package io.github.qq3939.codenest.service.rank;

import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HotScoreTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);

    @Test
    void formula() {
        // (10*2 + 5*3 + 2*4 + 100*0.1) / (2 + 2)^1.5 = 53 / 8
        double score = HotScore.compute(new ArticleCountVO(100, 10, 5, 2), now.minusHours(2), now);
        assertThat(score).isCloseTo(53.0 / 8, within(1e-9));
    }

    @Test
    void decaysOverTime() {
        ArticleCountVO counts = new ArticleCountVO(1000, 50, 10, 10);
        double fresh = HotScore.compute(counts, now.minusHours(1), now);
        double old = HotScore.compute(counts, now.minusDays(3), now);
        assertThat(fresh).isGreaterThan(old * 10);
    }

    @Test
    void noInteractionsIsZeroAndFutureTimeIsSafe() {
        assertThat(HotScore.compute(ArticleCountVO.zero(), now, now)).isZero();
        assertThat(HotScore.compute(new ArticleCountVO(10, 0, 0, 0), now.plusHours(1), now)).isPositive();
    }
}
