# syntax=docker/dockerfile:1
# Beaver 的 Web 镜像：Caddy + 编译好的前端 + Caddyfile。构建上下文是仓库根目录（需要 Caddyfile 和 frontend-vue）。
# 前端编译产物与平台无关，编译阶段固定在构建机的原生架构上跑
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY frontend-vue/package.json frontend-vue/package-lock.json ./
RUN npm ci
COPY frontend-vue/ ./
RUN npm run build

FROM caddy:2.8-alpine
LABEL dev.beaver.component="web"
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv/frontend
