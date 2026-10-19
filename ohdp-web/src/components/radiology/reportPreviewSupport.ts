export function clampReportPage(index: number, count: number): number {
  if (!Number.isFinite(index) || count <= 0) return 0
  return Math.min(Math.max(Math.trunc(index), 0), count - 1)
}

export function reportPageLabel(index: number): string {
  if (index <= 0) return '封面'
  if (index === 1) return '封二'
  return `正文第${index - 1}页`
}
