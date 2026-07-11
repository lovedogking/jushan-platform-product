import dayjs from 'dayjs'

/** 格式化日期时间 */
export function formatDate(date?: string | number | Date, template = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!date) return '-'
  return dayjs(date).format(template)
}

/** 格式化金额（分→元） */
export function formatAmount(amountInCents?: number): string {
  if (amountInCents === undefined || amountInCents === null) return '-'
  return (amountInCents / 100).toFixed(2)
}

/** 格式化停车时长（分钟） */
export function formatDuration(minutes?: number): string {
  if (!minutes || minutes <= 0) return '-'
  const d = Math.floor(minutes / 1440)
  const h = Math.floor((minutes % 1440) / 60)
  const m = minutes % 60
  const parts: string[] = []
  if (d > 0) parts.push(`${d}天`)
  if (h > 0) parts.push(`${h}小时`)
  if (m > 0) parts.push(`${m}分钟`)
  return parts.join('') || '-'
}

/** 防抖 */
export function debounce<T extends (...args: any[]) => any>(fn: T, delay: number): T {
  let timer: ReturnType<typeof setTimeout> | null = null
  return ((...args: any[]) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }) as T
}

/** 节流 */
export function throttle<T extends (...args: any[]) => any>(fn: T, delay: number): T {
  let last = 0
  return ((...args: any[]) => {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn(...args)
    }
  }) as T
}
