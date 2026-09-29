/** 本地日期 YYYY-MM-DD（toISOString 是 UTC，东八区凌晨会差一天） */
export function localToday() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
