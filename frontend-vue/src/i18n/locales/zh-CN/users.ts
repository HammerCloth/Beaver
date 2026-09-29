export default {
  title: '用户管理',
  description: '创建成员账号、重置密码。新用户首次登录时需要修改密码。',
  create: '新建用户',
  submit: '创建',
  allUsers: '全部用户',
  count: '{n} 人',
  createFailed: '创建失败',
  failed: '失败',
  passwordReset: '密码已重置',
  resetPassword: '重置密码',
  newPasswordPlaceholder: '新密码（至少 8 位）',
  columns: {
    user: '用户',
    role: '角色',
    passwordStatus: '密码状态',
  },
  passwordStatus: {
    mustChange: '待首次改密',
    normal: '正常',
  },
  fields: {
    username: '用户名',
    password: '密码',
    admin: '管理员',
  },
}
