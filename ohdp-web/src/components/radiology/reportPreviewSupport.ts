export function clampReportPage(index: number, count: number): number {
  if (!Number.isFinite(index) || count <= 0) return 0
  return Math.min(Math.max(Math.trunc(index), 0), count - 1)
}

export function reportPageLabel(index: number): string {
  if (index <= 0) return '封面'
  if (index === 1) return '封二'
  return `正文第${index - 1}页`
}

export const MIN_REPORT_ZOOM = 50
export const MAX_REPORT_ZOOM = 300
export const REPORT_ZOOM_STEP = 25

export function clampReportZoom(percent: number): number {
  if (!Number.isFinite(percent)) return 100
  return Math.min(MAX_REPORT_ZOOM, Math.max(MIN_REPORT_ZOOM, percent))
}
