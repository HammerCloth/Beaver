export default {
  title: 'AI clients',
  description: 'AI agents authorized to access your data via MCP. Revoke clients you no longer use.',
  revokeAll: 'Revoke all',
  authorizedClients: 'Authorized clients',
  count: '{n}',
  showInactive: 'Show inactive ({n})',
  defaultName: 'AI client',
  thisClient: 'this client',
  neverUsed: 'Never used',
  status: {
    active: 'Active',
    inactive: 'Inactive',
  },
  columns: {
    client: 'Client',
    scope: 'Scope',
    authorizedAt: 'First authorized',
    lastUsedAt: 'Last used',
    expiresAt: 'Expires',
    status: 'Status',
  },
  meta: {
    lastUsed: 'Last used {time}',
    authorized: 'First authorized {time}',
    expires: 'Expires {time}',
  },
  revokeDialog: {
    title: 'Revoke AI client',
    content: 'After revoking, {name} must be authorized again to access MCP.',
  },
  revokeAllDialog: {
    title: 'Revoke all AI clients',
    content: 'All AI clients will need to be authorized again to access MCP.',
  },
  revoked: 'Revoked',
  allRevoked: 'All clients revoked',
}
