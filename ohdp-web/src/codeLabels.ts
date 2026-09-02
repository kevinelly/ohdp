const CODE_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  DATA_FILLED: '已录入',
  DATA_ENTERED: '已录入',
  JUDGED: '已判定',
  CALCULATED: '已计算',
  PENDING_REVIEW: '待审核',
  APPROVED: '已批准',
  REPORTED: '已出报告',
  REPORT_READY: '已生成报告',
  PENDING: '待领取',
  IN_PROGRESS: '检测中',
  COMPLETED: '待复核',
  REVIEWED: '已复核',
  REJECTED: '已退回',
  GENERATED: '已生成',
  CHEMICAL: '化学因素',
  DUST: '粉尘',
  PHYSICAL: '物理因素',
  BIOLOGICAL: '生物因素',
  RADIATION: '放射因素',
  GENERAL: '一般因素',
  OTHER: '其他',
  NORMAL: '正常',
  MAINTENANCE: '维修中',
  SCRAPPED: '已报废',
  LAB_INSTRUMENT: '实验仪器',
  SAMPLER: '采样仪器',
  NATIONAL: '国家级',
  DEPARTMENT: '部门规章',
  INDUSTRY: '行业级',
  PROVINCIAL: '省级',
  LOCAL: '地方级',
  ACTIVE: '启用',
  INACTIVE: '禁用',
  CREATE: '生成报告',
  OVERWRITE: '重新生成报告',
  DOWNLOAD: '下载报告',
  SUCCESS: '成功',
  FAIL: '失败',
  FAILURE: '失败',
  QUALIFIED: '合格',
  UNQUALIFIED: '不合格',
  NOT_APPLICABLE: '不适用',
  NOT_JUDGED: '未判定',
  MENU: '菜单',
  BUTTON: '按钮',
  API: '接口',
  TASK_ORIGIN: '任务由来',
  EVALUATION_BASIS: '评价依据',
  NOTICE: '通知',
  ANNOUNCEMENT: '公告',
  PC_TWA: '时间加权平均容许浓度',
  PC_STEL: '短时间接触容许浓度',
  MAC: '最高容许浓度',
  PE: '峰接触浓度',
  LEX8H: '8 小时等效声级',
  FIXED: '定点采样',
  PERSONAL: '个体采样',
  LOGIN: '登录',
  LOGOUT: '退出登录',
  AUTH: '认证管理',
  USER: '用户管理',
  ROLE: '角色管理',
  PERMISSION: '权限管理',
  SYSTEM: '系统管理',
  PROJECT: '项目管理',
  REPORT: '报告管理',
  BASIC_DATA: '基础数据',
  RADIOLOGY_QC: '放射诊疗质量控制',
}

export function codeLabel(value?: string | null, fallback = '未设置') {
  const raw = value?.trim()
  if (!raw) return fallback
  if (/[一-鿿]/.test(raw)) return raw
  return CODE_LABELS[raw.toUpperCase()] || `未知（${raw}）`
}

export function regionLevelLabel(value?: number | null) {
  if (value === 1) return '省级'
  if (value === 2) return '市级'
  if (value === 3) return '区县级'
  return value == null ? '未设置' : `未知（${value}）`
}

export function formatFileSize(value?: number | null) {
  if (value == null || Number.isNaN(value)) return '-'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}
