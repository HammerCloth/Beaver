#!/usr/bin/env bash
# 在项目根目录排查「打不开 / 白屏 / 502」——不修改系统，只打印检查结果
# 用法: cd /path/to/Beaver && ./scripts/diagnose.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "========== 1. 前端静态文件（最常见：git pull 后未构建，此处为空）=========="
if [[ -f frontend-vue/dist/index.html ]]; then
  echo "[OK] frontend-vue/dist/index.html 存在"
  ls -la frontend-vue/dist | head -8
else
  echo "[失败] 没有 frontend-vue/dist/index.html"
  echo "       修复: cd frontend-vue && npm ci && npm run build && cd .."
  echo "       然后: docker compose up -d --build"
fi

echo ""
echo "========== 2. .env（FRONTEND_ORIGIN 须与浏览器地址栏一致，含 https://）=========="
PORT=8080
BIND=127.0.0.1
if [[ -f .env ]]; then
  grep -E '^(FRONTEND_ORIGIN|BEAVER_PORT|BEAVER_BIND)=' .env 2>/dev/null || true
  RAW_PORT=$(grep -E '^BEAVER_PORT=' .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r" ' || true)
  RAW_BIND=$(grep -E '^BEAVER_BIND=' .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '\r" ' || true)
  [[ -n "${RAW_PORT}" ]] && PORT="${RAW_PORT}"
  [[ -n "${RAW_BIND}" ]] && BIND="${RAW_BIND}"
  if grep -qE '^CADDY_SITE=' .env 2>/dev/null; then
    echo "[提示] .env 里还有 CADDY_SITE：已不再使用，域名与 HTTPS 交给你前面的反向代理"
  fi
else
  echo "[警告] 未找到 .env"
fi
echo "       Beaver 监听: ${BIND}:${PORT}"

echo ""
echo "========== 3. 容器状态 =========="
docker compose ps 2>/dev/null || echo "（无法执行 docker compose，请在项目根目录、有 docker 权限的机器上运行）"

echo ""
echo "========== 4. Caddy / 后端 最近日志 =========="
docker compose logs caddy --tail 25 2>/dev/null || true
echo "---"
docker compose logs backend --tail 15 2>/dev/null || true

echo ""
echo "========== 5. Caddy 容器能否访问后端（502 时常与此有关）========="
if docker compose exec -T caddy wget -qO- http://backend:8080/healthz 2>/dev/null; then
  echo "(caddy -> backend:8080 正常)"
else
  echo "[失败] 在 caddy 容器内无法访问 http://backend:8080/healthz"
  echo "       检查: docker compose ps 里 backend 是否为 running；勿单独 docker run 脱离 compose 网络"
fi

echo ""
echo "========== 6. 本机对 Beaver 的 HTTP 探测 =========="
if curl -sI --connect-timeout 3 "http://127.0.0.1:${PORT}/" | head -6; then
  echo "(127.0.0.1:${PORT} 有响应：Beaver 本身正常；若域名打不开，问题在前面的反向代理、DNS 或防火墙)"
else
  echo "（127.0.0.1:${PORT} 无响应：检查容器是否 up、端口映射与 BEAVER_PORT）"
fi

echo ""
echo "完成。优先对照 §1：若 dist 缺失，网站会空白或无法加载。"
