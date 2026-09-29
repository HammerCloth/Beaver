import http from './http'

/** 按客户端汇总的授权（刷新令牌轮换产生的多条记录已在后端合并） */
export interface OAuthClientAuthorization {
  clientId: string
  clientName: string
  scope: string
  authorizedAt: string | null
  lastUsedAt: string | null
  expiresAt: string | null
  active: boolean
}

export async function listClientAuthorizations() {
  const { data } = await http.get<{ clients: OAuthClientAuthorization[] }>('/api/v1/oauth/authorizations')
  return data.clients
}

/** 撤销某个客户端下的全部令牌 */
export async function revokeClientAuthorization(clientId: string) {
  await http.delete(`/api/v1/oauth/authorizations/clients/${encodeURIComponent(clientId)}`)
}

export async function revokeAllAuthorizations() {
  await http.delete('/api/v1/oauth/authorizations')
}
