#!/usr/bin/env bash
# 把占位名 project-a / io.github.changeme 替换成正式名称（SPEC 第 1 节：项目名、GitHub 用户名定下来之后执行一次）。
#
# 用法（在项目根目录）：
#   bash scripts/rename-project.sh <项目名> <GitHub 用户名>
#   例：bash scripts/rename-project.sh dev-forum zhangsan
#
# 会替换：
#   project-a            -> dev-forum           （模块名、Redis 前缀、bucket、镜像名等）
#   project_a            -> dev_forum           （数据库名、用户名）
#   projecta             -> devforum            （Java 包名的最后一段）
#   ProjectA             -> DevForum            （启动类名）
#   io.github.changeme   -> io.github.zhangsan  （groupId 和包名前缀）
# 并移动对应的模块目录和包目录。
#
# 先提交或备份当前代码再执行；执行后用 `git status` 检查改动，再跑一遍 ./mvnw verify。
# .env 不会被改（它不在仓库里）：执行后请手动对照 .env.example 修改数据库名等。
set -euo pipefail

if [ $# -ne 2 ]; then
  sed -n '2,5p' "$0"
  exit 1
fi

NAME="$1"
GH_USER="$2"

# 项目名只允许小写字母、数字和中划线，且以字母开头：它会出现在 Maven artifactId、Docker 镜像名和 Redis key 里
if ! [[ "$NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "项目名只能包含小写字母、数字和 -，且以字母开头：$NAME" >&2
  exit 1
fi
# Java 包名不能有中划线、不能以数字开头，统一转小写并去掉非法字符
GH_PKG="$(echo "$GH_USER" | tr 'A-Z' 'a-z' | tr -cd 'a-z0-9')"
if ! [[ "$GH_PKG" =~ ^[a-z] ]]; then
  GH_PKG="u$GH_PKG"
fi

SNAKE="${NAME//-/_}"
FLAT="${NAME//-/}"
# dev-forum -> DevForum
PASCAL="$(echo "$NAME" | awk -F- '{for (i = 1; i <= NF; i++) printf "%s%s", toupper(substr($i, 1, 1)), substr($i, 2)}')"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -d backend/project-a-web ]; then
  echo "没有找到 backend/project-a-web，可能已经改过名了。" >&2
  exit 1
fi

echo "project-a -> $NAME, project_a -> $SNAKE, projecta -> $FLAT, ProjectA -> $PASCAL, io.github.changeme -> io.github.$GH_PKG"

# 1. 替换文件内容：只处理文本文件，跳过构建产物、依赖目录和 .git
mapfile -d '' FILES < <(find . \
  \( -path ./.git -o -path '*/node_modules' -o -path '*/target' -o -path '*/dist' -o -path ./perf/results -o -path ./perf/data \) -prune -o \
  -type f ! -name '.env' ! -name 'rename-project.sh' -print0)
for f in "${FILES[@]}"; do
  if grep -Iq . "$f" 2>/dev/null && grep -qE 'project-a|project_a|projecta|ProjectA|io\.github\.changeme|io/github/changeme' "$f"; then
    sed -i.bak \
      -e "s#io\.github\.changeme#io.github.$GH_PKG#g" \
      -e "s#io/github/changeme#io/github/$GH_PKG#g" \
      -e "s#ProjectA#$PASCAL#g" \
      -e "s#projecta#$FLAT#g" \
      -e "s#project_a#$SNAKE#g" \
      -e "s#project-a#$NAME#g" \
      "$f"
    rm -f "$f.bak"
  fi
done

# 2. 移动目录：先改模块目录，再改各模块里的包目录
for m in api core service web; do
  if [ -d "backend/project-a-$m" ]; then
    git mv "backend/project-a-$m" "backend/$NAME-$m" 2>/dev/null || mv "backend/project-a-$m" "backend/$NAME-$m"
  fi
done
for src in $(find backend -type d -path '*/java/io/github/changeme' -not -path '*/target/*'); do
  parent="$(dirname "$src")"
  mkdir -p "$parent/$GH_PKG"
  if [ -d "$src/projecta" ]; then
    git mv "$src/projecta" "$parent/$GH_PKG/$FLAT" 2>/dev/null || mv "$src/projecta" "$parent/$GH_PKG/$FLAT"
  fi
  rmdir "$src" 2>/dev/null || true
done

# 3. 启动类文件名
find backend -name 'ProjectAApplication.java' -not -path '*/target/*' | while read -r f; do
  nf="$(dirname "$f")/${PASCAL}Application.java"
  git mv "$f" "$nf" 2>/dev/null || mv "$f" "$nf"
done

echo
echo "完成。接下来："
echo "  1. git status 检查改动；"
echo "  2. 对照 .env.example 修改你自己的 .env（数据库名、bucket 名已经变了）；"
echo "  3. 旧的 Docker 数据卷里还是旧库名，最简单的办法是 docker compose down -v 后重新启动；"
echo "  4. cd backend && ./mvnw -B verify。"
