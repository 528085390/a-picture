# 演示实例（在线 demo）

公开演示站 <https://demo.raylxpj.us.ci/> 就是把主仓库这套代码**再跑一份**，与作者自己的图册
共用同一台机器、同一批镜像，但容器、数据库、Redis、对象存储桶、域名全部独立——演示数据随便被人
上传删除也不会污染私人图册。

```text
demo.raylxpj.us.ci  ─┬─► 宿主机 Nginx（443，按 SNI 分流到 8443，TLS 由 certbot 签发）
                     └─► 容器 web:80（映射宿主机 8880）
                              ├─ 静态 SPA
                              └─ /api ──► backend ──┬─► mysql（库 a_picture，独立卷）
                                                    ├─► redis（独立卷）
                                                    └─► R2 桶 a-picture-demo / a-picture-demo-space
```

## 与生产实例的差异

| 项 | 生产 | 演示 |
| --- | --- | --- |
| 目录 | `/opt/a-picture` | `/opt/a-picture-demo` |
| 对外端口 | 8090 | 8880 |
| 数据库 / Redis | 各自独立卷 | 各自独立卷 |
| 对象存储 | 私人桶 `apic` / `apic-space` | 演示桶 `a-picture-demo` / `a-picture-demo-space` |
| admin 口令 | 私有 | 随机生成，不写进仓库 |
| 防刷 | 无 | 注册 3 次/分钟/IP、上传 6 次/分钟/IP（容器内 Nginx） |
| 数据生命周期 | 长期 | **每天 04:00 重置**为种子快照，访客产物清空 |

## 起一套

```bash
# 1. 准备同一套镜像（在 deploy/ 里构建过的话直接复用）
docker tag a-picture-backend:latest a-picture-backend:latest   # 演示实例直接引用同名镜像

# 2. 准备目录
mkdir -p /opt/a-picture-demo/initdb /opt/a-picture-demo/snapshot
cd /opt/a-picture-demo
cp <repo>/deploy/demo/docker-compose.demo.yml .
cp <repo>/deploy/demo/nginx.conf .
cp <repo>/deploy/demo/*.sh <repo>/deploy/demo/r2-orphan-clean.py .
cp <repo>/deploy/initdb/*.sql initdb/
cp <repo>/deploy/demo/.env.demo.example .env && vi .env   # 填口令 / R2 密钥

# 3. 起服务
docker compose -f docker-compose.demo.yml up -d
```

> 演示实例的 web 容器端口默认 `8880`（可用 `WEB_PORT` 覆盖）。宿主机 Nginx 再把它挂到 TLS 域名上。

## 灌示例数据

```bash
BASE=http://127.0.0.1:8880 ./seed-demo.sh 180 1 '你的admin口令'
```

脚本先登录取 cookie，再 6 并发从 [Picsum Photos](https://picsum.photos/) 拉 1600×1200 示例图，
走正常 `/api/picture/upload` 上传。两点经验：

- picsum 会 302 到 fastly，必须 `curl -L`；某些机器上用 python `urllib` 会直接挂住。
- 拿到返回后用 `grep -q '"code":0,'` 判成功，别在单引号 heredoc 里写 `case ... in *'"code":0'*`（引号会被吃掉，导致重复上传）。

## 每日重置

```bash
./make-snapshot.sh            # 灌完数据后打一次干净快照 → snapshot/demo-snapshot.sql
crontab -e                    # 加一行：
0 4 * * * cd /opt/a-picture-demo && ./reset-demo.sh >> reset.log 2>&1
```

`reset-demo.sh` 做四件事：drop/create 库 → 导入快照 → `redis-cli flushall` 清掉访客会话 →
`r2-orphan-clean.py --apply` 删掉快照里没有的对象（访客上传的图不会长年堆在桶里）→ 重启 backend。

`r2-orphan-clean.py` 只依赖标准库，用 SigV4 直连 R2 的 S3 兼容接口：列出公开桶对象，
把「词干」在 `picture.url` / `picture.previewUrl` 里找不到的对象删掉（缩略图 `_w240`、
预览图 `_preview` 与主图同词干，所以会一起保留）。先跑一次不加 `--apply` 看预演结果。

## 宿主机 Nginx

和主实例一样：80 端口只放 ACME 验证并 301 到 https，8443 走 TLS 再反代到 `127.0.0.1:8880`，
443 由 `stream` 的 `ssl_preread` 按 SNI 分流。关键是 `client_max_body_size 12m` 和
WebSocket 的 `Upgrade` 头，否则上传 10 MB 以上和协同编辑会失败。

```nginx
server {
    listen 8443 ssl http2;
    server_name demo.example.com;
    ssl_certificate     /opt/a-picture-demo/ssl/demo.crt;
    ssl_certificate_key /opt/a-picture-demo/ssl/demo.key;
    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:8880;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```
