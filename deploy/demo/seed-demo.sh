#!/usr/bin/env bash
# 给演示实例灌随机示例图（Picsum Photos），走正常上传接口，不直连数据库/对象存储。
# 用法：./seed-demo.sh [张数] [起始序号] [管理员口令]
#   BASE    演示实例地址，默认 http://127.0.0.1:8880
#   ACCOUNT 登录账号，默认 admin
set -u

BASE="${BASE:-http://127.0.0.1:8880}"
ACCOUNT="${ACCOUNT:-admin}"
PASSWORD="${3:-admin123456}"
COUNT="${1:-180}"
START="${2:-1}"
CK="$(mktemp /tmp/demo-ck-XXXX.txt)"

echo "==> 登录 $BASE（$ACCOUNT）"
curl -sS -c "$CK" -X POST "$BASE/api/user/login" \
  -H 'Content-Type: application/json' \
  -d "{\"userAccount\":\"$ACCOUNT\",\"userPassword\":\"$PASSWORD\"}" -o /dev/null || exit 1

export BASE CK
echo "==> 抓取并上传 $COUNT 张（起始序号 $START，6 并发）"
seq "$START" $((START + COUNT - 1)) | xargs -P 6 -I{} bash -c '
  i=$1; n=$(printf "%04d" "$i"); f=$(mktemp /tmp/demo-seed-XXXX.jpg)
  for attempt in 1 2 3; do
    # 注意：picsum 会 302 到 fastly，必须 -L 跟随；某些环境用 python urllib 会挂住
    code=$(curl -sSL -m 40 -o "$f" -w "%{http_code}" "https://picsum.photos/seed/demo$n/1600/1200.jpg")
    if [ "$code" = "200" ] && [ -s "$f" ]; then
      r=$(curl -sS -b "$CK" -m 180 -X POST "$BASE/api/picture/upload" \
            -F "file=@$f;filename=随机示例-$n.jpg")
      if echo "$r" | grep -q "\"code\":0,"; then
        echo "OK $n"; rm -f "$f"; exit 0
      fi
      echo "UPLOADFAIL $n $(echo "$r" | head -c 120)"
    else
      echo "DLFAIL $n http=$code"
    fi
    sleep 2
  done
  rm -f "$f"; exit 1
' _ {}

rm -f "$CK"
echo "==> 完成：看看 /gallery 的张数是否符合预期（重复执行会产生同名副本，需要去重）"
