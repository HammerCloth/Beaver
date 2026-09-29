#!/usr/bin/env bash
# 在项目根目录下完成：可选 git pull → 构建前端与镜像 → 备份数据库 → docker compose up
# 用法: ./scripts/deploy.sh
#       GIT_PULL=1 ./scripts/deploy.sh        # 先 git pull 再部署
#       SKIP_DB_BACKUP=1 ./scripts/deploy.sh  # 跳过数据库备份
#       BACKUP_KEEP=20 ./scripts/deploy.sh    # 保留最近 20 份备份（默认 10）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BACKUP_DIR="$ROOT/backups"
BACKUP_KEEP="${BACKUP_KEEP:-10}"
# 与 docker-compose.yml 中 backend 的 DATABASE_PATH 保持一致
DB_PATH_IN_CONTAINER="/data/zero.db"

if [[ ! -f .env ]]; then
  echo "错误：未找到 $ROOT/.env"
  echo "请先按 docs/DEPLOYMENT.md 创建并填写 FRONTEND_ORIGIN、JWT_ACCESS_SECRET、JWT_REFRESH_SECRET、CADDY_SITE"
  exit 1
fi

# 与后端 JwtSecretGuard 一致：缺失、仍为公开默认值或短于 32 字节时，在构建前就停下
check_secret() {
  local name="$1" value
  # 兼容 `export NAME=...`、首尾引号；没有这一行时 value 为空
  value="$( (grep -E "^[[:space:]]*(export[[:space:]]+)?${name}=" .env || true) | tail -n 1 | cut -d= -f2- \
    | sed -e 's/[[:space:]]*$//' -e 's/^["'"'"']//' -e 's/["'"'"']$//')"
  case "$value" in
    "" | dev-access-secret-change-in-production | dev-refresh-secret-change-in-production | \
      change-this-access-secret | change-this-refresh-secret)
      echo "错误：.env 中的 ${name} 未设置或仍是公开默认值，请改为随机长字符串（例如 openssl rand -base64 48）"
      exit 1
      ;;
  esac
  if (( ${#value} < 32 )); then
    echo "错误：.env 中的 ${name} 太短，至少需要 32 个字符"
    exit 1
  fi
}
check_secret JWT_ACCESS_SECRET
check_secret JWT_REFRESH_SECRET

if [[ ! -d frontend-vue ]]; then
  echo "错误：未找到 frontend-vue 目录（应在项目根目录下执行本脚本）"
  exit 1
fi

if [[ "${GIT_PULL:-0}" == "1" ]]; then
  echo "==> git pull 中..."
  git pull
fi

echo "==> 构建前端 (npm ci && npm run build)..."
(cd frontend-vue && npm ci && npm run build)

# 先构建镜像，旧后端继续提供服务，把停机时间压缩到备份 + 重启
echo "==> 构建 Docker 镜像..."
docker compose build

# 备份时会先停掉后端；若之后任一步失败退出，把原后端拉起来，避免服务一直停着
backend_stopped=0
restore_backend() {
  if [[ "$backend_stopped" == "1" ]]; then
    echo "!! 部署中断，重新启动原后端容器..."
    docker compose start backend || true
  fi
}
trap restore_backend EXIT

backup_db() {
  if [[ "${SKIP_DB_BACKUP:-0}" == "1" ]]; then
    echo "==> 已设置 SKIP_DB_BACKUP=1，跳过数据库备份"
    return
  fi

  local cid
  cid="$(docker compose ps -a -q backend)"
  if [[ -z "$cid" ]]; then
    echo "==> 未找到已有的 backend 容器（首次部署），跳过数据库备份"
    return
  fi

  # 停止后再复制，确保 SQLite 已落盘、拿到的是一致的文件
  echo "==> 停止后端以备份数据库..."
  docker compose stop backend
  backend_stopped=1

  mkdir -p "$BACKUP_DIR"
  local file="$BACKUP_DIR/zero-$(date +%Y%m%d-%H%M%S).db"
  if ! docker cp "$cid:$DB_PATH_IN_CONTAINER" "$file" 2>/dev/null; then
    rm -f "$file"
    echo "==> 容器内还没有数据库文件 $DB_PATH_IN_CONTAINER，跳过备份"
    return
  fi

  if [[ "$(head -c 15 "$file")" != "SQLite format 3" ]]; then
    rm -f "$file"
    echo "错误：从容器复制出的 $DB_PATH_IN_CONTAINER 不是有效的 SQLite 数据库，已中止部署"
    return 1
  fi
  echo "==> 已备份数据库：$file ($(du -h "$file" | cut -f1))"

  # 只保留最近 BACKUP_KEEP 份
  local old
  old="$(ls -1t "$BACKUP_DIR"/zero-*.db 2>/dev/null | tail -n +"$((BACKUP_KEEP + 1))")"
  if [[ -n "$old" ]]; then
    echo "$old" | xargs rm -f
    echo "==> 已清理旧备份，保留最近 $BACKUP_KEEP 份"
  fi
}

backup_db

echo "==> 启动 / 更新 Docker 服务..."
docker compose up -d
backend_stopped=0

echo "==> 完成。查看: docker compose ps"
echo "    日志: docker compose logs -f --tail=50"
echo "    备份: ls -lt $BACKUP_DIR"
