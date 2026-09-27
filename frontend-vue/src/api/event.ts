import http from './http'

export interface EventStatsResponse {
  year: number
  byCategory: Record<string, number>
  grandTotal: number
  countByCategory: Record<string, number>
}

export async function eventStats(year?: number) {
  const { data } = await http.get<EventStatsResponse>('/api/v1/events/stats', { params: { year } })
  return data
}

export interface EventItem {
  id: string
  snapshotId: string
  date: string
  category: string
  description: string
  /** 负数为支出，正数为收入 */
  amount: number
}

export async function listEvents(year?: number) {
  const { data } = await http.get<{ year: number; events: EventItem[] }>('/api/v1/events', { params: { year } })
  return data.events
}
