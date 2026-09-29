export default {
  title: '设置',
  description: '维护各类选项，所有页面的标签、筛选与图表颜色都以这里为准。',
  reload: '重新加载',
  resetDefaults: '恢复默认',
  dims: {
    accountType: {
      label: '账户类型',
      save: '保存账户类型',
      hint: '决定账户如何归类；负债类（key 为 credit）按负值计入净资产。',
    },
    accountOwner: {
      label: '账户归属',
      save: '保存账户归属',
      hint: '账户属于谁，用于按人查看资产构成。',
    },
    eventCategory: {
      label: '大事记分类',
      save: '保存大事记分类',
      hint: '快照里记录收支事件时可选的分类。',
    },
  },
  columns: {
    key: 'Key',
    label: '显示名称',
    sortOrder: '排序',
    enabled: '启用',
  },
  keyPlaceholder: '英文 key',
  labelPlaceholder: '显示名称',
  remove: '移除',
  addOption: '+ 新增选项',
  saveFailed: '保存失败（key 重复或仍有引用等）',
  resetDialog: {
    title: '恢复默认',
    content: '将覆盖当前所有自定义选项，确定继续？',
    confirm: '恢复',
  },
  resetDone: '已恢复默认',
}
