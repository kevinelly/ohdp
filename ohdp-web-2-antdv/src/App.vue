<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'

type ProjectStatus = 'DRAFT' | 'DATA_FILLED' | 'JUDGED' | 'REPORT_READY'

interface LoginResponse {
  token: string
}

interface Project {
  id: number
  projectCode: string
  companyName: string
  companyAddress: string
  detectType: string
  samplingDate?: string
  reportDate?: string
  notes?: string
  status: ProjectStatus
  createdAt?: string
  updatedAt?: string
}

interface Position {
  id: number
  projectId: number
  jobName: string
  shiftSystem?: string
  workingHours?: string
  workerCount?: number
  workLocation?: string
  operationType?: string
  operationContent?: string
  operationDuration?: string
  hazards?: string
  ppe?: string
  protectionFacilities?: string
}

interface ResultItem {
  id: number
  projectId: number
  jobName: string
  factorName: string
  metricType: string
  valueRaw?: string
  valueNum?: number
  unit?: string
  limitValue?: number
  limitUnit?: string
  judgement?: string
}

interface ReportFile {
  id: number
  projectId: number
  fileName: string
  generatedBy?: string
  generatedAt: string
}

interface ReportLog {
  id: number
  projectId: number
  operationType: string
  operatorName?: string
  operationMessage?: string
  operatedAt: string
}

interface ReportPrecheck {
  projectId: number
  passed: boolean
  missingItems: string[]
}

interface ReportPreview {
  project: Project
  positions: Position[]
  results: ResultItem[]
  precheck: ReportPrecheck
}

interface CompanyProfile {
  id: number
  companyName: string
  companyAddress: string
  contactName?: string
  contactPhone?: string
  industryType?: string
  notes?: string
}

interface PositionTemplate {
  id: number
  jobName: string
  shiftSystem?: string
  workingHours?: string
  operationType?: string
  operationDuration?: string
  hazards?: string
  ppe?: string
  protectionFacilities?: string
  notes?: string
}

interface FactorTemplate {
  id: number
  factorName: string
  metricType: string
  defaultUnit?: string
  defaultLimitValue?: string
  defaultLimitUnit?: string
  notes?: string
}

interface DictionaryItem {
  id: number
  dictType: string
  itemCode: string
  itemName: string
  sortNo?: number
  enabled?: boolean
}

interface PageResult<T> {
  content: T[]
  totalElements: number
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
})

const token = ref(localStorage.getItem('ohdp_token') || '')
const loading = ref(false)
const message = ref('')
const activeTab = ref<'project' | 'maintenance'>('project')
const showPreviewPanel = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: '123456',
})

const query = reactive({
  projectCode: '',
  companyName: '',
  status: '' as '' | ProjectStatus,
  page: 0,
  size: 20,
})

const projects = ref<Project[]>([])
const totalProjects = ref(0)
const selectedProject = ref<Project | null>(null)
const positions = ref<Position[]>([])
const results = ref<ResultItem[]>([])
const reports = ref<ReportFile[]>([])
const reportLogs = ref<ReportLog[]>([])
const reportPrecheck = ref<ReportPrecheck | null>(null)
const reportPreview = ref<ReportPreview | null>(null)

const projectForm = reactive({
  companyProfileId: null as number | null,
  companyName: '',
  companyAddress: '',
  detectType: '监测检验',
  samplingDate: today(),
  reportDate: today(),
  notes: '',
})

const positionForm = reactive({
  positionTemplateId: null as number | null,
  jobName: '',
  shiftSystem: '单班制',
  workingHours: '8h/d',
  workerCount: 1,
  workLocation: '',
  operationType: '定岗',
  operationContent: '',
  operationDuration: '8h',
  hazards: '',
  ppe: '',
  protectionFacilities: '',
})

const resultForm = reactive({
  factorTemplateId: null as number | null,
  jobName: '',
  factorName: '',
  metricType: 'TWA',
  valueRaw: '',
  valueNum: '',
  unit: 'mg/m3',
  limitValue: '',
  limitUnit: 'mg/m3',
})

const batchResultText = ref('')
const projectDirty = ref(false)
let syncingProjectForm = false
let autoSaveTimer: number | undefined

const companyProfiles = ref<CompanyProfile[]>([])
const positionTemplates = ref<PositionTemplate[]>([])
const factorTemplates = ref<FactorTemplate[]>([])
const dictionaryItems = ref<DictionaryItem[]>([])
const dictTypeQuery = ref('detect_type')

const companyProfileForm = reactive({
  id: null as number | null,
  companyName: '',
  companyAddress: '',
  contactName: '',
  contactPhone: '',
  industryType: '',
  notes: '',
})

const positionTemplateForm = reactive({
  id: null as number | null,
  jobName: '',
  shiftSystem: '',
  workingHours: '',
  operationType: '',
  operationDuration: '',
  hazards: '',
  ppe: '',
  protectionFacilities: '',
  notes: '',
})

const factorTemplateForm = reactive({
  id: null as number | null,
  factorName: '',
  metricType: 'TWA',
  defaultUnit: '',
  defaultLimitValue: '',
  defaultLimitUnit: '',
  notes: '',
})

const dictionaryForm = reactive({
  id: null as number | null,
  dictType: 'detect_type',
  itemCode: '',
  itemName: '',
  sortNo: 0,
  enabled: true,
})

const statusText = computed<Record<ProjectStatus, string>>(() => ({
  DRAFT: '草稿',
  DATA_FILLED: '已录入',
  JUDGED: '已判定',
  REPORT_READY: '已生成报告',
}))

const isLoggedIn = computed(() => Boolean(token.value))

api.interceptors.request.use((config) => {
  if (token.value) {
    config.headers.Authorization = `Bearer ${token.value}`
  }
  return config
})

watch(
  () => ({ ...projectForm }),
  () => {
    if (selectedProject.value && !syncingProjectForm) {
      projectDirty.value = true
    }
  },
  { deep: true }
)

function today() {
  return new Date().toISOString().slice(0, 10)
}

function setMessage(text: string) {
  message.value = text
  window.setTimeout(() => {
    if (message.value === text) {
      message.value = ''
    }
  }, 3000)
}

function resetProjectForm() {
  projectForm.companyProfileId = null
  projectForm.companyName = ''
  projectForm.companyAddress = ''
  projectForm.detectType = '监测检验'
  projectForm.samplingDate = today()
  projectForm.reportDate = today()
  projectForm.notes = ''
}

function fillProjectForm(project: Project) {
  syncingProjectForm = true
  projectForm.companyProfileId = null
  projectForm.companyName = project.companyName || ''
  projectForm.companyAddress = project.companyAddress || ''
  projectForm.detectType = project.detectType || '监测检验'
  projectForm.samplingDate = project.samplingDate || today()
  projectForm.reportDate = project.reportDate || today()
  projectForm.notes = project.notes || ''
  projectDirty.value = false
  window.setTimeout(() => {
    syncingProjectForm = false
  }, 0)
}

function toNumber(value: string): number | null {
  const text = value.trim()
  if (!text) return null
  const num = Number(text)
  return Number.isFinite(num) ? num : null
}

function mapMissingItem(item: string) {
  const map: Record<string, string> = {
    'project.companyName': '受检单位名称',
    'project.companyAddress': '受检单位地址',
    'project.detectType': '检测类别',
    'project.samplingDate': '采样日期',
    'project.reportDate': '报告日期',
    'positions.empty': '至少需要 1 条岗位信息',
    'results.empty': '至少需要 1 条检测结果',
    'results.requiredFields': '检测结果缺少岗位/因素/指标',
  }
  return map[item] || item
}

async function login() {
  loading.value = true
  try {
    const { data } = await api.post<LoginResponse>('/api/auth/login', {
      username: loginForm.username,
      password: loginForm.password,
      clientType: 'web',
    })
    token.value = data.token
    localStorage.setItem('ohdp_token', data.token)
    await initAfterLogin()
    setMessage('登录成功')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function logout() {
  try {
    if (token.value) {
      await api.post('/api/auth/logout')
    }
  } catch {
    // ignore
  }
  token.value = ''
  localStorage.removeItem('ohdp_token')
  projects.value = []
  selectedProject.value = null
  positions.value = []
  results.value = []
  reports.value = []
  reportLogs.value = []
  reportPreview.value = null
  reportPrecheck.value = null
  resetProjectForm()
  setMessage('已退出')
}

async function initAfterLogin() {
  await Promise.all([
    fetchProjects(),
    fetchCompanyProfiles(),
    fetchPositionTemplates(),
    fetchFactorTemplates(),
    fetchDictionaryItems(),
  ])
}

async function fetchProjects() {
  if (!token.value) return
  const { data } = await api.get<PageResult<Project>>('/api/ohdp/projects', {
    params: {
      projectCode: query.projectCode || undefined,
      companyName: query.companyName || undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size,
    },
  })
  projects.value = data.content || []
  totalProjects.value = data.totalElements || 0
  if (selectedProject.value) {
    const current = projects.value.find((item) => item.id === selectedProject.value?.id)
    if (current) {
      selectedProject.value = current
    }
  }
}

async function selectProject(project: Project) {
  selectedProject.value = project
  fillProjectForm(project)
  await Promise.all([
    fetchPositions(project.id),
    fetchResults(project.id),
    fetchReports(project.id),
    fetchReportLogs(project.id),
  ])
}

async function createProject() {
  loading.value = true
  try {
    const payload = {
      companyProfileId: projectForm.companyProfileId,
      companyName: projectForm.companyName,
      companyAddress: projectForm.companyAddress,
      detectType: projectForm.detectType,
      samplingDate: projectForm.samplingDate || null,
      reportDate: projectForm.reportDate || null,
      notes: projectForm.notes || null,
    }
    const { data } = await api.post<Project>('/api/ohdp/projects', payload)
    await fetchProjects()
    await selectProject(data)
    setMessage('项目已创建')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '创建项目失败')
  } finally {
    loading.value = false
  }
}

async function updateProject(silent = false) {
  if (!selectedProject.value) return
  try {
    const payload = {
      companyProfileId: projectForm.companyProfileId,
      companyName: projectForm.companyName,
      companyAddress: projectForm.companyAddress,
      detectType: projectForm.detectType,
      samplingDate: projectForm.samplingDate || null,
      reportDate: projectForm.reportDate || null,
      notes: projectForm.notes || null,
    }
    const { data } = await api.put<Project>(`/api/ohdp/projects/${selectedProject.value.id}`, payload)
    selectedProject.value = data
    projectDirty.value = false
    await fetchProjects()
    if (!silent) {
      setMessage('项目已更新')
    }
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '更新项目失败')
  }
}

async function deleteProject() {
  if (!selectedProject.value) return
  if (!window.confirm('确认删除当前项目吗？')) return
  loading.value = true
  try {
    await api.delete(`/api/ohdp/projects/${selectedProject.value.id}`)
    selectedProject.value = null
    positions.value = []
    results.value = []
    reports.value = []
    reportLogs.value = []
    reportPreview.value = null
    reportPrecheck.value = null
    resetProjectForm()
    await fetchProjects()
    setMessage('项目已删除')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '删除项目失败')
  } finally {
    loading.value = false
  }
}

async function copyProject() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    const { data } = await api.post<Project>(`/api/ohdp/projects/${selectedProject.value.id}/copy`)
    await fetchProjects()
    await selectProject(data)
    setMessage('项目已复制')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '复制项目失败')
  } finally {
    loading.value = false
  }
}

async function fetchPositions(projectId: number) {
  const { data } = await api.get<Position[]>(`/api/ohdp/projects/${projectId}/positions`)
  positions.value = data
}

async function addPosition() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/positions`, {
      ...positionForm,
      workerCount: Number(positionForm.workerCount) || 0,
    })
    await fetchPositions(selectedProject.value.id)
    await fetchProjects()
    setMessage('岗位已添加')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '添加岗位失败')
  } finally {
    loading.value = false
  }
}

async function removePosition(id: number) {
  if (!selectedProject.value) return
  if (!window.confirm('确认删除该岗位吗？')) return
  loading.value = true
  try {
    await api.delete(`/api/ohdp/positions/${id}`)
    await fetchPositions(selectedProject.value.id)
    setMessage('岗位已删除')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '删除岗位失败')
  } finally {
    loading.value = false
  }
}

async function fetchResults(projectId: number) {
  const { data } = await api.get<ResultItem[]>(`/api/ohdp/projects/${projectId}/results`)
  results.value = data
}

async function addResult() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/results`, {
      factorTemplateId: resultForm.factorTemplateId,
      jobName: resultForm.jobName,
      factorName: resultForm.factorName,
      metricType: resultForm.metricType,
      valueRaw: resultForm.valueRaw || null,
      valueNum: resultForm.valueNum === '' ? null : Number(resultForm.valueNum),
      unit: resultForm.unit || null,
      limitValue: resultForm.limitValue === '' ? null : Number(resultForm.limitValue),
      limitUnit: resultForm.limitUnit || null,
    })
    await fetchResults(selectedProject.value.id)
    await fetchProjects()
    setMessage('检测结果已添加')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '添加检测结果失败')
  } finally {
    loading.value = false
  }
}

function parseBatchItems(text: string) {
  const rows = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)

  const items = rows
    .map((line) => {
      const cols = line.split(/\t|,|，|\|/).map((item) => item.trim())
      if (cols.length < 3) return null
      const valueNum = cols[4] ? toNumber(cols[4]) : null
      const limitValue = cols[6] ? toNumber(cols[6]) : null
      return {
        jobName: cols[0],
        factorName: cols[1],
        metricType: cols[2],
        valueRaw: cols[3] || null,
        valueNum,
        unit: cols[5] || null,
        limitValue,
        limitUnit: cols[7] || null,
      }
    })
    .filter((item): item is NonNullable<typeof item> => Boolean(item))

  return items
}

async function addBatchResults() {
  if (!selectedProject.value) return
  const items = parseBatchItems(batchResultText.value)
  if (!items.length) {
    setMessage('批量内容为空或格式不正确')
    return
  }
  loading.value = true
  try {
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/results/batch`, { items })
    batchResultText.value = ''
    await fetchResults(selectedProject.value.id)
    await fetchProjects()
    setMessage(`批量导入成功：${items.length} 条`)
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '批量导入失败')
  } finally {
    loading.value = false
  }
}

async function removeResult(id: number) {
  if (!selectedProject.value) return
  if (!window.confirm('确认删除该检测结果吗？')) return
  loading.value = true
  try {
    await api.delete(`/api/ohdp/results/${id}`)
    await fetchResults(selectedProject.value.id)
    setMessage('结果已删除')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '删除结果失败')
  } finally {
    loading.value = false
  }
}

async function judgeProject() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    const { data } = await api.post(`/api/ohdp/projects/${selectedProject.value.id}/judge`)
    await fetchResults(selectedProject.value.id)
    await fetchProjects()
    setMessage(`自动判定完成：合格 ${data.qualifiedCount}，不合格 ${data.unqualifiedCount}`)
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '自动判定失败')
  } finally {
    loading.value = false
  }
}

async function precheckCurrentProject() {
  if (!selectedProject.value) return
  const { data } = await api.get<ReportPrecheck>(`/api/ohdp/projects/${selectedProject.value.id}/report/precheck`)
  reportPrecheck.value = data
}

async function previewReport() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    const { data } = await api.get<ReportPreview>(`/api/ohdp/projects/${selectedProject.value.id}/report/preview`)
    reportPreview.value = data
    reportPrecheck.value = data.precheck
    showPreviewPanel.value = true
    setMessage('预览数据已刷新')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '预览失败')
  } finally {
    loading.value = false
  }
}

async function fetchReports(projectId: number) {
  const { data } = await api.get<ReportFile[]>(`/api/ohdp/projects/${projectId}/reports`)
  reports.value = data
}

async function fetchReportLogs(projectId: number) {
  const { data } = await api.get<ReportLog[]>(`/api/ohdp/projects/${projectId}/report/logs`)
  reportLogs.value = data
}

async function generateReport() {
  if (!selectedProject.value) return
  loading.value = true
  try {
    await precheckCurrentProject()
    if (reportPrecheck.value && !reportPrecheck.value.passed) {
      setMessage('报告预检未通过，请先补全数据')
      return
    }
    if (reports.value.length > 0) {
      const ok = window.confirm('当前项目已有报告，新生成将覆盖旧报告。是否继续？')
      if (!ok) return
    }
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/report/generate`)
    await Promise.all([
      fetchReports(selectedProject.value.id),
      fetchReportLogs(selectedProject.value.id),
      fetchProjects(),
    ])
    setMessage('报告已生成（覆盖模式）')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告生成失败')
  } finally {
    loading.value = false
  }
}

async function downloadReport(reportId: number, fileName: string) {
  loading.value = true
  try {
    const { data } = await api.get(`/api/ohdp/reports/${reportId}/download`, {
      responseType: 'blob',
    })
    const blobUrl = URL.createObjectURL(new Blob([data]))
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = fileName
    link.click()
    URL.revokeObjectURL(blobUrl)
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '下载失败')
  } finally {
    loading.value = false
  }
}

async function fetchCompanyProfiles() {
  const { data } = await api.get<PageResult<CompanyProfile>>('/api/ohdp/maintenance/company-profiles', {
    params: { page: 0, size: 200 },
  })
  companyProfiles.value = data.content || []
}

async function fetchPositionTemplates() {
  const { data } = await api.get<PageResult<PositionTemplate>>('/api/ohdp/maintenance/position-templates', {
    params: { page: 0, size: 200 },
  })
  positionTemplates.value = data.content || []
}

async function fetchFactorTemplates() {
  const { data } = await api.get<PageResult<FactorTemplate>>('/api/ohdp/maintenance/factor-templates', {
    params: { page: 0, size: 200 },
  })
  factorTemplates.value = data.content || []
}

async function fetchDictionaryItems() {
  const { data } = await api.get<DictionaryItem[]>('/api/ohdp/maintenance/dictionary-items', {
    params: { dictType: dictTypeQuery.value || 'detect_type' },
  })
  dictionaryItems.value = data
}

function applyCompanyProfile(profileId: string) {
  const id = Number(profileId)
  const profile = companyProfiles.value.find((item) => item.id === id)
  if (!profile) return
  projectForm.companyProfileId = profile.id
  projectForm.companyName = profile.companyName
  projectForm.companyAddress = profile.companyAddress
}

function applyPositionTemplate(templateId: string) {
  const id = Number(templateId)
  const template = positionTemplates.value.find((item) => item.id === id)
  if (!template) return
  positionForm.positionTemplateId = template.id
  positionForm.jobName = template.jobName || ''
  positionForm.shiftSystem = template.shiftSystem || positionForm.shiftSystem
  positionForm.workingHours = template.workingHours || positionForm.workingHours
  positionForm.operationType = template.operationType || positionForm.operationType
  positionForm.operationDuration = template.operationDuration || positionForm.operationDuration
  positionForm.hazards = template.hazards || ''
  positionForm.ppe = template.ppe || ''
  positionForm.protectionFacilities = template.protectionFacilities || ''
}

function applyFactorTemplate(templateId: string) {
  const id = Number(templateId)
  const template = factorTemplates.value.find((item) => item.id === id)
  if (!template) return
  resultForm.factorTemplateId = template.id
  resultForm.factorName = template.factorName || ''
  resultForm.metricType = template.metricType || resultForm.metricType
  resultForm.unit = template.defaultUnit || resultForm.unit
  resultForm.limitValue = template.defaultLimitValue || resultForm.limitValue
  resultForm.limitUnit = template.defaultLimitUnit || resultForm.limitUnit
}

async function saveCompanyProfile() {
  loading.value = true
  try {
    if (companyProfileForm.id) {
      await api.put(`/api/ohdp/maintenance/company-profiles/${companyProfileForm.id}`, companyProfileForm)
      setMessage('单位信息已更新')
    } else {
      await api.post('/api/ohdp/maintenance/company-profiles', companyProfileForm)
      setMessage('单位信息已新增')
    }
    await fetchCompanyProfiles()
    clearCompanyProfileForm()
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '保存单位信息失败')
  } finally {
    loading.value = false
  }
}

function editCompanyProfile(item: CompanyProfile) {
  companyProfileForm.id = item.id
  companyProfileForm.companyName = item.companyName || ''
  companyProfileForm.companyAddress = item.companyAddress || ''
  companyProfileForm.contactName = item.contactName || ''
  companyProfileForm.contactPhone = item.contactPhone || ''
  companyProfileForm.industryType = item.industryType || ''
  companyProfileForm.notes = item.notes || ''
}

function clearCompanyProfileForm() {
  companyProfileForm.id = null
  companyProfileForm.companyName = ''
  companyProfileForm.companyAddress = ''
  companyProfileForm.contactName = ''
  companyProfileForm.contactPhone = ''
  companyProfileForm.industryType = ''
  companyProfileForm.notes = ''
}

async function deleteCompanyProfile(id: number) {
  if (!window.confirm('确认删除单位信息吗？')) return
  await api.delete(`/api/ohdp/maintenance/company-profiles/${id}`)
  await fetchCompanyProfiles()
}

async function savePositionTemplate() {
  loading.value = true
  try {
    if (positionTemplateForm.id) {
      await api.put(`/api/ohdp/maintenance/position-templates/${positionTemplateForm.id}`, positionTemplateForm)
      setMessage('岗位模板已更新')
    } else {
      await api.post('/api/ohdp/maintenance/position-templates', positionTemplateForm)
      setMessage('岗位模板已新增')
    }
    await fetchPositionTemplates()
    clearPositionTemplateForm()
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '保存岗位模板失败')
  } finally {
    loading.value = false
  }
}

function editPositionTemplate(item: PositionTemplate) {
  positionTemplateForm.id = item.id
  positionTemplateForm.jobName = item.jobName || ''
  positionTemplateForm.shiftSystem = item.shiftSystem || ''
  positionTemplateForm.workingHours = item.workingHours || ''
  positionTemplateForm.operationType = item.operationType || ''
  positionTemplateForm.operationDuration = item.operationDuration || ''
  positionTemplateForm.hazards = item.hazards || ''
  positionTemplateForm.ppe = item.ppe || ''
  positionTemplateForm.protectionFacilities = item.protectionFacilities || ''
  positionTemplateForm.notes = item.notes || ''
}

function clearPositionTemplateForm() {
  positionTemplateForm.id = null
  positionTemplateForm.jobName = ''
  positionTemplateForm.shiftSystem = ''
  positionTemplateForm.workingHours = ''
  positionTemplateForm.operationType = ''
  positionTemplateForm.operationDuration = ''
  positionTemplateForm.hazards = ''
  positionTemplateForm.ppe = ''
  positionTemplateForm.protectionFacilities = ''
  positionTemplateForm.notes = ''
}

async function deletePositionTemplate(id: number) {
  if (!window.confirm('确认删除岗位模板吗？')) return
  await api.delete(`/api/ohdp/maintenance/position-templates/${id}`)
  await fetchPositionTemplates()
}

async function saveFactorTemplate() {
  loading.value = true
  try {
    if (factorTemplateForm.id) {
      await api.put(`/api/ohdp/maintenance/factor-templates/${factorTemplateForm.id}`, factorTemplateForm)
      setMessage('危害因素模板已更新')
    } else {
      await api.post('/api/ohdp/maintenance/factor-templates', factorTemplateForm)
      setMessage('危害因素模板已新增')
    }
    await fetchFactorTemplates()
    clearFactorTemplateForm()
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '保存危害因素模板失败')
  } finally {
    loading.value = false
  }
}

function editFactorTemplate(item: FactorTemplate) {
  factorTemplateForm.id = item.id
  factorTemplateForm.factorName = item.factorName || ''
  factorTemplateForm.metricType = item.metricType || 'TWA'
  factorTemplateForm.defaultUnit = item.defaultUnit || ''
  factorTemplateForm.defaultLimitValue = item.defaultLimitValue || ''
  factorTemplateForm.defaultLimitUnit = item.defaultLimitUnit || ''
  factorTemplateForm.notes = item.notes || ''
}

function clearFactorTemplateForm() {
  factorTemplateForm.id = null
  factorTemplateForm.factorName = ''
  factorTemplateForm.metricType = 'TWA'
  factorTemplateForm.defaultUnit = ''
  factorTemplateForm.defaultLimitValue = ''
  factorTemplateForm.defaultLimitUnit = ''
  factorTemplateForm.notes = ''
}

async function deleteFactorTemplate(id: number) {
  if (!window.confirm('确认删除危害因素模板吗？')) return
  await api.delete(`/api/ohdp/maintenance/factor-templates/${id}`)
  await fetchFactorTemplates()
}

async function saveDictionaryItem() {
  loading.value = true
  try {
    dictionaryForm.dictType = dictTypeQuery.value
    if (dictionaryForm.id) {
      await api.put(`/api/ohdp/maintenance/dictionary-items/${dictionaryForm.id}`, dictionaryForm)
      setMessage('字典项已更新')
    } else {
      await api.post('/api/ohdp/maintenance/dictionary-items', dictionaryForm)
      setMessage('字典项已新增')
    }
    await fetchDictionaryItems()
    clearDictionaryForm()
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '保存字典项失败')
  } finally {
    loading.value = false
  }
}

function editDictionaryItem(item: DictionaryItem) {
  dictionaryForm.id = item.id
  dictionaryForm.dictType = item.dictType || dictTypeQuery.value
  dictionaryForm.itemCode = item.itemCode || ''
  dictionaryForm.itemName = item.itemName || ''
  dictionaryForm.sortNo = item.sortNo || 0
  dictionaryForm.enabled = item.enabled !== false
}

function clearDictionaryForm() {
  dictionaryForm.id = null
  dictionaryForm.dictType = dictTypeQuery.value
  dictionaryForm.itemCode = ''
  dictionaryForm.itemName = ''
  dictionaryForm.sortNo = 0
  dictionaryForm.enabled = true
}

async function deleteDictionaryItem(id: number) {
  if (!window.confirm('确认删除字典项吗？')) return
  await api.delete(`/api/ohdp/maintenance/dictionary-items/${id}`)
  await fetchDictionaryItems()
}

onMounted(async () => {
  if (token.value) {
    await initAfterLogin()
  }
  autoSaveTimer = window.setInterval(async () => {
    if (selectedProject.value && projectDirty.value && !loading.value) {
      await updateProject(true)
      setMessage('已自动保存项目草稿')
    }
  }, 30000)
})

onUnmounted(() => {
  if (autoSaveTimer) {
    window.clearInterval(autoSaveTimer)
  }
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <h1>职业危害数据处理平台（MVP）</h1>
      <div class="topbar-right">
        <span v-if="loading" class="loading">处理中...</span>
        <button class="ghost" v-if="isLoggedIn" @click="activeTab = 'project'">项目管理</button>
        <button class="ghost" v-if="isLoggedIn" @click="activeTab = 'maintenance'">信息维护</button>
        <button v-if="isLoggedIn" class="ghost" @click="logout">退出</button>
      </div>
    </header>

    <p v-if="message" class="message">{{ message }}</p>

    <section v-if="!isLoggedIn" class="card login-card">
      <h2>登录</h2>
      <div class="grid2">
        <label>
          账号
          <input v-model="loginForm.username" placeholder="admin" />
        </label>
        <label>
          密码
          <input v-model="loginForm.password" type="password" placeholder="123456" />
        </label>
      </div>
      <button @click="login">登录系统</button>
    </section>

    <section v-else-if="activeTab === 'project'" class="workspace">
      <aside class="card sidebar">
        <h2>项目列表</h2>
        <div class="grid2 compact">
          <input v-model="query.projectCode" placeholder="项目编号" />
          <input v-model="query.companyName" placeholder="单位名称" />
        </div>
        <div class="grid2 compact">
          <select v-model="query.status">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="DATA_FILLED">已录入</option>
            <option value="JUDGED">已判定</option>
            <option value="REPORT_READY">已生成报告</option>
          </select>
          <button @click="fetchProjects">查询</button>
        </div>
        <p class="small">共 {{ totalProjects }} 条</p>
        <ul class="project-list">
          <li
            v-for="item in projects"
            :key="item.id"
            :class="{ active: selectedProject?.id === item.id }"
            @click="selectProject(item)"
          >
            <div class="title">{{ item.projectCode }}</div>
            <div class="meta">{{ item.companyName }}</div>
            <div class="badge">{{ statusText[item.status] }}</div>
          </li>
        </ul>
      </aside>

      <main class="main">
        <section class="card">
          <h2>项目信息</h2>
          <div class="grid2">
            <label>
              选择单位（可新增）
              <select v-model="projectForm.companyProfileId" @change="applyCompanyProfile(String(projectForm.companyProfileId || ''))">
                <option :value="null">手工输入</option>
                <option v-for="item in companyProfiles" :key="item.id" :value="item.id">{{ item.companyName }}</option>
              </select>
            </label>
            <label>
              检测类别
              <input v-model="projectForm.detectType" />
            </label>
            <label>
              受检单位
              <input v-model="projectForm.companyName" />
            </label>
            <label>
              单位地址
              <input v-model="projectForm.companyAddress" />
            </label>
            <label>
              采样日期（默认当天）
              <input v-model="projectForm.samplingDate" type="date" />
            </label>
            <label>
              报告日期（默认当天）
              <input v-model="projectForm.reportDate" type="date" />
            </label>
            <label class="full">
              备注
              <input v-model="projectForm.notes" />
            </label>
          </div>
          <div class="actions wrap">
            <button @click="createProject">新建项目</button>
            <button class="ghost" :disabled="!selectedProject" @click="updateProject()">保存项目</button>
            <button class="ghost" :disabled="!selectedProject" @click="copyProject">复制项目</button>
            <button class="danger" :disabled="!selectedProject" @click="deleteProject">删除项目</button>
            <span class="hint" v-if="selectedProject">自动保存：每 30 秒（有变更时）</span>
          </div>
        </section>

        <section class="card" v-if="selectedProject">
          <h2>岗位信息（可选模板）</h2>
          <div class="grid3">
            <label>
              岗位模板
              <select v-model="positionForm.positionTemplateId" @change="applyPositionTemplate(String(positionForm.positionTemplateId || ''))">
                <option :value="null">手工输入</option>
                <option v-for="item in positionTemplates" :key="item.id" :value="item.id">{{ item.jobName }}</option>
              </select>
            </label>
            <label>
              岗位
              <input v-model="positionForm.jobName" />
            </label>
            <label>
              班制
              <input v-model="positionForm.shiftSystem" />
            </label>
            <label>
              工时
              <input v-model="positionForm.workingHours" />
            </label>
            <label>
              人数
              <input v-model.number="positionForm.workerCount" type="number" min="0" />
            </label>
            <label>
              地点
              <input v-model="positionForm.workLocation" />
            </label>
            <label>
              作业类型
              <input v-model="positionForm.operationType" />
            </label>
            <label>
              作业内容
              <input v-model="positionForm.operationContent" />
            </label>
            <label>
              作业时间
              <input v-model="positionForm.operationDuration" />
            </label>
            <label>
              危害因素
              <input v-model="positionForm.hazards" />
            </label>
            <label>
              防护用品
              <input v-model="positionForm.ppe" />
            </label>
            <label>
              防护设施
              <input v-model="positionForm.protectionFacilities" />
            </label>
          </div>
          <div class="actions">
            <button @click="addPosition">添加岗位</button>
          </div>

          <table>
            <thead>
              <tr>
                <th>岗位</th>
                <th>班制/工时</th>
                <th>人数</th>
                <th>危害因素</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in positions" :key="item.id">
                <td>{{ item.jobName }}</td>
                <td>{{ item.shiftSystem }} / {{ item.workingHours }}</td>
                <td>{{ item.workerCount }}</td>
                <td>{{ item.hazards }}</td>
                <td><button class="danger tiny" @click="removePosition(item.id)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="card" v-if="selectedProject">
          <h2>检测结果（支持单条与批量粘贴）</h2>
          <div class="grid3">
            <label>
              危害因素模板
              <select v-model="resultForm.factorTemplateId" @change="applyFactorTemplate(String(resultForm.factorTemplateId || ''))">
                <option :value="null">手工输入</option>
                <option v-for="item in factorTemplates" :key="item.id" :value="item.id">
                  {{ item.factorName }} / {{ item.metricType }}
                </option>
              </select>
            </label>
            <label>岗位<input v-model="resultForm.jobName" /></label>
            <label>危害因素<input v-model="resultForm.factorName" /></label>
            <label>指标类型<input v-model="resultForm.metricType" /></label>
            <label>原始值<input v-model="resultForm.valueRaw" placeholder="如 <0.04" /></label>
            <label>数值<input v-model="resultForm.valueNum" type="number" step="0.001" /></label>
            <label>单位<input v-model="resultForm.unit" /></label>
            <label>限值<input v-model="resultForm.limitValue" type="number" step="0.001" /></label>
            <label>限值单位<input v-model="resultForm.limitUnit" /></label>
          </div>
          <div class="actions wrap">
            <button @click="addResult">添加结果</button>
            <button class="ghost" @click="judgeProject">自动判定</button>
            <button class="ghost" @click="precheckCurrentProject">报告预检</button>
            <button class="ghost" @click="previewReport">报告预览</button>
            <button class="ghost" @click="generateReport">生成报告（覆盖）</button>
          </div>

          <label>
            批量粘贴（每行：岗位,因素,指标,原始值,数值,单位,限值,限值单位；支持逗号/Tab/竖线）
            <textarea v-model="batchResultText" rows="4" placeholder="焊接,锰及其无机化合物,TWA,<0.04,,mg/m3,0.15,mg/m3"></textarea>
          </label>
          <div class="actions">
            <button class="ghost" @click="addBatchResults">批量导入</button>
          </div>

          <div class="precheck" v-if="reportPrecheck">
            <strong>预检结果：</strong>
            <span v-if="reportPrecheck.passed" class="ok">通过</span>
            <span v-else class="bad">未通过</span>
            <ul v-if="!reportPrecheck.passed">
              <li v-for="item in reportPrecheck.missingItems" :key="item">{{ mapMissingItem(item) }}</li>
            </ul>
          </div>

          <table>
            <thead>
              <tr>
                <th>岗位</th>
                <th>因素</th>
                <th>指标</th>
                <th>值</th>
                <th>限值</th>
                <th>判定</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in results" :key="item.id">
                <td>{{ item.jobName }}</td>
                <td>{{ item.factorName }}</td>
                <td>{{ item.metricType }}</td>
                <td>{{ item.valueRaw || item.valueNum }} {{ item.unit }}</td>
                <td>{{ item.limitValue }} {{ item.limitUnit }}</td>
                <td>{{ item.judgement }}</td>
                <td><button class="danger tiny" @click="removeResult(item.id)">删除</button></td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="card" v-if="selectedProject && showPreviewPanel && reportPreview">
          <h2>报告预览（生成前）</h2>
          <p>项目：{{ reportPreview.project.projectCode }} / {{ reportPreview.project.companyName }}</p>
          <p>岗位数：{{ reportPreview.positions.length }}；结果数：{{ reportPreview.results.length }}</p>
          <p>预检：<strong>{{ reportPreview.precheck.passed ? '通过' : '未通过' }}</strong></p>
        </section>

        <section class="card" v-if="selectedProject">
          <h2>报告文件（仅保留一份，重新生成会覆盖）</h2>
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>文件名</th>
                <th>生成人</th>
                <th>生成时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in reports" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.fileName }}</td>
                <td>{{ item.generatedBy || '-' }}</td>
                <td>{{ item.generatedAt }}</td>
                <td><button class="ghost tiny" @click="downloadReport(item.id, item.fileName)">下载</button></td>
              </tr>
            </tbody>
          </table>

          <h3>报告操作日志（覆盖记录）</h3>
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>操作</th>
                <th>操作人</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in reportLogs" :key="item.id">
                <td>{{ item.operatedAt }}</td>
                <td>{{ item.operationType }}</td>
                <td>{{ item.operatorName || '-' }}</td>
                <td>{{ item.operationMessage || '-' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </main>
    </section>

    <section v-else class="maintenance-grid">
      <section class="card">
        <h2>单位信息维护</h2>
        <div class="grid2">
          <label>单位名称<input v-model="companyProfileForm.companyName" /></label>
          <label>单位地址<input v-model="companyProfileForm.companyAddress" /></label>
          <label>联系人<input v-model="companyProfileForm.contactName" /></label>
          <label>联系电话<input v-model="companyProfileForm.contactPhone" /></label>
          <label>行业类型<input v-model="companyProfileForm.industryType" /></label>
          <label>备注<input v-model="companyProfileForm.notes" /></label>
        </div>
        <div class="actions">
          <button @click="saveCompanyProfile">{{ companyProfileForm.id ? '更新单位' : '新增单位' }}</button>
          <button class="ghost" @click="clearCompanyProfileForm">清空</button>
          <button class="ghost" @click="fetchCompanyProfiles">刷新</button>
        </div>
        <table>
          <thead><tr><th>单位名称</th><th>地址</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in companyProfiles" :key="item.id">
              <td>{{ item.companyName }}</td>
              <td>{{ item.companyAddress }}</td>
              <td>
                <button class="ghost tiny" @click="editCompanyProfile(item)">编辑</button>
                <button class="danger tiny" @click="deleteCompanyProfile(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <h2>岗位模板维护</h2>
        <div class="grid2">
          <label>岗位名称<input v-model="positionTemplateForm.jobName" /></label>
          <label>班制<input v-model="positionTemplateForm.shiftSystem" /></label>
          <label>工时<input v-model="positionTemplateForm.workingHours" /></label>
          <label>作业类型<input v-model="positionTemplateForm.operationType" /></label>
          <label>作业时间<input v-model="positionTemplateForm.operationDuration" /></label>
          <label>危害因素<input v-model="positionTemplateForm.hazards" /></label>
          <label>防护用品<input v-model="positionTemplateForm.ppe" /></label>
          <label>防护设施<input v-model="positionTemplateForm.protectionFacilities" /></label>
          <label class="full">备注<input v-model="positionTemplateForm.notes" /></label>
        </div>
        <div class="actions">
          <button @click="savePositionTemplate">{{ positionTemplateForm.id ? '更新岗位模板' : '新增岗位模板' }}</button>
          <button class="ghost" @click="clearPositionTemplateForm">清空</button>
          <button class="ghost" @click="fetchPositionTemplates">刷新</button>
        </div>
        <table>
          <thead><tr><th>岗位</th><th>班制/工时</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in positionTemplates" :key="item.id">
              <td>{{ item.jobName }}</td>
              <td>{{ item.shiftSystem }} / {{ item.workingHours }}</td>
              <td>
                <button class="ghost tiny" @click="editPositionTemplate(item)">编辑</button>
                <button class="danger tiny" @click="deletePositionTemplate(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <h2>危害因素模板维护</h2>
        <div class="grid2">
          <label>因素名称<input v-model="factorTemplateForm.factorName" /></label>
          <label>指标类型<input v-model="factorTemplateForm.metricType" /></label>
          <label>默认单位<input v-model="factorTemplateForm.defaultUnit" /></label>
          <label>默认限值<input v-model="factorTemplateForm.defaultLimitValue" /></label>
          <label>默认限值单位<input v-model="factorTemplateForm.defaultLimitUnit" /></label>
          <label>备注<input v-model="factorTemplateForm.notes" /></label>
        </div>
        <div class="actions">
          <button @click="saveFactorTemplate">{{ factorTemplateForm.id ? '更新因素模板' : '新增因素模板' }}</button>
          <button class="ghost" @click="clearFactorTemplateForm">清空</button>
          <button class="ghost" @click="fetchFactorTemplates">刷新</button>
        </div>
        <table>
          <thead><tr><th>因素</th><th>指标</th><th>默认限值</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in factorTemplates" :key="item.id">
              <td>{{ item.factorName }}</td>
              <td>{{ item.metricType }}</td>
              <td>{{ item.defaultLimitValue }} {{ item.defaultLimitUnit }}</td>
              <td>
                <button class="ghost tiny" @click="editFactorTemplate(item)">编辑</button>
                <button class="danger tiny" @click="deleteFactorTemplate(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card">
        <h2>字典项维护</h2>
        <div class="grid2">
          <label>
            字典类型
            <input v-model="dictTypeQuery" @blur="fetchDictionaryItems" />
          </label>
          <label>编码<input v-model="dictionaryForm.itemCode" /></label>
          <label>名称<input v-model="dictionaryForm.itemName" /></label>
          <label>排序<input v-model.number="dictionaryForm.sortNo" type="number" /></label>
          <label>启用
            <select v-model="dictionaryForm.enabled">
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </label>
        </div>
        <div class="actions">
          <button @click="saveDictionaryItem">{{ dictionaryForm.id ? '更新字典项' : '新增字典项' }}</button>
          <button class="ghost" @click="clearDictionaryForm">清空</button>
          <button class="ghost" @click="fetchDictionaryItems">刷新</button>
        </div>
        <table>
          <thead><tr><th>编码</th><th>名称</th><th>排序</th><th>启用</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in dictionaryItems" :key="item.id">
              <td>{{ item.itemCode }}</td>
              <td>{{ item.itemName }}</td>
              <td>{{ item.sortNo }}</td>
              <td>{{ item.enabled ? '是' : '否' }}</td>
              <td>
                <button class="ghost tiny" @click="editDictionaryItem(item)">编辑</button>
                <button class="danger tiny" @click="deleteDictionaryItem(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>
  </div>
</template>
