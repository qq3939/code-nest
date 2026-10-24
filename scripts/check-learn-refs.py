#!/usr/bin/env python3
"""检查学习文档里的代码引用（ROADMAP A0-9）。

学习文档用 `路径:行号`（`锚点`）指向代码，例如 `service/cache/CacheService.java:105`（`get`，读路径）。
代码一改，行号就可能偏移，读文档的人会对着错误的行打断点，所以在 CI 里逐条检查：
  1. 路径能解析到仓库里唯一的文件（展开 docs/learn/README.md 里的路径缩写；只写文件名时按后缀匹配）；
  2. 行号不越界；
  3. 带行号的引用必须写锚点（括号里第一个反引号里的函数名、字段名或配置键），且锚点出现在该行上下 3 行内。
`:行号` 这种续写指同一行里上一个引用的文件。只写路径、不写行号的引用只检查文件存在。

用法：python3 scripts/check-learn-refs.py [文档 ...]，默认检查 docs/learn/*.md；有问题时退出码为 1。
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
README = os.path.join(ROOT, "docs", "learn", "README.md")
# target/ 里有 application.yml、V1__init_schema.sql 等资源文件的副本，按文件名匹配时会误判成"不唯一"
SKIP_DIRS = {".git", "target", "node_modules", "dist"}
WINDOW = 3

EXT = r"(?:java|kt|ts|js|vue|yml|yaml|xml|sql|lua|json|sh|py|conf|properties|toml)"
REF = re.compile(r"^(?P<path>(?:…/)?[\w.*/-]+\." + EXT + r"|(?:[\w.-]+/)*Dockerfile)(?::(?P<line>\d+))?$")
CONT = re.compile(r"^:(?P<line>\d+)$")
CODE = re.compile(r"`([^`]+)`")
ANCHOR = re.compile(r"^（`([^`]+)`")


def load_abbrevs():
    """README 的"路径缩写"表：| `api/…` | `backend/…/api/…` |，缩写和实际目录都以 … 结尾。"""
    abbrevs = {}
    with open(README, encoding="utf-8") as f:
        for row in f:
            m = re.match(r"^\|\s*`([^`]+)…`\s*\|\s*`([^`]+)…`\s*\|", row)
            if m:
                abbrevs[m.group(1)] = m.group(2)
    return abbrevs


def repo_files():
    files = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        rel = os.path.relpath(dirpath, ROOT).replace(os.sep, "/")
        files.extend(name if rel == "." else rel + "/" + name for name in filenames)
    return files


class Resolver:
    def __init__(self):
        self.abbrevs = load_abbrevs()
        self.files = repo_files()
        self.cache = {}

    def resolve(self, path):
        """返回 (仓库内相对路径, 错误信息)。"""
        if path not in self.cache:
            self.cache[path] = self._resolve(path)
        return self.cache[path]

    def _resolve(self, path):
        for short, full in self.abbrevs.items():
            if path.startswith(short):
                return self._unique(sorted(glob.glob(os.path.join(ROOT, full + path[len(short):]))), path, absolute=True)
        if path.startswith("…/"):
            path = path[2:]
        elif os.path.isfile(os.path.join(ROOT, path)):
            return path, None
        return self._unique([f for f in self.files if f == path or f.endswith("/" + path)], path)

    def _unique(self, matches, path, absolute=False):
        if absolute:
            matches = [os.path.relpath(m, ROOT).replace(os.sep, "/") for m in matches]
        matches = [m for m in matches if not SKIP_DIRS & set(m.split("/")[:-1])]
        if len(matches) == 1:
            return matches[0], None
        if not matches:
            return None, "找不到文件"
        return None, "匹配到多个文件，请写更长的路径：" + "、".join(matches)


def read_lines(path, cache={}):
    if path not in cache:
        with open(os.path.join(ROOT, path), encoding="utf-8") as f:
            cache[path] = f.read().splitlines()
    return cache[path]


def check_ref(resolver, path, line_no, anchor):
    target, err = resolver.resolve(path)
    if err:
        return target, err
    if line_no is None:
        return target, None
    lines = read_lines(target)
    if not 1 <= line_no <= len(lines):
        return target, "行号越界（文件只有 %d 行）" % len(lines)
    if anchor is None:
        return target, "缺少锚点：写成 `路径:行号`（`函数名`）"
    lo, hi = max(1, line_no - WINDOW), min(len(lines), line_no + WINDOW)
    if any(anchor in lines[i - 1] for i in range(lo, hi + 1)):
        return target, None
    hits = [i for i, text in enumerate(lines, 1) if anchor in text]
    hint = "，文件里出现在第 %s 行" % "、".join(map(str, hits[:5])) if hits else "，文件里没有这个名字"
    return target, "锚点 `%s` 不在第 %d±%d 行%s" % (anchor, line_no, WINDOW, hint)


def check_doc(resolver, doc):
    errors, count = [], 0
    in_fence = False
    with open(doc, encoding="utf-8") as f:
        doc_lines = f.read().splitlines()
    for n, text in enumerate(doc_lines, 1):
        if text.lstrip().startswith(("```", "~~~")):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        current = None  # 同一行里上一个引用的路径，给 `:行号` 续写用
        for m in CODE.finditer(text):
            span = m.group(1)
            ref, cont = REF.match(span), CONT.match(span)
            if not ref and not cont:
                continue
            count += 1
            if cont and current is None:
                errors.append((n, span, "续写的 `:行号` 前面没有同一行的文件引用"))
                continue
            path = ref.group("path") if ref else current
            line = (ref or cont).group("line")
            anchor = ANCHOR.match(text[m.end():])
            _, err = check_ref(resolver, path, int(line) if line else None, anchor.group(1) if anchor else None)
            if err:
                errors.append((n, span if ref else "%s%s" % (path, span), err))
            current = path
    return errors, count


def main(argv):
    docs = argv or sorted(glob.glob(os.path.join(ROOT, "docs", "learn", "*.md")))
    resolver = Resolver()
    total, failed = 0, 0
    for doc in docs:
        errors, count = check_doc(resolver, doc)
        total += count
        try:
            rel = os.path.relpath(doc, ROOT).replace(os.sep, "/")
        except ValueError:  # Windows 上文档和仓库不在同一个盘符
            rel = doc
        for n, span, err in errors:
            failed += 1
            print("%s:%d: `%s` %s" % (rel, n, span, err))
    print("检查了 %d 个引用，%d 个有问题" % (total, failed))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
