#!/usr/bin/env python3
"""汇总 JMeter 的 CSV 结果（.jtl）。

单个文件：python3 summarize.py a.jtl            -> 打印一行 JSON
多次运行取中位数：python3 summarize.py a.jtl b.jtl c.jtl --median
"""
import csv
import json
import statistics
import sys
from collections import Counter


def percentile(sorted_values, p):
    if not sorted_values:
        return 0
    k = max(0, min(len(sorted_values) - 1, int(round(p / 100 * len(sorted_values) + 0.5)) - 1))
    return sorted_values[k]


def summarize(path):
    elapsed, codes, start, end, errors = [], Counter(), None, None, 0
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ts, ms = int(row["timeStamp"]), int(row["elapsed"])
            elapsed.append(ms)
            codes[row["responseCode"]] += 1
            start = ts if start is None else min(start, ts)
            end = ts + ms if end is None else max(end, ts + ms)
            if row["success"] != "true":
                errors += 1
    elapsed.sort()
    n = len(elapsed)
    seconds = max((end - start) / 1000, 0.001) if n else 1
    return {
        "samples": n,
        "throughput": round(n / seconds, 1),
        "avg_ms": round(sum(elapsed) / n, 1) if n else 0,
        "p50_ms": percentile(elapsed, 50),
        "p95_ms": percentile(elapsed, 95),
        "p99_ms": percentile(elapsed, 99),
        "error_pct": round(errors * 100 / n, 2) if n else 0,
        "codes": dict(codes),
    }


def median_of(results):
    keys = ["samples", "throughput", "avg_ms", "p50_ms", "p95_ms", "p99_ms", "error_pct"]
    merged = {k: statistics.median(r[k] for r in results) for k in keys}
    codes = Counter()
    for r in results:
        codes.update(r["codes"])
    merged["codes"] = dict(codes)
    merged["runs"] = len(results)
    return merged


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    results = [summarize(p) for p in args]
    out = median_of(results) if "--median" in sys.argv else results[0]
    print(json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()
