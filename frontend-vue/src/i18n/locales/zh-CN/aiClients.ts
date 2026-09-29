export default {
  title: 'AI 客户端',
  description: '通过 MCP 授权访问你数据的 AI Agent。不再使用的客户端请及时撤销。',
  revokeAll: '全部撤销',
  authorizedClients: '已授权客户端',
  count: '{n} 个',
  showInactive: '显示已失效（{n}）',
  defaultName: 'AI 客户端',
  thisClient: '该客户端',
  neverUsed: '尚未使用',
  status: {
    active: '有效',
    inactive: '已失效',
  },
  columns: {
    client: '客户端',
    scope: '权限',
    authorizedAt: '首次授权',
    lastUsedAt: '最后使用',
    expiresAt: '过期时间',
    status: '状态',
  },
  meta: {
    lastUsed: '最后使用 {time}',
    authorized: '首次授权 {time}',
    expires: '{time} 过期',
  },
  revokeDialog: {
    title: '撤销 AI 客户端',
    content: '撤销后 {name} 需要重新授权才能访问 MCP。',
  },
  revokeAllDialog: {
    title: '撤销全部 AI 客户端',
    content: '所有 AI 客户端都需要重新授权后才能访问 MCP。',
  },
  revoked: '已撤销',
  allRevoked: '已全部撤销',
}
