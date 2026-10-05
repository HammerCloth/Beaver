# 生产环境部署指南（Docker + 反向代理）

本文说明如何把 **Project Zero** 部署到一台远程 Linux 主机（VPS），并让用户通过浏览器访问。  
部署命令均在 **服务器上** 通过 SSH 执行。

---

## 发行版说明（请先读）

| 推荐 | 说明 |
|------|------|
| **Ubuntu 22.04 / 24.04 LTS** | **默认按本文主流程部署**：glibc 新，可安装 **Node 22** 与官方 Docker，文档步骤与下列章节一一对应。 |

| 其它 | 说明 |
|------|------|
| **Debian 12** | 与 Ubuntu 类似，可将下方 `apt` 中发行版代号按 Debian 文档微调，或直接使用 Docker 官方「Debian」安装页。 |
| **Rocky Linux / AlmaLinux 9** | 使用 **附录 A**（yum/dnf、firewalld），勿再使用已 EOL 的 CentOS 8。 |
| **CentOS 7** | **glibc 2.17**，无法安装 NodeSource 的 Node 22，见 **附录 B**。强烈建议换机到 Ubuntu 24.04 或 Rocky 9。 |

**重要目录：** 仓库内 Compose 与 Caddy 配置在 **`zero/`** 下。下文默认你已 `cd` 到 **`zero`**（与 `docker-compose.yml` 同级）。

**当前栈（与旧版 Go/React 不同）：**

| 组件 | 说明 |
|------|------|
| 后端 | **Spring Boot 3**，容器内 `APP_PORT=8080`，SQLite 文件在卷 **`zero_data`**（`/data/zero.db`）。启动时 Flyway 自动迁移。 |
| 镜像 | GitHub Actions 构建 **`ghcr.io/hammercloth/beaver-backend`** 与 **`ghcr.io/hammercloth/beaver-web`**（前端已打进 Web 镜像），支持 amd64 / arm64。服务器只拉镜像，**不需要 Node、不在服务器上编译**。 |
| Web | Caddy（容器 `beaver-caddy`）：`/api/*`、`/mcp`、`/oauth/*` → `backend:8080`，其余路径为静态 SPA。只在本机 **`127.0.0.1:8080`** 监听，**不处理域名与 HTTPS**。 |
| 入口 | **你自己的反向代理**（§8）：负责域名和 HTTPS 证书，转发到 `127.0.0.1:8080`。同一台机器上的其他站点也由它统一处理。 |

---

## 0. 你需要先具备什么

| 项目 | 说明 |
|------|------|
| 一台 VPS | 1 核 2G 可用，有公网 IP；**本文主流程以 Ubuntu 24.04 为例** |
| SSH 登录 | 本机能执行 `ssh user@服务器IP` |
| 代码托管 | 仓库已在 GitHub（或 Gitee 等），服务器能 `git clone` |
| 域名（推荐） | 用于 HTTPS；无域名时可用 IP + HTTP 测试（见 §14） |
| 防火墙 | 云安全组 + 本机放行 **22 / 80 / 443** |

---

## 1. 安装 Docker（Ubuntu 24.04）

SSH 登录后执行（来自 [Docker 官方 Ubuntu 安装说明](https://docs.docker.com/engine/install/ubuntu/)，可按官方更新微调）：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable docker --now
```

验证：

```bash
docker --version
docker compose version
```

当前用户免 `sudo` 使用 Docker（需**重新登录 SSH** 后生效）：

```bash
sudo usermod -aG docker "$USER"
```

---

## 2. （可选）安装 Node.js

服务器**不需要 Node**：镜像由 CI 构建，部署时直接拉取。只有在服务器上从源码构建（`BUILD_FROM_SOURCE=1`）时才用得到，而且源码构建也在 Docker 里完成，同样不需要在系统里装 Node。

只有想在服务器上跑前端开发服务器之类的场景才需要它：

```bash
curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
sudo apt-get install -y nodejs
```

---

## 3. 防火墙：ufw（Ubuntu）

- **云控制台**：安全组放行 **TCP 22、80、443**。
- **本机 ufw**：

```bash
sudo apt-get install -y ufw
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

---

## 4. 把代码拉到服务器

```bash
cd /opt
sudo git clone https://github.com/你的用户名/你的仓库名.git
cd 你的仓库名
```

若仓库根下还有 **`zero`** 子目录：

```bash
cd zero
pwd
# 应能看到 docker-compose.yml、Caddyfile、backend/、frontend-vue/
```

以后更新：

```bash
cd /opt/你的仓库名/zero
git pull
```

---

## 5. 生成密钥（不要提交到 Git）

JWT 密钥不是仓库里的文件，由你生成随机串，写入 `.env`。

```bash
openssl rand -hex 48
openssl rand -hex 48
```

两次输出分别作为 **`JWT_ACCESS_SECRET`** 与 **`JWT_REFRESH_SECRET`**，保存在安全处。

---

## 6. 配置环境变量

在 **`zero` 目录**创建 `.env`：

```bash
cd /opt/你的仓库名/zero
nano .env
```

示例：

```env
FRONTEND_ORIGIN=https://app.example.com
JWT_ACCESS_SECRET=第一个随机串
JWT_REFRESH_SECRET=第二个随机串
# 可选：Beaver 在本机监听的端口与地址，默认 127.0.0.1:8080
# BEAVER_PORT=8080
# BEAVER_BIND=127.0.0.1
```

```bash
chmod 600 .env
```

- `FRONTEND_ORIGIN`：必须与浏览器地址栏一致（含 `https://`）。若同时存在 **www 与根域**访问，可写多个来源（**英文逗号分隔**），例如：`https://www.example.com,https://example.com`。只配一个而用户访问另一个时，登录后接口会因 **CORS** 失败，页面可能白屏。
- `BEAVER_PORT` / `BEAVER_BIND`：Beaver 对宿主机开放的端口和地址。默认只绑定 `127.0.0.1`，外网无法直接访问，必须经过 §8 的反向代理；局域网内直接访问可设 `BEAVER_BIND=0.0.0.0`。
- 旧版本的 `CADDY_SITE` 已不再使用，留在 `.env` 里也没有影响。
- 勿将 `.env` 提交到 Git（`zero/.gitignore` 已忽略）。

---

## 7. 镜像从哪来

`docker-compose.yml` 里的两个服务直接使用 CI 发布的镜像：

| 服务 | 镜像 |
|------|------|
| `backend` | `ghcr.io/hammercloth/beaver-backend:${BEAVER_VERSION:-latest}` |
| `caddy` | `ghcr.io/hammercloth/beaver-web:${BEAVER_VERSION:-latest}`（Caddy + 编译好的前端 + Caddyfile） |

- 推送到 `main` 后，CI 会构建并发布 `latest` 和以完整提交 sha 命名的标签；推送 `v*` 标签时另发布版本号标签。
- `BEAVER_VERSION` 不设时用 `latest`；CI 自动部署时会传入本次提交的 sha，保证部署的正是刚构建的那一版。
- 想用 fork 的镜像：在 `.env` 里设 `BEAVER_IMAGE=ghcr.io/<你的用户名>/beaver`。
- 想完全从源码构建：`BUILD_FROM_SOURCE=1 ./scripts/deploy.sh`，或 `docker compose build && docker compose up -d`。

---

## 8. 域名与 HTTPS：在前面放一个反向代理

Beaver 自己不处理域名和证书，只在本机 `127.0.0.1:8080` 提供服务。对外访问需要一个反向代理占用 80/443，负责 HTTPS，再转发到这个端口。这样同一台服务器上的其他网站也能共用同一个入口。

1. **DNS**：在域名控制台添加 **A 记录**：主机名（如 `app`）→ 服务器**公网 IP**。
2. **反向代理**：任选其一。要求是转发时带上 **`X-Forwarded-Proto`** 和 **`X-Forwarded-Host`**，Beaver 的 OAuth / MCP 地址靠它们生成；缺了会变成 `http://` 地址，MCP 客户端无法授权。

   **Caddy**（推荐，自动申请和续期证书，默认就会带上这两个头）。可以直接装在系统里，也可以用 Docker 运行（需 `network_mode: host` 才能访问宿主机的 `127.0.0.1:8080`）。Caddyfile：

   ```caddyfile
   app.example.com {
     reverse_proxy 127.0.0.1:8080
   }
   ```

   **Nginx**（证书用 certbot 等自行申请）：

   ```nginx
   server {
     listen 443 ssl;
     server_name app.example.com;
     # ssl_certificate / ssl_certificate_key ...

     location / {
       proxy_pass http://127.0.0.1:8080;
       proxy_set_header Host $host;
       proxy_set_header X-Forwarded-Proto $scheme;
       proxy_set_header X-Forwarded-Host $host;
       proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
     }
   }
   ```

3. 确认 DNS 已生效、80/443 已放行后再启动反向代理，否则证书申请会失败。

---

## 9. 启动服务

```bash
cd /opt/你的仓库名/zero
./scripts/deploy.sh          # 拉取镜像、备份数据库、启动
```

```bash
docker compose ps
docker compose logs -f --tail=100
```

### 查看 Caddy / 后端日志

均在 **`zero` 目录**（与 `docker-compose.yml` 同级）执行。

**Caddy**（静态资源、接口转发、`Caddyfile` 报错多出现在此）：

```bash
docker compose logs -f caddy
docker compose logs --tail=100 caddy
docker compose logs -f -t caddy
```

**Spring Boot 后端**：

```bash
docker compose logs -f backend
```

**Caddy 与后端一起看**（排查 502、接口转发）：

```bash
docker compose logs -f --tail=50 caddy backend
```

也可按 **容器名**（与 `docker-compose.yml` 里 `container_name` 一致）：

```bash
docker logs -f beaver-caddy
docker logs -f beaver-backend
```

---

## 10. 首次访问与管理员

浏览器打开 **`https://你的域名`**，按引导创建**第一个管理员**（仅无用户时会出现）。

---

## 11. 日常更新（发新版）

推荐直接用部署脚本，它会先备份数据库再更新（见第 12 节）：

```bash
ssh user@服务器IP
cd /opt/你的仓库名/zero
GIT_PULL=1 ./scripts/deploy.sh
```

配置了 GitHub Actions 自动部署时（README「部署」一节），推送到 `main` 后会自动执行同样的脚本。

数据在卷 **`zero_data`**（SQLite），不删卷则数据保留。新版本若包含 Flyway 迁移，后端启动时会自动修改表结构。

### 回到某个旧版本

每次提交都有以 sha 命名的镜像，回滚不需要重新编译：

```bash
BEAVER_VERSION=<旧提交的完整 sha> ./scripts/deploy.sh
```

数据库结构如果被新版本的 Flyway 迁移改过，旧版本可能无法读取，回滚前先确认 `backend/src/main/resources/db/migration/` 在这两个版本之间有没有新增文件；有的话用 §12 的备份一起恢复。

### 只更新前端（不重启后端）

```bash
docker compose pull caddy
docker compose up -d caddy
```

- 只替换 Web 容器，不会重建后端容器，也不会动当前数据库

### 只更新后端（保留当前数据库）

适用场景：只改了 `backend/`，希望保留当前线上数据。

```bash
docker compose pull backend
docker compose up -d backend
```

说明：

- 这组命令会更新 `backend` 容器
- `zero_data` 卷会继续挂载，所以不会删除当前 `zero.db`
- `caddy` 不需要重建

重要说明：

- “保留当前数据库”指的是：不删库、不重置卷、不替换现有 `zero.db`
- 但如果新版本后端包含新的 Flyway migration，后端启动时仍会自动执行迁移并修改数据库结构
- 如果你这次明确要求“连表结构都不要动”，先检查 `backend/src/main/resources/db/migration/` 是否新增了 SQL 文件；有新增时，先备份数据库再发版

---

## 12. 数据备份（建议）

数据库在容器内 `/data/zero.db`，对应卷 **`zero_data`**。

`./scripts/deploy.sh`（包括 CI 自动部署）在启动新版本前会**自动备份**：新镜像拉取完成后短暂停止后端，把数据库复制到项目根目录的 **`backups/zero-<时间>.db`**，校验通过后再启动新版本，默认保留最近 10 份。备份失败会中止部署并重新拉起原后端。

- 调整保留份数：`BACKUP_KEEP=20 ./scripts/deploy.sh`
- 跳过备份：`SKIP_DB_BACKUP=1 ./scripts/deploy.sh`

从备份恢复：

```bash
docker compose stop backend
docker cp backups/zero-20260101-120000.db "$(docker compose ps -a -q backend)":/data/zero.db
docker compose start backend
```

`backups/` 与服务器在同一台机器上，只防误操作和升级失败；需要防磁盘损坏时，请再定期把它同步到其他地方。

---

## 13. 常见问题

| 现象 | 排查 |
|------|------|
| **打不开 / 白屏 / 一直转圈** | 先在服务器上执行 `curl -I http://127.0.0.1:8080/`：有响应说明 Beaver 本身正常，问题在反向代理、DNS 或防火墙；没有响应看 `docker compose ps` 和 `docker compose logs`。镜像拉取失败（`docker compose pull` 报错）时，检查服务器能否访问 `ghcr.io`。在 **`zero` 目录**执行 **`./scripts/diagnose.sh`** 可快速汇总上述检查。 |
| 域名打不开，但 `127.0.0.1:8080` 正常 | 反向代理是否在跑、是否指向 `127.0.0.1:8080`；云安全组与本机 **ufw** 是否放行 80/443；DNS 是否指向本机 IP |
| **502**，日志含 `lookup backend` / `127.0.0.11` / `server misbehaving` | **先确认后端在跑**：`docker compose ps`、`docker compose logs backend`。再在 Caddy 容器内测解析：`docker exec beaver-caddy wget -qO- http://backend:8080/healthz`。若 `backend` 解析失败，在同一目录执行 `docker compose down && docker compose up -d`（勿单独用 `docker run` 起 Caddy）。勿在 `/etc/docker/daemon.json` 里把容器 DNS 改成仅公网 DNS，否则会破坏服务名解析。 |
| 登录后 401 / CORS | `FRONTEND_ORIGIN` 是否与浏览器地址完全一致 |
| MCP 授权失败，OAuth 元数据里是 `http://` | 反向代理没有转发 `X-Forwarded-Proto` / `X-Forwarded-Host`（见 §8） |
| HTTPS 证书失败 | 这是反向代理的问题：域名是否解析到本机；**80** 是否对公网开放（Let’s Encrypt HTTP-01） |

---

## 14. 仅 IP、不配域名（测试）

不放反向代理，直接把 Beaver 的端口开到公网或局域网：

```env
FRONTEND_ORIGIN=http://你的公网IP:8080
BEAVER_BIND=0.0.0.0
JWT_ACCESS_SECRET=...
JWT_REFRESH_SECRET=...
```

并在防火墙放行 8080。纯 IP 下 Cookie/安全策略与 HTTPS 域名不同，仅建议联调；生产请用**域名 + HTTPS**。

---

## 15. 命令速查（均在 `zero` 目录）

| 目的 | 命令 |
|------|------|
| 拉取镜像 + 备份 + 启动（推荐） | `./scripts/deploy.sh` |
| 部署指定版本 | `BEAVER_VERSION=<sha> ./scripts/deploy.sh` |
| 从源码构建并启动 | `BUILD_FROM_SOURCE=1 ./scripts/deploy.sh` |
| 启动 | `docker compose up -d` |
| 查看 Caddy 日志 | `docker compose logs -f caddy`（或 `docker logs -f beaver-caddy`） |
| 查看后端日志 | `docker compose logs -f backend`（或 `docker logs -f beaver-backend`） |
| 查看全部服务日志 | `docker compose logs -f` |
| 停止 | `docker compose down` |

更多参数（如 `--tail`、`-t` 时间戳、同时看两个服务）见 **§9「查看 Caddy / 后端日志」**。

若仓库结构不同，只要进入 **`docker-compose.yml` 所在目录**，步骤顺序不变。

---

## 16. 一键脚本（可选）

无法做到「单条命令从零完成全部」：**`.env` 里的密钥必须你自己生成**，**仓库克隆路径**也因人而异。下面脚本是「能自动的部分」尽量自动化。

| 脚本 | 作用 | 典型用法 |
|------|------|----------|
| **`scripts/bootstrap-ubuntu.sh`** | 仅在**全新 Ubuntu 22.04/24.04** 上**一次性**安装 Docker、ufw（以及可选的 Node 22）（需 **root/sudo**） | `sudo bash scripts/bootstrap-ubuntu.sh` |
| **`scripts/deploy.sh`** | 在已有 **`zero/.env`** 的前提下：**拉取镜像 → 备份数据库 → `docker compose up`**；可选先 `git pull`，或 `BUILD_FROM_SOURCE=1` 从源码构建 | `cd /path/to/zero && ./scripts/deploy.sh` 或 `GIT_PULL=1 ./scripts/deploy.sh` |
| **`scripts/diagnose.sh`** | **排查「打不开 / 白屏 / 502」**：检查镜像版本、容器状态、Caddy/后端日志、caddy→backend 连通性 | `cd /path/to/zero && ./scripts/diagnose.sh` |

**推荐流程：**

1. （仅新机）执行 **`bootstrap-ubuntu.sh`**，然后 `usermod -aG docker <用户>`，**重新登录 SSH**。  
2. **`git clone`** 仓库并进入 **`zero`**，手动创建 **`.env`**（§6）。  
3. 以后每次发版：在 **`zero` 目录**执行 **`./scripts/deploy.sh`**；需要拉代码时 **`GIT_PULL=1 ./scripts/deploy.sh`**。

脚本需在 **`zero` 目录**下执行（或 `bash /绝对路径/zero/scripts/deploy.sh`，脚本会自动定位到 `zero` 根目录）。

---

## 附录 A：Rocky Linux / AlmaLinux / CentOS Stream（dnf）

**Docker（示例，以 Docker 官方文档为准）：**

```bash
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable docker --now
sudo usermod -aG docker "$USER"
```

**Node 22（NodeSource RPM）：**

```bash
curl -fsSL https://rpm.nodesource.com/setup_22.x | sudo bash -
sudo yum install -y nodejs
# 或使用：sudo dnf install -y nodejs
```

**防火墙（firewalld）：**

```bash
sudo systemctl enable firewalld --now
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

若启用 **SELinux** 且挂载卷异常，见 [Docker 与 SELinux](https://docs.docker.com/engine/storage/bind-mounts/#configure-the-selinux-label)。

---

## 附录 B：CentOS 7（glibc 2.17）说明

NodeSource **Node 22** 需要 **glibc ≥ 2.28**，CentOS 7 **无法满足**，会出现 `libc.so.6(GLIBC_2.28)` 等依赖错误。

**可选：**

1. **换系统**（推荐）：Ubuntu 24.04 / Rocky 9 等，再按正文或附录 A 操作。  
2. **试 Node 16 RPM**（可能仍失败或无法满足前端依赖）：  
   `curl -fsSL https://rpm.nodesource.com/setup_16.x | sudo bash -` → `yum install -y nodejs`  
3. **nvm 安装 Node 16**（用户目录）。  
4. **不需要 Node**：服务器只装 Docker，直接拉取 CI 构建好的镜像即可。

---

## 用户选项与迁移（V2）

后端 Flyway **`V2__user_option_items.sql`** 会创建 `user_option_items` 表，用于每用户可配置的账户类型、归属与大事记分类。首次访问「设置」或调用相关 API 时，若该用户尚无记录，会自动写入与历史硬编码一致的默认种子。部署新版本时请正常启动后端以执行迁移；无需手工 SQL。

---

以上步骤与 **`zero/README.md`** 中 Docker 部署一节互相引用；以本文分步为准。
