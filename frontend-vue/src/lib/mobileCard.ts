import { h, type VNodeChild } from 'vue'
import type { DataTableColumns } from 'naive-ui'

export interface MobileCardParts {
  /** 左上：主标题（名称、对象、分类等） */
  title: VNodeChild
  /** 右上：金额或主要数值 */
  value?: VNodeChild
  /** 第二行：日期、标签等次要信息，空值会被跳过 */
  meta?: VNodeChild[]
  /** 可选的整行附加内容，如还款进度条 */
  extra?: VNodeChild
  /** 底部操作按钮 */
  actions?: VNodeChild[]
}

export function mobileCard(parts: MobileCardParts) {
  const meta = (parts.meta ?? []).filter((m) => m !== null && m !== undefined && m !== false && m !== '')
  return h('div', { class: 'm-card' }, [
    h('div', { class: 'm-card__top' }, [
      h('div', { class: 'm-card__title' }, [parts.title]),
      parts.value != null ? h('div', { class: 'm-card__value' }, [parts.value]) : null,
    ]),
    meta.length ? h('div', { class: 'm-card__meta' }, meta.map((m) => h('span', null, [m]))) : null,
    parts.extra ?? null,
    parts.actions?.length ? h('div', { class: 'm-card__actions' }, parts.actions) : null,
  ])
}

/** 手机端：把整行渲染成一张卡片，配合 `.data-table--cards` 隐藏表头 */
export function mobileCardColumns<T>(render: (row: T) => MobileCardParts): DataTableColumns<T> {
  return [{ title: '', key: '__card', render: (row) => mobileCard(render(row)) }]
}
