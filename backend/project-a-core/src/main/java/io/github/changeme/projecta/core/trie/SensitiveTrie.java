package io.github.changeme.projecta.core.trie;

import io.github.changeme.projecta.api.enums.SensitiveLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词前缀树（DFA）。构建后只读，所以可以被多个线程并发使用；
 * 词库变更时整棵重建再替换引用（见 SensitiveWordService），而不是原地修改，避免读到"半棵树"。
 *
 * 匹配采用"最长匹配"：词库同时有"赌"和"赌博"时，"赌博网站"命中的是"赌博"。
 * 匹配前会忽略大小写，并跳过空格和常见符号，防止用"赌 博"、"赌*博"绕过。
 */
public final class SensitiveTrie {

    private final Node root = new Node();

    private static final class Node {
        private final Map<Character, Node> children = new HashMap<>();
        private SensitiveLevel level;
    }

    public record Hit(String word, SensitiveLevel level, int start, int end) {
    }

    public static SensitiveTrie build(Map<String, SensitiveLevel> words) {
        SensitiveTrie trie = new SensitiveTrie();
        words.forEach(trie::add);
        return trie;
    }

    private void add(String word, SensitiveLevel level) {
        if (word == null || word.isBlank()) {
            return;
        }
        Node node = root;
        for (char c : word.toCharArray()) {
            if (isSkippable(c)) {
                continue;
            }
            node = node.children.computeIfAbsent(normalize(c), k -> new Node());
        }
        node.level = level;
    }

    /** 返回所有命中，命中区间不重叠，按出现顺序排列。 */
    public List<Hit> findAll(String text) {
        List<Hit> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        int i = 0;
        while (i < text.length()) {
            if (isSkippable(text.charAt(i))) {
                i++;
                continue;
            }
            int matchEnd = -1;
            SensitiveLevel matchLevel = null;
            Node node = root;
            for (int j = i; j < text.length(); j++) {
                char c = text.charAt(j);
                if (isSkippable(c)) {
                    continue;
                }
                node = node.children.get(normalize(c));
                if (node == null) {
                    break;
                }
                if (node.level != null) {
                    matchEnd = j + 1;
                    matchLevel = node.level;
                }
            }
            if (matchEnd > 0) {
                hits.add(new Hit(text.substring(i, matchEnd), matchLevel, i, matchEnd));
                i = matchEnd;
            } else {
                i++;
            }
        }
        return hits;
    }

    /** 把指定级别的命中替换成等长的 *（只替换非跳过字符，保留原文里的空格排版）。 */
    public String replace(String text, SensitiveLevel... levels) {
        List<Hit> hits = findAll(text);
        if (hits.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (Hit hit : hits) {
            if (!contains(levels, hit.level())) {
                continue;
            }
            for (int k = hit.start(); k < hit.end(); k++) {
                if (!isSkippable(sb.charAt(k))) {
                    sb.setCharAt(k, '*');
                }
            }
        }
        return sb.toString();
    }

    private static boolean contains(SensitiveLevel[] levels, SensitiveLevel level) {
        for (SensitiveLevel l : levels) {
            if (l == level) {
                return true;
            }
        }
        return false;
    }

    private static char normalize(char c) {
        return Character.toLowerCase(c);
    }

    private static boolean isSkippable(char c) {
        return Character.isWhitespace(c) || "*-_.,，。!！?？~·`'\"|/\\".indexOf(c) >= 0;
    }
}
