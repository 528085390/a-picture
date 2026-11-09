#!/usr/bin/env python3
"""删除演示实例对象存储里的"孤儿对象"（数据库已无引用）。

演示站每天重置数据库，访客上传的图片会从库里消失，但对象存储里的文件还在。
本脚本对比 picture 表里的 url / previewUrl 与公开桶的对象列表，删掉没人引用的那些。

用法：
  python3 r2-orphan-clean.py            # 只列出，不删除
  python3 r2-orphan-clean.py --apply    # 真删
环境变量（默认读同目录 .env）：R2_ENDPOINT / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_BUCKET_NAME
依赖：只用到标准库；数据库通过 docker compose exec mysql 读取。
"""
import datetime
import hashlib
import hmac
import os
import subprocess
import sys
import urllib.parse
import urllib.request
import socket
import xml.etree.ElementTree as ET

# 有些 ARM 云主机的 IPv6 出海是坏的：R2 域名会先解析到 IPv6，python 不做
# Happy Eyeballs 就会一直卡住（curl 会自动回落 IPv4，所以用 curl 试是好的）。
# 这里强制只走 IPv4。
_orig_getaddrinfo = socket.getaddrinfo


def _ipv4_only(host, port, family=0, type=0, proto=0, flags=0):
    return _orig_getaddrinfo(host, port, socket.AF_INET, type, proto, flags)


socket.getaddrinfo = _ipv4_only

HERE = os.path.dirname(os.path.abspath(__file__))
SERVICE = "s3"
REGION = "auto"


def load_env(path):
    env = {}
    if not os.path.exists(path):
        return env
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def db_referenced_stems():
    """取出数据库里引用到的对象 key（去掉扩展名当词干）。"""
    sql = (
        "select url from picture "
        "union select previewUrl from picture "
        "union select url from live_video where url is not null"
    )
    cmd = ["docker", "compose", "exec", "-T", "mysql", "sh", "-c",
           'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" a_picture -N -e "%s"' % sql.replace('"', '\\"')]
    try:
        out = subprocess.run(cmd, cwd=HERE, capture_output=True, text=True,
                            stdin=subprocess.DEVNULL, timeout=120)
    except Exception as exc:  # noqa: BLE001
        sys.exit("读取数据库失败：%s" % exc)
    if out.returncode != 0:
        # live_video 表可能不存在，退回只查 picture
        cmd[-1] = ['mysql -uroot -p"$MYSQL_ROOT_PASSWORD" a_picture -N -e "select url from picture union select previewUrl from picture"'][0]
        out = subprocess.run(cmd, cwd=HERE, capture_output=True, text=True,
                             stdin=subprocess.DEVNULL, timeout=120)
    if out.returncode != 0:
        sys.exit("读取数据库失败：%s" % (out.stderr or "")[:400])

    stems = set()
    for line in out.stdout.splitlines():
        url = line.strip()
        if not url or "://" not in url:
            continue
        key = urllib.parse.urlparse(url).path.lstrip("/")
        if not key:
            continue
        stems.add(os.path.splitext(key)[0])
    return stems


def sign(key, msg):
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def signing_key(secret, datestamp, region, service):
    k_date = sign(("AWS4" + secret).encode("utf-8"), datestamp)
    k_region = sign(k_date, region)
    k_service = sign(k_region, service)
    return sign(k_service, "aws4_request")


def s3_request(method, endpoint, bucket, query=None, body=b""):
    parsed = urllib.parse.urlparse(endpoint)
    host = parsed.netloc
    query = query or {}
    now = datetime.datetime.now(datetime.timezone.utc)
    amzdate = now.strftime("%Y%m%dT%H%M%SZ")
    datestamp = now.strftime("%Y%m%d")

    canonical_uri = "/" + bucket
    canonical_query = "&".join(
        "%s=%s" % (urllib.parse.quote(k, safe="-_.~"), urllib.parse.quote(str(v), safe="-_.~"))
        for k, v in sorted(query.items())
    )
    payload_hash = hashlib.sha256(body).hexdigest()
    canonical_headers = (
        "host:%s\nx-amz-content-sha256:%s\nx-amz-date:%s\n" % (host, payload_hash, amzdate)
    )
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join(
        [method, canonical_uri, canonical_query, canonical_headers, signed_headers, payload_hash]
    )
    scope = "%s/%s/%s/aws4_request" % (datestamp, REGION, SERVICE)
    string_to_sign = "\n".join(
        ["AWS4-HMAC-SHA256", amzdate, scope, hashlib.sha256(canonical_request.encode()).hexdigest()]
    )
    signature = hmac.new(
        signing_key(os.environ["R2_SECRET_ACCESS_KEY"], datestamp, REGION, SERVICE),
        string_to_sign.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    auth = (
        "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s"
        % (os.environ["R2_ACCESS_KEY_ID"], scope, signed_headers, signature)
    )

    url = "https://%s%s" % (host, canonical_uri)
    if canonical_query:
        url += "?" + canonical_query
    req = urllib.request.Request(
        url,
        data=body if method == "POST" else None,
        method=method,
        headers={
            "x-amz-date": amzdate,
            "x-amz-content-sha256": payload_hash,
            "Authorization": auth,
            "Content-Type": "application/xml",
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read()


def list_keys(endpoint, bucket):
    keys, token = [], None
    while True:
        query = {"list-type": "2", "max-keys": "1000"}
        if token:
            query["continuation-token"] = token
        xml = s3_request("GET", endpoint, bucket, query)
        root = ET.fromstring(xml)
        ns = {"s3": root.tag.split("}")[0].strip("{")} if root.tag.startswith("{") else {}
        for c in root.findall("s3:Contents" if ns else "Contents", ns):
            key = c.find("s3:Key" if ns else "Key", ns).text
            keys.append(key)
        truncated = root.find("s3:IsTruncated" if ns else "IsTruncated", ns)
        if truncated is not None and truncated.text == "true":
            token = root.find("s3:NextContinuationToken" if ns else "NextContinuationToken", ns).text
        else:
            return keys


def delete_keys(endpoint, bucket, keys):
    for i in range(0, len(keys), 500):
        chunk = keys[i:i + 500]
        body = ("<Delete><Quiet>true</Quiet>%s</Delete>" % "".join(
            "<Object><Key>%s</Key></Object>" % k for k in chunk
        )).encode("utf-8")
        s3_request("POST", endpoint, bucket, {"delete": ""}, body)
        print("  已删除 %d 个对象" % len(chunk))


def main():
    apply = "--apply" in sys.argv
    for key, value in load_env(os.path.join(HERE, ".env")).items():
        os.environ.setdefault(key, value)
    for need in ("R2_ENDPOINT", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY", "R2_BUCKET_NAME"):
        if not os.environ.get(need):
            sys.exit("缺少环境变量 %s" % need)

    endpoint, bucket = os.environ["R2_ENDPOINT"], os.environ["R2_BUCKET_NAME"]
    stems = db_referenced_stems()
    print("数据库引用对象数：%d" % len(stems))

    keys = list_keys(endpoint, bucket)
    print("桶内对象数：%d" % len(keys))

    orphans = []
    for k in keys:
        stem = os.path.splitext(k)[0]
        if not any(stem == s or stem.startswith(s) or s.startswith(stem) for s in stems):
            orphans.append(k)
    print("孤儿对象：%d" % len(orphans))
    for k in orphans[:10]:
        print("  -", k)
    if not apply:
        print("（预演模式，加 --apply 才会删除）")
        return
    if orphans:
        delete_keys(endpoint, bucket, orphans)
    print("完成")


if __name__ == "__main__":
    main()
