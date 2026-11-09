#!/usr/bin/env bash
# 演示实例每日重置：恢复数据库快照 → 清 Redis 会话 → 删掉快照里没有的对象 → 重启后端
# 依赖：同目录 snapshot/demo-snapshot.sql（用 make-snapshot.sh 生成）
set -uo pipefail
cd "$(dirname "$0")"

SNAP="snapshot/demo-snapshot.sql"
[ -f "$SNAP" ] || { echo "缺少快照 $SNAP，先跑 ./make-snapshot.sh"; exit 1; }

echo "[$(date '+%F %T')] reset start"
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "drop database if exists a_picture; create database a_picture default character set utf8mb4 collate utf8mb4_unicode_ci;"' || exit 1
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" a_picture' < "$SNAP" || exit 1
docker compose exec -T redis redis-cli flushall >/dev/null || true

# 访客上传的图片此刻已脱离数据库，清掉对象存储里的孤儿对象（脚本自带 --dry-run 预演）
if [ -f r2-orphan-clean.py ]; then
  python3 r2-orphan-clean.py --apply || echo "WARN: 孤儿对象清理失败，可稍后手动重跑"
fi

docker compose restart backend >/dev/null 2>&1 || true
echo "[$(date '+%F %T')] reset done"
