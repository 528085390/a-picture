#!/usr/bin/env bash
# 给演示实例打一份"干净状态"的数据库快照，供 reset-demo.sh 每日恢复
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p snapshot
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --skip-add-locks --default-character-set=utf8mb4 a_picture' > snapshot/demo-snapshot.sql
echo "已写入 snapshot/demo-snapshot.sql（$(du -h snapshot/demo-snapshot.sql | cut -f1)）"
