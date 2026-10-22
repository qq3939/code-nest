#!/usr/bin/env bash
# 压测入口（SPEC 14.2 / 14.3）。先执行 bash perf/prepare.sh。
# 用法：bash perf/run.sh 1|2|3|4|5|all
# 可调：RUNS（每组次数，默认 3，取中位数）、THREADS、DURATION（秒）、JM_HOST（JMeter 容器访问后端用的主机名）
# JMeter 跑在 alpine/jmeter 容器里，结果写到 perf/results/：raw/ 是原始 jtl，scenario-N.md 是汇总表。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PERF="$ROOT/perf"
RAW="$PERF/results/raw"
mkdir -p "$RAW"
# shellcheck disable=SC1091
source "$PERF/data/meta.env"

RUNS="${RUNS:-3}"
THREADS="${THREADS:-50}"
DURATION="${DURATION:-30}"
# Docker Desktop（Windows/macOS）和 Linux 都能用 host.docker.internal 访问宿主机（Linux 需要 --add-host，下面已经加了）
JM_HOST="${JM_HOST:-host.docker.internal}"
PORT="${BASE##*:}"
PREFIX="${REDIS_PREFIX:-project-a:}"

env_get() { grep -E "^$1=" "$ROOT/.env" | head -1 | cut -d= -f2-; }
compose() { docker compose -f "$ROOT/docker-compose.yml" "$@"; }
mysql_q() { compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u"$MYSQL_USER" "$MYSQL_DATABASE"' <<<"$1"; }
redis_cli() { compose exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli "$@"' _ "$@"; }

admin_token() {
  curl -sf -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$(env_get ADMIN_USERNAME)\",\"password\":\"$(env_get ADMIN_PASSWORD)\"}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])'
}
ADMIN_TOKEN="$(admin_token)"

switch() {  # switch countStrategy redis-batch
  curl -sf -X PUT "$BASE/api/admin/dev/strategies" -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' -d "{\"$1\":\"$2\"}" >/dev/null
  echo "    strategy $1 = $2"
}

jmeter() {  # jmeter plan.jmx out.jtl [-Jk=v ...]
  local plan="$1" out="$2"; shift 2
  rm -f "$RAW/$out"
  docker run --rm --add-host=host.docker.internal:host-gateway -v "$PERF:/perf" alpine/jmeter:5.6.3 \
    -n -t "/perf/plans/$plan" -l "/perf/results/raw/$out" -j /dev/null \
    -Jhost="$JM_HOST" -Jport="$PORT" -Jdata=/perf/data "$@" >/dev/null 2>&1
}

median() { python3 "$PERF/tools/summarize.py" "$@" --median; }

row() {  # 把 summarize 的 JSON 转成 markdown 表格的一行
  python3 -c '
import json, sys
label, extra = sys.argv[1], sys.argv[3:] if len(sys.argv) > 3 else []
d = json.loads(sys.argv[2])
cells = [label, d["throughput"], d["avg_ms"], d["p50_ms"], d["p95_ms"], d["p99_ms"], d["error_pct"]] + extra
print("| " + " | ".join(str(c) for c in cells) + " |")' "$@"
}

header() {
  local file="$1" title="$2" extra="${3:-}"
  {
    echo "# $title"
    echo
    echo "- 时间：$(date '+%Y-%m-%d %H:%M')；每组 $RUNS 次取中位数；线程数 $THREADS；单次时长 ${DURATION}s"
    echo "- 机器：$(nproc 2>/dev/null || echo '?') 核，内存 $(free -g 2>/dev/null | awk '/Mem:/{print $2 "G"}')（本机测试，只看相对变化）"
    echo
    echo "| 配置 | 吞吐（次/秒） | 平均（ms） | P50 | P95 | P99 | 错误率% |$extra"
    echo "|---|---|---|---|---|---|---|$( [ -n "$extra" ] && echo "$extra" | sed 's/[^|]*|/---|/g')"
  } > "$file"
}

scenario1() {
  local out="$PERF/results/scenario-1.md"
  echo "==> 场景 1：热点文章详情，对比缓存模式"
  header "$out" "场景 1：热点文章详情（90% 请求落在 10 篇文章）"
  for mode in none redis two-level; do
    switch cacheMode "$mode"
    jmeter 01-hot-article-detail.jmx warmup.jtl -Jthreads="$THREADS" -Jduration=10
    local files=()
    for i in $(seq 1 "$RUNS"); do
      jmeter 01-hot-article-detail.jmx "s1-$mode-$i.jtl" -Jthreads="$THREADS" -Jduration="$DURATION"
      files+=("$RAW/s1-$mode-$i.jtl")
    done
    row "$mode" "$(median "${files[@]}")" >> "$out"
  done
  switch cacheMode two-level
  cat "$out"
}

scenario2() {
  local out="$PERF/results/scenario-2.md" loops=20
  local expected=$((THREADS * loops))
  echo "==> 场景 2：$THREADS 个线程各读同一篇文章 $loops 次，对比计数策略"
  header "$out" "场景 2：并发阅读同一篇文章（每次 $expected 个请求）" " 实际增加 | 准确率 | 数据库写入（Com_update+Com_insert） |"
  for strategy in naive atomic-sql redis-batch; do
    switch countStrategy "$strategy"
    local files=() accs=() writes=() incs=()
    for i in $(seq 1 "$RUNS"); do
      local before w_before after w_after
      before="$(mysql_q "SELECT read_count FROM article_stat WHERE article_id = $READ_ARTICLE;")"
      w_before="$(mysql_q "SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_update','Com_insert');" | awk '{s+=$2} END {print s}')"
      jmeter 02-read-count.jmx "s2-$strategy-$i.jtl" -Jthreads="$THREADS" -Jloops="$loops" -JarticleId="$READ_ARTICLE"
      if [ "$strategy" = "redis-batch" ]; then
        # 切走 redis-batch 时会先把 Redis 里的计数刷回数据库（beforeSwitch），借此触发一次写回
        switch countStrategy atomic-sql; switch countStrategy redis-batch
      fi
      after="$(mysql_q "SELECT read_count FROM article_stat WHERE article_id = $READ_ARTICLE;")"
      w_after="$(mysql_q "SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_update','Com_insert');" | awk '{s+=$2} END {print s}')"
      incs+=($((after - before))); writes+=($((w_after - w_before)))
      files+=("$RAW/s2-$strategy-$i.jtl")
    done
    local inc write
    inc="$(printf '%s\n' "${incs[@]}" | sort -n | sed -n "$(( (RUNS + 1) / 2 ))p")"
    write="$(printf '%s\n' "${writes[@]}" | sort -n | sed -n "$(( (RUNS + 1) / 2 ))p")"
    row "$strategy" "$(median "${files[@]}")" "$inc" "$(python3 -c "print(f'{$inc*100/$expected:.1f}%')")" "$write" >> "$out"
  done
  switch countStrategy redis-batch
  cat "$out"
}

scenario3() {
  local out="$PERF/results/scenario-3.md"
  echo "==> 场景 3：读取关注动态，对比 Feed 模式"
  header "$out" "场景 3：读取关注动态"
  for mode in push pull hybrid; do
    switch feedMode "$mode"
    for kind in normal bigv; do
      # 切换模式会清空收件箱：先预热一轮，让收件箱按新模式冷启动重建，再正式计时
      jmeter 03-feed-read.jmx warmup.jtl -Jthreads="$THREADS" -Jduration=10 -Jtokens="tokens_$kind.csv"
      local files=()
      for i in $(seq 1 "$RUNS"); do
        jmeter 03-feed-read.jmx "s3-$mode-$kind-$i.jtl" -Jthreads="$THREADS" -Jduration="$DURATION" -Jtokens="tokens_$kind.csv"
        files+=("$RAW/s3-$mode-$kind-$i.jtl")
      done
      row "$mode / $( [ $kind = normal ] && echo 普通用户 || echo 关注了大V)" "$(median "${files[@]}")" >> "$out"
    done
  done
  switch feedMode hybrid
  cat "$out"
}

scenario4() {
  local out="$PERF/results/scenario-4.md"
  echo "==> 场景 4：大 V 发文，对比 push 和 hybrid 的扇出耗时与 Redis 内存"
  local followers last
  followers="$(mysql_q "SELECT follower_id FROM follow WHERE followee_id = $BIGV_ID AND status = 1;")"
  last="$(mysql_q "SELECT follower_id FROM follow WHERE followee_id = $BIGV_ID AND status = 1 ORDER BY id DESC LIMIT 1;")"
  local token
  token="$(curl -sf -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$BIGV_USERNAME\",\"password\":\"${DG_PASSWORD:-password-123}\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')"
  {
    echo "# 场景 4：大 V 发文（粉丝数 $(echo "$followers" | wc -l)）"
    echo
    echo "- 模拟所有粉丝都活跃：发文前给每个粉丝建好收件箱（只含占位成员）。每组 $RUNS 次取中位数。"
    echo
    echo "| 模式 | 扇出完成耗时（ms） | Redis 内存增量（KB） | 说明 |"
    echo "|---|---|---|---|"
  } > "$out"
  for mode in push hybrid; do
    switch feedMode "$mode"
    local times=() mems=()
    for i in $(seq 1 "$RUNS"); do
      echo "$followers" | awk -v p="$PREFIX" '{print "ZADD " p "feed:inbox:" $1 " 0 0"}' | redis_cli --pipe >/dev/null
      local mem_before id t0 t1 tok
      mem_before="$(redis_cli INFO memory | awk -F: '/^used_memory:/{print $2}' | tr -d '\r')"
      id="$(curl -sf -X POST "$BASE/api/articles" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
        -d '{"title":"大V压测文章","content":"用于测量扇出耗时的文章正文，内容足够长。","categoryId":1}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["id"])')"
      tok="$(curl -sf "$BASE/api/articles/submit-token" -H "Authorization: Bearer $token" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')"
      t0="$(python3 -c 'import time; print(int(time.time()*1000))')"
      curl -sf -X POST "$BASE/api/articles/$id/publish" -H "Authorization: Bearer $token" -H "Idempotency-Token: $tok" >/dev/null
      local elapsed="-"
      if [ "$mode" = push ]; then
        for _ in $(seq 1 600); do
          if [ -n "$(redis_cli ZSCORE "${PREFIX}feed:inbox:$last" "$id" | tr -d '\r')" ]; then
            t1="$(python3 -c 'import time; print(int(time.time()*1000))')"; elapsed=$((t1 - t0)); break
          fi
          sleep 0.05
        done
      else
        sleep 3
      fi
      local mem_after
      mem_after="$(redis_cli INFO memory | awk -F: '/^used_memory:/{print $2}' | tr -d '\r')"
      times+=("$elapsed"); mems+=($(( (mem_after - mem_before) / 1024 )))
      curl -sf -X DELETE "$BASE/api/articles/$id" -H "Authorization: Bearer $token" >/dev/null || true
      # 清掉这些收件箱，下一轮重新建，保证每轮起点相同
      echo "$followers" | awk -v p="$PREFIX" '{print "DEL " p "feed:inbox:" $1}' | redis_cli --pipe >/dev/null
    done
    local t m
    t="$(printf '%s\n' "${times[@]}" | sort -n | sed -n "$(( (RUNS + 1) / 2 ))p")"
    m="$(printf '%s\n' "${mems[@]}" | sort -n | sed -n "$(( (RUNS + 1) / 2 ))p")"
    if [ "$mode" = push ]; then
      echo "| push | $t | $m | 发文时写入所有粉丝收件箱 |" >> "$out"
    else
      echo "| hybrid | 不扇出 | $m | 大 V 只写自己的发件箱，粉丝读时再合并 |" >> "$out"
    fi
  done
  switch feedMode hybrid
  cat "$out"
}

scenario5() {
  local out="$PERF/results/scenario-5.md"
  echo "==> 场景 5：同一用户高频点赞，统计 429 比例"
  local files=()
  for i in $(seq 1 "$RUNS"); do
    jmeter 05-rate-limit.jmx "s5-$i.jtl" -Jthreads=5 -Jduration=10 -JarticleId="$LIKE_ARTICLE"
    files+=("$RAW/s5-$i.jtl")
  done
  local json
  json="$(median "${files[@]}")"
  python3 - "$json" "$out" <<'PY'
import json, sys
d = json.loads(sys.argv[1]); codes = d["codes"]; total = sum(codes.values())
lines = ["# 场景 5：点赞限流（规则：每用户每秒 5 次）", "",
         f"- 5 个线程用同一个令牌连续点赞/取消点赞 10 秒，共 {d['runs']} 轮，合计 {total} 个请求", "",
         "| 状态码 | 次数 | 占比 |", "|---|---|---|"]
for code, n in sorted(codes.items()):
    lines.append(f"| {code} | {n} | {n * 100 / total:.1f}% |")
lines += ["", f"吞吐（中位数）：{d['throughput']} 次/秒，P99：{d['p99_ms']} ms。被限流的请求在 Lua 脚本里就返回，不进入业务逻辑。"]
open(sys.argv[2], "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
PY
}

case "${1:-all}" in
  1) scenario1 ;; 2) scenario2 ;; 3) scenario3 ;; 4) scenario4 ;; 5) scenario5 ;;
  all) scenario1; scenario2; scenario3; scenario4; scenario5 ;;
  *) echo "usage: $0 1|2|3|4|5|all"; exit 1 ;;
esac
