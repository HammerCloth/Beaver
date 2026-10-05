# MCP 与 AI 客户端授权

后端提供远程 MCP endpoint：

```text
POST /mcp
```

MCP 访问使用标准 OAuth 授权码 + PKCE 流程。AI Agent 第一次连接时会通过 discovery 找到授权入口，打开浏览器登录 Beaver 已有账号，登录成功后 Agent 使用 authorization code 换取 access token 和 refresh token。MCP 不开放新用户注册；没有现有账号会授权失败。

相关端点：

```text
GET  /.well-known/oauth-protected-resource
GET  /.well-known/oauth-authorization-server
POST /oauth/register
GET  /oauth/authorize
POST /oauth/authorize
POST /oauth/token
POST /oauth/revoke
POST /mcp
```

MCP 当前提供 4 个 tool：

```text
get_analysis_guide
list_snapshots
get_asset_snapshot
get_major_financial_events
```

说明：

- `get_analysis_guide` 告诉调用方 AI 当前系统是基于资产快照的数据模型，以及数据限制。
- `list_snapshots` 列出当前用户已有快照。
- `get_asset_snapshot` 获取最新、指定 ID 或指定日期的资产快照。
- `get_major_financial_events` 按关联快照日期查询大事记；不按 `events.created_at` 查询。
- AI 客户端拿到的是专用 `mcp_access` token，只能访问 `/mcp`，不能访问普通 `/api/**`。

网页端新增“AI 客户端”页面，用于查看和撤销已授权的 AI Agent。后端管理 API：

```text
GET    /api/v1/oauth/authorizations                     # 按客户端汇总的授权列表
DELETE /api/v1/oauth/authorizations/clients/{clientId}  # 撤销某个客户端的全部令牌
DELETE /api/v1/oauth/authorizations                     # 撤销全部
```

刷新令牌每次刷新都会轮换（旧令牌作废、生成新令牌），列表接口会把同一客户端的多条令牌合并成一条。

MCP 授权功能对应 Flyway 迁移 `V3__oauth_mcp.sql`，会创建：

```text
oauth_clients
oauth_authorization_codes
oauth_refresh_tokens
```

线上发布前建议先备份数据库。

## 安装到 AI 客户端

下面示例假设线上域名是：

```text
https://app.example.com
```

实际使用时替换为你访问 Beaver 的域名（与 `FRONTEND_ORIGIN` 一致）。远程 MCP URL 固定为：

```text
https://app.example.com/mcp
```

### Codex

Codex CLI 和 IDE extension 共享 `config.toml` 中的 MCP 配置。可以编辑用户级配置：

```bash
mkdir -p ~/.codex
nano ~/.codex/config.toml
```

加入：

```toml
[mcp_servers.beaver]
url = "https://app.example.com/mcp"
```

然后执行 OAuth 登录：

```bash
codex mcp login beaver
```

也可以在 Codex TUI 中输入：

```text
/mcp
```

查看 MCP server 状态并触发登录。Codex 会打开浏览器，登录 Beaver 已有账号后保存 OAuth token。官方配置说明见 [OpenAI Codex MCP docs](https://developers.openai.com/codex/mcp)。

### Claude Code

添加 HTTP MCP server：

```bash
claude mcp add --transport http beaver https://app.example.com/mcp
```

然后执行 OAuth 登录：

```bash
claude mcp login beaver
```

或者进入 Claude Code 交互会话后输入：

```text
/mcp
```

在 MCP 面板里选择 `beaver` 并完成浏览器登录。服务器返回 `401` 时会通过 `WWW-Authenticate` 指向 OAuth discovery，Claude Code 会按 OAuth 2.0 流程完成授权并自动刷新 token。远程 SSH 环境下可使用：

```bash
claude mcp login beaver --no-browser
```

它会打印授权 URL，浏览器登录后把回调 URL 粘回终端。官方说明见 [Claude Code MCP docs](https://docs.anthropic.com/en/docs/claude-code/mcp)。

### Claude Desktop

如果你的 Claude Desktop 版本支持远程 HTTP MCP server，可以使用与 Claude Code 相同的远程地址：

```text
https://app.example.com/mcp
```

在客户端 MCP 设置里新增 HTTP server，名称建议用 `beaver`。首次调用或在 MCP 管理界面中触发认证时，客户端会打开浏览器走 OAuth 登录。不同 Claude Desktop 版本的 MCP 配置入口可能不同；如果界面没有远程 HTTP MCP 配置项，优先使用 Claude Code 的 `claude mcp add --transport http ...` 方式。

### 验证

授权成功后，可以让客户端询问：

```text
请调用 Beaver MCP，列出当前可用快照，并说明这个资产系统的数据模型限制。
```

如果客户端显示未授权或没有 tools：

- 确认 `https://你的域名/mcp` 能访问到后端，而不是前端页面。
- 确认 `https://你的域名/.well-known/oauth-protected-resource` 返回 JSON。
- 确认线上 Caddyfile 已包含 `/mcp`、`/oauth/*` 和 `/.well-known/*` 反代。
- 在网页端“AI 客户端”页面撤销旧授权后，重新执行客户端登录命令。
