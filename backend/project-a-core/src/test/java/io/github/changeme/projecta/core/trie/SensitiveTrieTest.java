package io.github.changeme.projecta.core.trie;

import io.github.changeme.projecta.api.enums.SensitiveLevel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveTrieTest {

    private final SensitiveTrie trie = SensitiveTrie.build(new LinkedHashMap<>(Map.of(
            "赌", SensitiveLevel.REVIEW,
            "赌博", SensitiveLevel.BLOCK,
            "傻瓜", SensitiveLevel.REPLACE,
            "spam", SensitiveLevel.REPLACE)));

    @Test
    void longestMatchWins() {
        var hits = trie.findAll("这是赌博网站");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).word()).isEqualTo("赌博");
        assertThat(hits.get(0).level()).isEqualTo(SensitiveLevel.BLOCK);
    }

    @Test
    void shorterWordStillMatchesAlone() {
        assertThat(trie.findAll("小赌怡情")).extracting(SensitiveTrie.Hit::level).containsExactly(SensitiveLevel.REVIEW);
    }

    @Test
    void skipsSeparatorsAndIgnoresCase() {
        assertThat(trie.findAll("赌 * 博")).extracting(SensitiveTrie.Hit::word).containsExactly("赌 * 博");
        assertThat(trie.findAll("SPAM here")).hasSize(1);
    }

    @Test
    void replaceOnlyGivenLevelsAndKeepsSpacing() {
        assertThat(trie.replace("你个傻 瓜，别去赌博", SensitiveLevel.REPLACE)).isEqualTo("你个* *，别去赌博");
        assertThat(trie.replace("干净的文本", SensitiveLevel.values())).isEqualTo("干净的文本");
    }

    @Test
    void emptyInputs() {
        assertThat(trie.findAll(null)).isEmpty();
        assertThat(trie.findAll("")).isEmpty();
        assertThat(SensitiveTrie.build(Map.of()).findAll("任何文本")).isEmpty();
    }
}
