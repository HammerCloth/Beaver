export default {
  title: 'Settings',
  description: 'Manage options here. Labels, filters and chart colors on every page follow these settings.',
  reload: 'Reload',
  resetDefaults: 'Restore defaults',
  dims: {
    accountType: {
      label: 'Account types',
      save: 'Save account types',
      hint: 'Determines how accounts are grouped. Liabilities (key "credit") count as negative toward net worth.',
    },
    accountOwner: {
      label: 'Account owners',
      save: 'Save account owners',
      hint: 'Who an account belongs to, used to view asset breakdown by person.',
    },
    eventCategory: {
      label: 'Event categories',
      save: 'Save event categories',
      hint: 'Categories available when recording income and expense events in a snapshot.',
    },
  },
  columns: {
    key: 'Key',
    label: 'Display name',
    sortOrder: 'Order',
    enabled: 'Enabled',
  },
  keyPlaceholder: 'Key (English)',
  labelPlaceholder: 'Display name',
  remove: 'Remove',
  addOption: '+ Add option',
  saveFailed: 'Failed to save (duplicate key or option still in use)',
  resetDialog: {
    title: 'Restore defaults',
    content: 'This will overwrite all your custom options. Continue?',
    confirm: 'Restore',
  },
  resetDone: 'Defaults restored',
}
