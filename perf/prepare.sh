#!/usr/bin/env bash
# 准备压测数据（先跑过造数：--spring.profiles.active=dev,datagen）。
# 后端要带 perf profile 启动（放宽登录限流），例如：
#   cd backend/code-nest-web && java -jar target/app.jar --spring.profiles.active=dev,perf
# 用法：在项目根目录执行 bash perf/prepare.sh
# 生成 perf/data/ 下的 CSV 和 meta.env，供 run.sh 使用。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$ROOT/perf/data"
BASE="${BASE:-http://localhost:8081}"
PASSWORD="${DG_PASSWORD:-password-123}"
mkdir -p "$DATA"

mysql_q() {
  docker compose -f "$ROOT/docker-compose.yml" exec -T mysql \
    sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"' <<<"$1"
}

login() {
  curl -sf -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])'
}

echo "==> 检查后端：$BASE"
curl -sf "$BASE/api/health" >/dev/null || { echo "后端没有启动"; exit 1; }

BIGV_IDS="$(mysql_q "SELECT GROUP_CONCAT(id) FROM user WHERE username IN ('dg_user00001','dg_user00002','dg_user00003','dg_user00004');")"
[ -n "$BIGV_IDS" ] && [ "$BIGV_IDS" != "NULL" ] || { echo "没有找到造数用户，请先运行 datagen"; exit 1; }

echo "==> 场景 1：热点文章混合列表（90% 落在热度前 10 的文章上）"
HOT="$(mysql_q "SELECT article_id FROM article_stat ORDER BY like_count DESC LIMIT 10;")"
ALL="$(mysql_q "SELECT id FROM article WHERE status='PUBLISHED' ORDER BY RAND(1) LIMIT 2000;")"
python3 - "$DATA/hot_mix.csv" <<PY
import random, sys
random.seed(1)
hot = """$HOT""".split()
cold = """$ALL""".split()
rows = [random.choice(hot) if random.random() < 0.9 else random.choice(cold) for _ in range(20000)]
open(sys.argv[1], "w").write("\n".join(rows) + "\n")
PY

echo "==> 场景 2：选一篇冷门文章做并发阅读（避免和其他场景互相影响）"
READ_ARTICLE="$(mysql_q "SELECT article_id FROM article_stat ORDER BY read_count ASC LIMIT 1;")"

echo "==> 场景 3：登录 200 个普通用户（不关注大 V）和 200 个关注了大 V 的用户"
mysql_q "SELECT u.username FROM user u WHERE u.username LIKE 'dg\\_%' AND u.id NOT IN ($BIGV_IDS)
  AND NOT EXISTS (SELECT 1 FROM follow f WHERE f.follower_id = u.id AND f.followee_id IN ($BIGV_IDS))
  AND (SELECT COUNT(*) FROM follow f WHERE f.follower_id = u.id) >= 20 ORDER BY u.id LIMIT 200;" > "$DATA/.normal_users"
mysql_q "SELECT DISTINCT u.username FROM user u JOIN follow f ON f.follower_id = u.id
  WHERE f.followee_id IN ($BIGV_IDS) AND u.id NOT IN ($BIGV_IDS) ORDER BY u.username LIMIT 200;" > "$DATA/.bigv_users"
: > "$DATA/tokens_normal.csv"; : > "$DATA/tokens_bigv.csv"
while read -r u; do login "$u" "$PASSWORD" >> "$DATA/tokens_normal.csv"; done < "$DATA/.normal_users"
while read -r u; do login "$u" "$PASSWORD" >> "$DATA/tokens_bigv.csv"; done < "$DATA/.bigv_users"
rm -f "$DATA/.normal_users" "$DATA/.bigv_users"

echo "==> 场景 5：一个普通用户的令牌"
login dg_user09999 "$PASSWORD" > "$DATA/tokens_like.csv"

cat > "$DATA/meta.env" <<EOF
BASE=$BASE
READ_ARTICLE=$READ_ARTICLE
LIKE_ARTICLE=$(echo "$HOT" | head -1)
BIGV_USERNAME=dg_user00001
BIGV_ID=$(echo "$BIGV_IDS" | cut -d, -f1)
EOF
echo "完成：$(wc -l < "$DATA/tokens_normal.csv") 个普通用户令牌，$(wc -l < "$DATA/tokens_bigv.csv") 个大 V 粉丝令牌"
