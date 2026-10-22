package io.github.qq3939.codenest.service.interaction;

import io.github.qq3939.codenest.core.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void roundTrip() {
        String cursor = CursorCodec.encode(1_792_000_000_123L, 2113103131792932865L);
        CursorCodec.Position pos = CursorCodec.decode(cursor);
        assertThat(pos.time()).isEqualTo(1_792_000_000_123L);
        assertThat(pos.id()).isEqualTo(2113103131792932865L);
    }

    @Test
    void blankMeansFirstPageAndGarbageIsRejected() {
        assertThat(CursorCodec.decode(null)).isNull();
        assertThat(CursorCodec.decode("")).isNull();
        assertThatThrownBy(() -> CursorCodec.decode("!!!")).isInstanceOf(BizException.class);
    }
}
