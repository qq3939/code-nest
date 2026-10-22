#!/bin/bash
# 建 bucket 并允许匿名下载。重复执行是安全的（--ignore-existing）。
# Bitnami 镜像启动时会先在后台起一次 MinIO 做初始化、再重启，健康检查可能在重启前就通过了，所以这里重试等待。
set -uo pipefail
for i in $(seq 1 30); do
  if mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  echo "waiting for minio... ($i)"
  sleep 2
done
set -e
mc mb --ignore-existing "local/$MINIO_BUCKET"
mc anonymous set download "local/$MINIO_BUCKET"
echo "bucket $MINIO_BUCKET ready"
