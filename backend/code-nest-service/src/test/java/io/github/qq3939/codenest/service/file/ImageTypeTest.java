package io.github.qq3939.codenest.service.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    private static byte[] pad(int... head) {
        byte[] bytes = new byte[16];
        for (int i = 0; i < head.length; i++) {
            bytes[i] = (byte) head[i];
        }
        return bytes;
    }

    @Test
    void detectsByMagicNumber() {
        assertThat(ImageType.detect(pad(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo(ImageType.JPG);
        assertThat(ImageType.detect(pad(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect(pad('G', 'I', 'F', '8', '9', 'a'))).isEqualTo(ImageType.GIF);
        assertThat(ImageType.detect(pad('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).isEqualTo(ImageType.WEBP);
    }

    @Test
    void rejectsHtmlRenamedToPngAndTinyFiles() {
        assertThat(ImageType.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(ImageType.detect(null)).isNull();
    }
}
