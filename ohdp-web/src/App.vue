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
  companyProfileId?: number
  companyName: string
  companyAddress: string
  detectTypeDictItemId?: number
  detectType: string
  samplingDate?: string
  reportDate?: string
  sampleName?: string
  submitUnitCompanyId?: number
  submitUnit?: string
  entrustUnitCompanyId?: number
  entrustUnit?: string
  taskOriginTemplateId?: number
  taskOrigin?: string
  evaluationBasisTemplateId?: number
  evaluationBasis?: string
  writerStaffId?: number
  writerName?: string
  reviewerStaffId?: number
  reviewerName?: string
  approverStaffId?: number
  approverName?: string
  writerCertNo?: string
  reviewerCertNo?: string
  approverCertNo?: string
  statementDate?: string
  notes?: string
  status: ProjectStatus
  createdAt?: string
  updatedAt?: string
}

interface Position {
  id: number
  projectId?: number | null
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
  positionId?: number
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

interface StaffProfile {
  id: number
  staffName: string
  staffTitle?: string
  certNo: string
  contactPhone?: string
  notes?: string
}

interface ReportTextTemplate {
  id: number
  templateType: string
  templateName: string
  templateContent: string
  enabled?: boolean
}

interface PageResult<T> {
  content: T[]
  totalElements: number
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
})

// ===== 新模块状态 =====
const basicDataChildMenu = ref<'law' | 'standard' | 'region' | 'device'>('law')
const hazardChildMenu = ref<'factor' | 'sds'>('factor')
const configChildMenu = ref<'detectMethod' | 'orgConfig' | 'detectType'>('detectMethod')
const labChildMenu = ref<'task' | 'result'>('task')

interface OhdpLaw { id: number; title: string; docNo?: string; level?: string; publishDate?: string; isValid?: boolean; notes?: string }
interface OhdpStandard { id: number; standardName: string; standardCode?: string; level?: string; isValid?: boolean; notes?: string }
interface OhdpRegion { id: number; parentId?: number; regionCode: string; regionName: string; level: number; sortNo?: number }
interface OhdpDevice { id: number; deviceName: string; deviceType: string; deviceModel?: string; deviceNo?: string; status?: string; calibrationExpiry?: string; notes?: string }
interface OhdpHazardFactor { id: number; factorName: string; factorCategory: string; factorCode?: string; casNo?: string; enabled?: boolean }
interface OhdpSds { id: number; substanceName: string; casNo?: string; factorId?: number; version?: string; notes?: string }
interface OhdpDetectionMethod { id: number; methodName: string; methodCode?: string; methodAbbr?: string; formula?: string; enabled?: boolean }
interface OhdpDetectType { id: number; typeName: string; typeCode: string; typeAbbr?: string; enabled?: boolean }
interface OhdpOrgConfig { orgName?: string; orgCode?: string; orgAddress?: string; contactName?: string; contactPhone?: string; certNo?: string; sampleNoPrefix?: string; reportNoPrefix?: string }
interface OhdpLabTask { id: number; taskCode: string; projectId: number; taskStatus: string; issuerName?: string; receiverName?: string; notes?: string }
interface OhdpLabResult { id: number; taskId: number; factorName: string; methodName?: string; interceptA?: number; slopeB?: number; correlationR?: number; resultValue?: number; resultUnit?: string; analystName?: string }
interface SysDept { id: number; deptName: string; parentId?: number; deptCode?: string; enabled?: boolean }
interface SysPost { id: number; postName: string; postCode?: string; enabled?: boolean }
interface SysNotice { id: number; title: string; noticeType?: string; publisher?: string; enabled?: boolean }
interface SysSysLog { id: number; userName?: string; operation?: string; resultType?: string; module?: string; createdAt?: string }
interface SysConfig { id: number; configKey: string; configValue?: string; configName?: string }
// ===== END 新模块接口 =====

const token = ref(localStorage.getItem('ohdp_token') || '')
const loading = ref(false)
const message = ref('')
const messageType = ref<'info' | 'success' | 'error'>('info')
const isMenuCollapse = ref(false)
const actionBusy = reactive({
  saveProject: false,
  copyProject: false,
  deleteProject: false,
  addPosition: false,
  addResult: false,
  judgeProject: false,
  precheckProject: false,
  previewReport: false,
  generateReport: false,
  downloadReport: false,
})
const activeTab = ref<'project' | 'maintenance' | 'system' | 'basicData' | 'hazardFactor' | 'configCenter' | 'labManage'>('project')
const projectChildMenu = ref<'workspace' | 'report'>('workspace')
const projectWorkTab = ref<'base' | 'supplement' | 'position' | 'result' | 'report'>('base')
const maintenanceChildMenu = ref<'company' | 'position' | 'factor' | 'dict' | 'staff' | 'reportText'>('company')
const systemChildMenu = ref<'user' | 'role' | 'menu' | 'permission' | 'dept' | 'post' | 'notice' | 'syslog' | 'sysfile' | 'sysconfig'>('user')
type MenuKey =
  | 'project' | 'project.workspace' | 'project.report'
  | 'maintenance' | 'maintenance.company' | 'maintenance.position'
  | 'maintenance.factor' | 'maintenance.dict' | 'maintenance.staff' | 'maintenance.reportText'
  | 'system' | 'system.user' | 'system.role' | 'system.menu' | 'system.permission'
  | 'system.dept' | 'system.post' | 'system.notice' | 'system.syslog' | 'system.sysfile' | 'system.sysconfig'
  | 'basicData' | 'basicData.law' | 'basicData.standard' | 'basicData.region' | 'basicData.device'
  | 'hazardFactor' | 'hazardFactor.factor' | 'hazardFactor.sds'
  | 'configCenter' | 'configCenter.detectMethod' | 'configCenter.orgConfig' | 'configCenter.detectType'
  | 'labManage' | 'labManage.task' | 'labManage.result'
const selectedMenuKey = ref<MenuKey>('project.workspace')
const showPreviewPanel = ref(false)

function openProjectMenu(child: 'workspace' | 'report' = 'workspace') {
  activeTab.value = 'project'
  projectChildMenu.value = child
  selectedMenuKey.value = child === 'workspace' ? 'project.workspace' : 'project.report'
  if (child === 'report') {
    projectWorkTab.value = 'report'
  } else if (projectWorkTab.value === 'report') {
    projectWorkTab.value = 'base'
  }
}

function openMaintenanceMenu(child: 'company' | 'position' | 'factor' | 'dict' | 'staff' | 'reportText' = 'company') {
  activeTab.value = 'maintenance'
  maintenanceChildMenu.value = child
  if (child === 'company') selectedMenuKey.value = 'maintenance.company'
  if (child === 'position') selectedMenuKey.value = 'maintenance.position'
  if (child === 'factor') selectedMenuKey.value = 'maintenance.factor'
  if (child === 'dict') selectedMenuKey.value = 'maintenance.dict'
  if (child === 'staff') selectedMenuKey.value = 'maintenance.staff'
  if (child === 'reportText') selectedMenuKey.value = 'maintenance.reportText'
}

function openSystemMenu(child: 'user' | 'role' | 'menu' | 'permission' | 'dept' | 'post' | 'notice' | 'syslog' | 'sysfile' | 'sysconfig' = 'user') {
  activeTab.value = 'system'
  systemChildMenu.value = child
  const keyMap: Record<typeof child, MenuKey> = {
    user: 'system.user', role: 'system.role', menu: 'system.menu', permission: 'system.permission',
    dept: 'system.dept', post: 'system.post', notice: 'system.notice',
    syslog: 'system.syslog', sysfile: 'system.sysfile', sysconfig: 'system.sysconfig',
  }
  selectedMenuKey.value = keyMap[child]
}

function selectMenu(key: MenuKey) {
  selectedMenuKey.value = key
  if (key === 'project') {
    openProjectMenu('workspace')
    return
  }
  if (key === 'project.workspace') {
    openProjectMenu('workspace')
    return
  }
  if (key === 'project.report') {
    openProjectMenu('report')
    return
  }
  if (key === 'maintenance') {
    openMaintenanceMenu('company')
    return
  }
  if (key === 'maintenance.company') {
    openMaintenanceMenu('company')
    return
  }
  if (key === 'maintenance.position') {
    openMaintenanceMenu('position')
    return
  }
  if (key === 'maintenance.factor') {
    openMaintenanceMenu('factor')
    return
  }
  if (key === 'maintenance.dict') {
    openMaintenanceMenu('dict')
    return
  }
  if (key === 'maintenance.staff') {
    openMaintenanceMenu('staff')
    return
  }
  if (key === 'system' || key === 'system.user') { openSystemMenu('user'); return }
  if (key === 'system.role') { openSystemMenu('role'); return }
  if (key === 'system.menu') { openSystemMenu('menu'); return }
  if (key === 'system.permission') { openSystemMenu('permission'); return }
  if (key === 'basicData' || key === 'basicData.law') { activeTab.value = 'basicData'; basicDataChildMenu.value = 'law'; selectedMenuKey.value = 'basicData.law'; return }
  if (key === 'basicData.standard') { activeTab.value = 'basicData'; basicDataChildMenu.value = 'standard'; selectedMenuKey.value = 'basicData.standard'; return }
  if (key === 'basicData.region') { activeTab.value = 'basicData'; basicDataChildMenu.value = 'region'; selectedMenuKey.value = 'basicData.region'; return }
  if (key === 'basicData.device') { activeTab.value = 'basicData'; basicDataChildMenu.value = 'device'; selectedMenuKey.value = 'basicData.device'; return }
  if (key === 'hazardFactor' || key === 'hazardFactor.factor') { activeTab.value = 'hazardFactor'; hazardChildMenu.value = 'factor'; selectedMenuKey.value = 'hazardFactor.factor'; return }
  if (key === 'hazardFactor.sds') { activeTab.value = 'hazardFactor'; hazardChildMenu.value = 'sds'; selectedMenuKey.value = 'hazardFactor.sds'; return }
  if (key === 'configCenter' || key === 'configCenter.detectMethod') { activeTab.value = 'configCenter'; configChildMenu.value = 'detectMethod'; selectedMenuKey.value = 'configCenter.detectMethod'; return }
  if (key === 'configCenter.orgConfig') { activeTab.value = 'configCenter'; configChildMenu.value = 'orgConfig'; selectedMenuKey.value = 'configCenter.orgConfig'; return }
  if (key === 'configCenter.detectType') { activeTab.value = 'configCenter'; configChildMenu.value = 'detectType'; selectedMenuKey.value = 'configCenter.detectType'; return }
  if (key === 'labManage' || key === 'labManage.task') { activeTab.value = 'labManage'; labChildMenu.value = 'task'; selectedMenuKey.value = 'labManage.task'; return }
  if (key === 'labManage.result') { activeTab.value = 'labManage'; labChildMenu.value = 'result'; selectedMenuKey.value = 'labManage.result'; return }
  if (key === 'system.dept') { openSystemMenu('dept'); return }
  if (key === 'system.post') { openSystemMenu('post'); return }
  if (key === 'system.notice') { openSystemMenu('notice'); return }
  if (key === 'system.syslog') { openSystemMenu('syslog'); return }
  if (key === 'system.sysfile') { openSystemMenu('sysfile'); return }
  if (key === 'system.sysconfig') { openSystemMenu('sysconfig'); return }
  openMaintenanceMenu('reportText')
}

function toggleSideCollapse() {
  isMenuCollapse.value = !isMenuCollapse.value
}

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
  submitUnitCompanyId: null as number | null,
  entrustUnitCompanyId: null as number | null,
  detectTypeDictItemId: null as number | null,
  taskOriginTemplateId: null as number | null,
  evaluationBasisTemplateId: null as number | null,
  writerStaffId: null as number | null,
  reviewerStaffId: null as number | null,
  approverStaffId: null as number | null,
  companyName: '',
  companyAddress: '',
  detectType: '监测检验',
  samplingDate: today(),
  reportDate: today(),
  sampleName: '工作场所空气、噪声等',
  submitUnit: '',
  entrustUnit: '',
  taskOrigin: '',
  evaluationBasis: '',
  writerName: '',
  reviewerName: '',
  approverName: '',
  writerCertNo: '',
  reviewerCertNo: '',
  approverCertNo: '',
  statementDate: today(),
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
  positionId: null as number | null,
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
const autoSaveEnabled = ref(false)
const autoSaveIntervalSec = ref(30)
const lastManualSaveAt = ref('')
const clearAfterCreate = ref(true)

const companyProfiles = ref<CompanyProfile[]>([])
const positionTemplates = ref<PositionTemplate[]>([])
const factorTemplates = ref<FactorTemplate[]>([])
const dictionaryItems = ref<DictionaryItem[]>([])
const detectTypeItems = ref<DictionaryItem[]>([])
const dictTypeQuery = ref('detect_type')
const reportTextTypeQuery = ref<'TASK_ORIGIN' | 'EVALUATION_BASIS'>('TASK_ORIGIN')

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

const staffProfiles = ref<StaffProfile[]>([])
const reportTextTemplates = ref<ReportTextTemplate[]>([])
const taskOriginTemplates = ref<ReportTextTemplate[]>([])
const evaluationBasisTemplates = ref<ReportTextTemplate[]>([])

const staffProfileForm = reactive({
  id: null as number | null,
  staffName: '',
  staffTitle: '',
  certNo: '',
  contactPhone: '',
  notes: '',
})

const reportTextTemplateForm = reactive({
  id: null as number | null,
  templateType: 'TASK_ORIGIN' as 'TASK_ORIGIN' | 'EVALUATION_BASIS',
  templateName: '',
  templateContent: '',
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

watch(reportTextTypeQuery, async (value) => {
  reportTextTemplateForm.templateType = value
  await fetchReportTextTemplates()
})

function today() {
  return new Date().toISOString().slice(0, 10)
}

function setMessage(text: string, type: 'info' | 'success' | 'error' = 'info') {
  message.value = text
  messageType.value = type
  window.setTimeout(() => {
    if (message.value === text) {
      message.value = ''
    }
  }, type === 'error' ? 5000 : 3500)
}

function nowTimeText() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function resolveApiMessage(err: any, fallback: string) {
  const raw = err?.response?.data?.message || ''
  if (typeof raw === 'string' && raw.includes('garbled text')) {
    return '检测到乱码数据，请先在信息维护中修复模板或使用标准UTF-8文本'
  }
  return raw || fallback
}

function resetProjectForm() {
  projectForm.companyProfileId = null
  projectForm.submitUnitCompanyId = null
  projectForm.entrustUnitCompanyId = null
  projectForm.detectTypeDictItemId = null
  projectForm.taskOriginTemplateId = null
  projectForm.evaluationBasisTemplateId = null
  projectForm.writerStaffId = null
  projectForm.reviewerStaffId = null
  projectForm.approverStaffId = null
  projectForm.companyName = ''
  projectForm.companyAddress = ''
  projectForm.detectType = '监测检验'
  projectForm.samplingDate = today()
  projectForm.reportDate = today()
  projectForm.sampleName = '工作场所空气、噪声等'
  projectForm.submitUnit = ''
  projectForm.entrustUnit = ''
  projectForm.taskOrigin = ''
  projectForm.evaluationBasis = ''
  projectForm.writerName = ''
  projectForm.reviewerName = ''
  projectForm.approverName = ''
  projectForm.writerCertNo = ''
  projectForm.reviewerCertNo = ''
  projectForm.approverCertNo = ''
  projectForm.statementDate = today()
  projectForm.notes = ''
}

function fillProjectForm(project: Project) {
  syncingProjectForm = true
  projectForm.companyProfileId = project.companyProfileId || null
  projectForm.submitUnitCompanyId = project.submitUnitCompanyId || null
  projectForm.entrustUnitCompanyId = project.entrustUnitCompanyId || null
  projectForm.detectTypeDictItemId = project.detectTypeDictItemId || null
  projectForm.taskOriginTemplateId = project.taskOriginTemplateId || null
  projectForm.evaluationBasisTemplateId = project.evaluationBasisTemplateId || null
  projectForm.writerStaffId = project.writerStaffId || null
  projectForm.reviewerStaffId = project.reviewerStaffId || null
  projectForm.approverStaffId = project.approverStaffId || null
  projectForm.companyName = project.companyName || ''
  projectForm.companyAddress = project.companyAddress || ''
  projectForm.detectType = project.detectType || '监测检验'
  projectForm.samplingDate = project.samplingDate || today()
  projectForm.reportDate = project.reportDate || today()
  projectForm.sampleName = project.sampleName || '工作场所空气、噪声等'
  projectForm.submitUnit = project.submitUnit || ''
  projectForm.entrustUnit = project.entrustUnit || ''
  projectForm.taskOrigin = project.taskOrigin || ''
  projectForm.evaluationBasis = project.evaluationBasis || ''
  projectForm.writerName = project.writerName || ''
  projectForm.reviewerName = project.reviewerName || ''
  projectForm.approverName = project.approverName || ''
  projectForm.writerCertNo = project.writerCertNo || ''
  projectForm.reviewerCertNo = project.reviewerCertNo || ''
  projectForm.approverCertNo = project.approverCertNo || ''
  projectForm.statementDate = project.statementDate || project.reportDate || today()
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
    'project.companyName.garbled': '受检单位名称（含乱码）',
    'project.companyAddress': '受检单位地址',
    'project.companyAddress.garbled': '受检单位地址（含乱码）',
    'project.detectType': '检测类别',
    'project.detectType.garbled': '检测类别（含乱码）',
    'project.samplingDate': '采样日期',
    'project.reportDate': '报告日期',
    'positions.empty': '至少需要 1 条岗位信息',
    'positions.garbled': '岗位信息含乱码',
    'results.empty': '至少需要 1 条检测结果',
    'results.requiredFields': '检测结果缺少岗位/因素/指标',
    'results.garbled': '检测结果含乱码',
  }
  return map[item] || item
}

function projectStatusTagType(status: ProjectStatus): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'REPORT_READY') return 'success'
  if (status === 'JUDGED') return 'warning'
  if (status === 'DATA_FILLED') return 'info'
  return 'danger'
}

function onProjectRowClick(row: Project) {
  void selectProject(row)
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
    fetchDetectTypeItems(),
    fetchStaffProfiles(),
    fetchTaskOriginTemplates(),
    fetchEvaluationBasisTemplates(),
    fetchReportTextTemplates(),
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
  if (!validateProjectSelections()) return
  loading.value = true
  actionBusy.saveProject = true
  try {
    const payload = {
      companyProfileId: projectForm.companyProfileId,
      submitUnitCompanyId: projectForm.submitUnitCompanyId,
      entrustUnitCompanyId: projectForm.entrustUnitCompanyId,
      detectTypeDictItemId: projectForm.detectTypeDictItemId,
      taskOriginTemplateId: projectForm.taskOriginTemplateId,
      evaluationBasisTemplateId: projectForm.evaluationBasisTemplateId,
      writerStaffId: projectForm.writerStaffId,
      reviewerStaffId: projectForm.reviewerStaffId,
      approverStaffId: projectForm.approverStaffId,
      companyName: projectForm.companyName,
      companyAddress: projectForm.companyAddress,
      detectType: projectForm.detectType,
      samplingDate: projectForm.samplingDate || null,
      reportDate: projectForm.reportDate || null,
      sampleName: projectForm.sampleName || null,
      submitUnit: projectForm.submitUnit || null,
      entrustUnit: projectForm.entrustUnit || null,
      taskOrigin: projectForm.taskOrigin || null,
      evaluationBasis: projectForm.evaluationBasis || null,
      writerName: projectForm.writerName || null,
      reviewerName: projectForm.reviewerName || null,
      approverName: projectForm.approverName || null,
      writerCertNo: projectForm.writerCertNo || null,
      reviewerCertNo: projectForm.reviewerCertNo || null,
      approverCertNo: projectForm.approverCertNo || null,
      statementDate: projectForm.statementDate || null,
      notes: projectForm.notes || null,
    }
    const { data } = await api.post<Project>('/api/ohdp/projects', payload)
    await fetchProjects()
    if (clearAfterCreate.value) {
      selectedProject.value = null
      positions.value = []
      results.value = []
      reports.value = []
      reportLogs.value = []
      reportPrecheck.value = null
      reportPreview.value = null
      showPreviewPanel.value = false
      resetProjectForm()
      projectDirty.value = false
      projectWorkTab.value = 'base'
      setMessage(`项目已创建：${data.projectCode}，表单已清空`, 'success')
    } else {
      await selectProject(data)
      projectWorkTab.value = 'base'
      lastManualSaveAt.value = nowTimeText()
      setMessage(`项目已创建并选中：${data.projectCode}`, 'success')
    }
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '创建项目失败'), 'error')
  } finally {
    actionBusy.saveProject = false
    loading.value = false
  }
}

async function updateProject(silent = false) {
  if (!selectedProject.value) return
  if (!silent && !validateProjectSelections()) return
  actionBusy.saveProject = true
  try {
    const payload = {
      companyProfileId: projectForm.companyProfileId,
      submitUnitCompanyId: projectForm.submitUnitCompanyId,
      entrustUnitCompanyId: projectForm.entrustUnitCompanyId,
      detectTypeDictItemId: projectForm.detectTypeDictItemId,
      taskOriginTemplateId: projectForm.taskOriginTemplateId,
      evaluationBasisTemplateId: projectForm.evaluationBasisTemplateId,
      writerStaffId: projectForm.writerStaffId,
      reviewerStaffId: projectForm.reviewerStaffId,
      approverStaffId: projectForm.approverStaffId,
      companyName: projectForm.companyName,
      companyAddress: projectForm.companyAddress,
      detectType: projectForm.detectType,
      samplingDate: projectForm.samplingDate || null,
      reportDate: projectForm.reportDate || null,
      sampleName: projectForm.sampleName || null,
      submitUnit: projectForm.submitUnit || null,
      entrustUnit: projectForm.entrustUnit || null,
      taskOrigin: projectForm.taskOrigin || null,
      evaluationBasis: projectForm.evaluationBasis || null,
      writerName: projectForm.writerName || null,
      reviewerName: projectForm.reviewerName || null,
      approverName: projectForm.approverName || null,
      writerCertNo: projectForm.writerCertNo || null,
      reviewerCertNo: projectForm.reviewerCertNo || null,
      approverCertNo: projectForm.approverCertNo || null,
      statementDate: projectForm.statementDate || null,
      notes: projectForm.notes || null,
    }
    const { data } = await api.put<Project>(`/api/ohdp/projects/${selectedProject.value.id}`, payload)
    selectedProject.value = data
    projectDirty.value = false
    await fetchProjects()
    lastManualSaveAt.value = nowTimeText()
    if (!silent) {
      setMessage('项目已更新', 'success')
    }
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '更新项目失败'), 'error')
  } finally {
    actionBusy.saveProject = false
  }
}

async function deleteProject() {
  if (!selectedProject.value) return
  if (!window.confirm('确认删除当前项目吗？')) return
  loading.value = true
  actionBusy.deleteProject = true
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
    setMessage('项目已删除', 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '删除项目失败', 'error')
  } finally {
    actionBusy.deleteProject = false
    loading.value = false
  }
}

async function copyProject() {
  if (!selectedProject.value) return
  loading.value = true
  actionBusy.copyProject = true
  try {
    const { data } = await api.post<Project>(`/api/ohdp/projects/${selectedProject.value.id}/copy`)
    await fetchProjects()
    await selectProject(data)
    setMessage('项目已复制', 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '复制项目失败', 'error')
  } finally {
    actionBusy.copyProject = false
    loading.value = false
  }
}

async function fetchPositions(projectId: number) {
  const { data } = await api.get<Position[]>(`/api/ohdp/projects/${projectId}/positions`)
  positions.value = data
}

async function addPosition() {
  if (!selectedProject.value) return
  if (!positionForm.positionTemplateId) {
    setMessage('请先选择岗位模板', 'error')
    return
  }
  loading.value = true
  actionBusy.addPosition = true
  try {
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/positions`, {
      ...positionForm,
      workerCount: Number(positionForm.workerCount) || 0,
    })
    await fetchPositions(selectedProject.value.id)
    await fetchProjects()
    setMessage('岗位已添加', 'success')
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '添加岗位失败'), 'error')
  } finally {
    actionBusy.addPosition = false
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
  if (!resultForm.factorTemplateId) {
    setMessage('请先选择危害因素模板', 'error')
    return
  }
  if (!resultForm.positionId) {
    setMessage('请先选择岗位', 'error')
    return
  }
  loading.value = true
  actionBusy.addResult = true
  try {
    await api.post(`/api/ohdp/projects/${selectedProject.value.id}/results`, {
      factorTemplateId: resultForm.factorTemplateId,
      positionId: resultForm.positionId,
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
    setMessage('检测结果已添加', 'success')
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '添加检测结果失败'), 'error')
  } finally {
    actionBusy.addResult = false
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
    setMessage(resolveApiMessage(err, '批量导入失败'))
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
  actionBusy.judgeProject = true
  try {
    const { data } = await api.post(`/api/ohdp/projects/${selectedProject.value.id}/judge`)
    await fetchResults(selectedProject.value.id)
    await fetchProjects()
    setMessage(`自动判定完成：合格 ${data.qualifiedCount}，不合格 ${data.unqualifiedCount}`, 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '自动判定失败', 'error')
  } finally {
    actionBusy.judgeProject = false
    loading.value = false
  }
}

async function precheckCurrentProject() {
  if (!selectedProject.value) return
  actionBusy.precheckProject = true
  try {
    const { data } = await api.get<ReportPrecheck>(`/api/ohdp/projects/${selectedProject.value.id}/report/precheck`)
    reportPrecheck.value = data
    if (data.passed) {
      setMessage('报告预检通过', 'success')
    } else {
      setMessage(`报告预检未通过：缺失 ${data.missingItems.length} 项`, 'error')
    }
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告预检失败', 'error')
    throw err
  } finally {
    actionBusy.precheckProject = false
  }
}

async function previewReport() {
  if (!selectedProject.value) return
  loading.value = true
  actionBusy.previewReport = true
  try {
    const { data } = await api.get<ReportPreview>(`/api/ohdp/projects/${selectedProject.value.id}/report/preview`)
    reportPreview.value = data
    reportPrecheck.value = data.precheck
    showPreviewPanel.value = true
    setMessage('预览数据已刷新', 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '预览失败', 'error')
  } finally {
    actionBusy.previewReport = false
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
  actionBusy.generateReport = true
  try {
    await precheckCurrentProject()
    if (reportPrecheck.value && !reportPrecheck.value.passed) {
      setMessage('报告预检未通过，请先补全数据', 'error')
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
    setMessage('报告已生成（覆盖模式）', 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告生成失败', 'error')
  } finally {
    actionBusy.generateReport = false
    loading.value = false
  }
}

async function downloadReport(reportId: number, fileName: string) {
  loading.value = true
  actionBusy.downloadReport = true
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
    setMessage(err?.response?.data?.message || '下载失败', 'error')
  } finally {
    actionBusy.downloadReport = false
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

async function fetchDetectTypeItems() {
  const { data } = await api.get<DictionaryItem[]>('/api/ohdp/maintenance/dictionary-items', {
    params: { dictType: 'detect_type' },
  })
  detectTypeItems.value = (data || []).filter((item) => item.enabled !== false)
}

async function fetchStaffProfiles() {
  const { data } = await api.get<PageResult<StaffProfile>>('/api/ohdp/maintenance/staff-profiles', {
    params: { page: 0, size: 200 },
  })
  staffProfiles.value = data.content || []
}

async function fetchTaskOriginTemplates() {
  const { data } = await api.get<ReportTextTemplate[]>('/api/ohdp/maintenance/report-text-templates/enabled', {
    params: { templateType: 'TASK_ORIGIN' },
  })
  taskOriginTemplates.value = data || []
}

async function fetchEvaluationBasisTemplates() {
  const { data } = await api.get<ReportTextTemplate[]>('/api/ohdp/maintenance/report-text-templates/enabled', {
    params: { templateType: 'EVALUATION_BASIS' },
  })
  evaluationBasisTemplates.value = data || []
}

async function fetchReportTextTemplates() {
  const { data } = await api.get<PageResult<ReportTextTemplate>>('/api/ohdp/maintenance/report-text-templates', {
    params: { templateType: reportTextTypeQuery.value, page: 0, size: 200 },
  })
  reportTextTemplates.value = data.content || []
}

function applyCompanyProfile(profileId: string) {
  const id = Number(profileId)
  const profile = companyProfiles.value.find((item) => item.id === id)
  if (!profile) return
  projectForm.companyProfileId = profile.id
  projectForm.companyName = profile.companyName
  projectForm.companyAddress = profile.companyAddress
}

function applyProjectUnitSelection(field: 'submitUnit' | 'entrustUnit', profileId: string) {
  const id = Number(profileId)
  if (!id) {
    if (field === 'submitUnit') {
      projectForm.submitUnitCompanyId = null
      projectForm.submitUnit = ''
    } else {
      projectForm.entrustUnitCompanyId = null
      projectForm.entrustUnit = ''
    }
    return
  }
  const profile = companyProfiles.value.find((item) => item.id === id)
  if (!profile) return
  if (field === 'submitUnit') {
    projectForm.submitUnitCompanyId = profile.id
    projectForm.submitUnit = profile.companyName
    return
  }
  projectForm.entrustUnitCompanyId = profile.id
  projectForm.entrustUnit = profile.companyName
}

function validateProjectSelections() {
  if (!projectForm.companyProfileId) {
    setMessage('请先选择受检单位', 'error')
    return false
  }
  if (!projectForm.submitUnitCompanyId) {
    setMessage('请先选择送检单位', 'error')
    return false
  }
  if (!projectForm.entrustUnitCompanyId) {
    setMessage('请先选择委托单位', 'error')
    return false
  }
  return true
}

function applyDetectTypeDict(dictId: string) {
  const id = Number(dictId)
  const dict = detectTypeItems.value.find((item) => item.id === id)
  if (!dict) return
  projectForm.detectTypeDictItemId = dict.id
  projectForm.detectType = dict.itemName
}

function applyTaskOriginTemplate(templateId: string) {
  const id = Number(templateId)
  const template = taskOriginTemplates.value.find((item) => item.id === id)
  if (!template) return
  projectForm.taskOriginTemplateId = template.id
  projectForm.taskOrigin = template.templateContent
}

function applyEvaluationBasisTemplate(templateId: string) {
  const id = Number(templateId)
  const template = evaluationBasisTemplates.value.find((item) => item.id === id)
  if (!template) return
  projectForm.evaluationBasisTemplateId = template.id
  projectForm.evaluationBasis = template.templateContent
}

function applyStaffSelection(role: 'writer' | 'reviewer' | 'approver', staffId: string) {
  const id = Number(staffId)
  const staff = staffProfiles.value.find((item) => item.id === id)
  if (!staff) return
  if (role === 'writer') {
    projectForm.writerStaffId = staff.id
    projectForm.writerName = staff.staffName
    projectForm.writerCertNo = staff.certNo
    return
  }
  if (role === 'reviewer') {
    projectForm.reviewerStaffId = staff.id
    projectForm.reviewerName = staff.staffName
    projectForm.reviewerCertNo = staff.certNo
    return
  }
  projectForm.approverStaffId = staff.id
  projectForm.approverName = staff.staffName
  projectForm.approverCertNo = staff.certNo
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

function applyResultPosition(positionId: string) {
  const id = Number(positionId)
  const position = positions.value.find((item) => item.id === id)
  if (!position) return
  resultForm.positionId = position.id
  resultForm.jobName = position.jobName
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
    setMessage(resolveApiMessage(err, '保存单位信息失败'))
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
    setMessage(resolveApiMessage(err, '保存岗位模板失败'))
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
    setMessage(resolveApiMessage(err, '保存危害因素模板失败'))
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
    if (dictTypeQuery.value === 'detect_type') {
      await fetchDetectTypeItems()
    }
    clearDictionaryForm()
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '保存字典项失败'))
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
  if (dictTypeQuery.value === 'detect_type') {
    await fetchDetectTypeItems()
  }
}

async function saveStaffProfile() {
  loading.value = true
  try {
    if (staffProfileForm.id) {
      await api.put(`/api/ohdp/maintenance/staff-profiles/${staffProfileForm.id}`, staffProfileForm)
      setMessage('人员资质已更新')
    } else {
      await api.post('/api/ohdp/maintenance/staff-profiles', staffProfileForm)
      setMessage('人员资质已新增')
    }
    await fetchStaffProfiles()
    clearStaffProfileForm()
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '保存人员资质失败'))
  } finally {
    loading.value = false
  }
}

function editStaffProfile(item: StaffProfile) {
  staffProfileForm.id = item.id
  staffProfileForm.staffName = item.staffName || ''
  staffProfileForm.staffTitle = item.staffTitle || ''
  staffProfileForm.certNo = item.certNo || ''
  staffProfileForm.contactPhone = item.contactPhone || ''
  staffProfileForm.notes = item.notes || ''
}

function clearStaffProfileForm() {
  staffProfileForm.id = null
  staffProfileForm.staffName = ''
  staffProfileForm.staffTitle = ''
  staffProfileForm.certNo = ''
  staffProfileForm.contactPhone = ''
  staffProfileForm.notes = ''
}

async function deleteStaffProfile(id: number) {
  if (!window.confirm('确认删除人员资质吗？')) return
  await api.delete(`/api/ohdp/maintenance/staff-profiles/${id}`)
  await fetchStaffProfiles()
}

async function saveReportTextTemplate() {
  loading.value = true
  try {
    if (reportTextTemplateForm.id) {
      await api.put(`/api/ohdp/maintenance/report-text-templates/${reportTextTemplateForm.id}`, reportTextTemplateForm)
      setMessage('报告文本模板已更新')
    } else {
      await api.post('/api/ohdp/maintenance/report-text-templates', reportTextTemplateForm)
      setMessage('报告文本模板已新增')
    }
    await Promise.all([fetchReportTextTemplates(), fetchTaskOriginTemplates(), fetchEvaluationBasisTemplates()])
    clearReportTextTemplateForm()
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '保存报告文本模板失败'))
  } finally {
    loading.value = false
  }
}

function startNewDraft() {
  selectedProject.value = null
  positions.value = []
  results.value = []
  reports.value = []
  reportLogs.value = []
  reportPrecheck.value = null
  reportPreview.value = null
  showPreviewPanel.value = false
  resetProjectForm()
  projectDirty.value = false
  projectWorkTab.value = 'base'
  setMessage('已清空表单，请填写后点击“创建项目”', 'info')
}

function setupAutoSaveTimer() {
  if (autoSaveTimer) {
    window.clearInterval(autoSaveTimer)
    autoSaveTimer = undefined
  }
  if (!autoSaveEnabled.value || !token.value) {
    return
  }
  const sec = Math.max(10, Number(autoSaveIntervalSec.value) || 30)
  autoSaveTimer = window.setInterval(async () => {
    if (selectedProject.value && projectDirty.value && !loading.value) {
      await updateProject(true)
    }
  }, sec * 1000)
}

function editReportTextTemplate(item: ReportTextTemplate) {
  reportTextTemplateForm.id = item.id
  reportTextTemplateForm.templateType = (item.templateType as 'TASK_ORIGIN' | 'EVALUATION_BASIS') || 'TASK_ORIGIN'
  reportTextTemplateForm.templateName = item.templateName || ''
  reportTextTemplateForm.templateContent = item.templateContent || ''
  reportTextTemplateForm.enabled = item.enabled !== false
}

function clearReportTextTemplateForm() {
  reportTextTemplateForm.id = null
  reportTextTemplateForm.templateType = reportTextTypeQuery.value
  reportTextTemplateForm.templateName = ''
  reportTextTemplateForm.templateContent = ''
  reportTextTemplateForm.enabled = true
}

async function deleteReportTextTemplate(id: number) {
  if (!window.confirm('确认删除报告文本模板吗？')) return
  await api.delete(`/api/ohdp/maintenance/report-text-templates/${id}`)
  await Promise.all([fetchReportTextTemplates(), fetchTaskOriginTemplates(), fetchEvaluationBasisTemplates()])
}

// ===== 新模块数据 =====
const laws = ref<OhdpLaw[]>([])
const standards = ref<OhdpStandard[]>([])
const regions = ref<OhdpRegion[]>([])
const devices = ref<OhdpDevice[]>([])
const hazardFactors = ref<OhdpHazardFactor[]>([])
const sdsList = ref<OhdpSds[]>([])
const detectionMethods = ref<OhdpDetectionMethod[]>([])
const detectTypes = ref<OhdpDetectType[]>([])
const orgConfig = ref<OhdpOrgConfig>({})
const labTasks = ref<OhdpLabTask[]>([])
const labResults = ref<OhdpLabResult[]>([])
const sysDepts = ref<SysDept[]>([])
const sysPosts = ref<SysPost[]>([])
const sysNotices = ref<SysNotice[]>([])
const sysLogs = ref<SysSysLog[]>([])
const sysConfigs = ref<SysConfig[]>([])

const lawForm = reactive({ id: null as number|null, title: '', docNo: '', level: 'NATIONAL', publishDate: '', isValid: true, notes: '' })
const standardForm = reactive({ id: null as number|null, standardName: '', standardCode: '', level: 'NATIONAL', isValid: true, notes: '' })
const regionForm = reactive({ id: null as number|null, parentId: null as number|null, regionCode: '', regionName: '', level: 1, sortNo: 0 })
const deviceForm = reactive({ id: null as number|null, deviceName: '', deviceType: 'LAB_INSTRUMENT', deviceModel: '', deviceNo: '', status: 'NORMAL', notes: '' })
const hazardFactorForm = reactive({ id: null as number|null, factorName: '', factorCategory: 'CHEMICAL', factorCode: '', casNo: '', enabled: true })
const sdsForm = reactive({ id: null as number|null, substanceName: '', casNo: '', factorId: null as number|null, version: '', notes: '' })
const detectionMethodForm = reactive({ id: null as number|null, methodName: '', methodCode: '', methodAbbr: '', formula: '', enabled: true })
const detectTypeForm = reactive({ id: null as number|null, typeName: '', typeCode: '', typeAbbr: '', sortNo: 0, enabled: true })
const orgConfigForm = reactive({ orgName: '', orgCode: '', orgAddress: '', contactName: '', contactPhone: '', certNo: '', sampleNoPrefix: 'S', reportNoPrefix: 'R' })
const labTaskForm = reactive({ id: null as number|null, projectId: null as number|null, issuerName: '', notes: '' })
const deptForm = reactive({ id: null as number|null, deptName: '', deptCode: '', parentId: null as number|null, enabled: true })
const postForm = reactive({ id: null as number|null, postName: '', postCode: '', enabled: true })
const noticeForm = reactive({ id: null as number|null, title: '', content: '', noticeType: 'NOTICE', publisher: '', enabled: true })
const sysConfigForm = reactive({ id: null as number|null, configKey: '', configValue: '', configName: '' })

// ===== 新模块 API 函数 =====
async function fetchLaws() { const r = await api.get('/api/ohdp/basic/laws', { params: { page: 0, size: 100 } }); laws.value = r.data.content || [] }
async function saveLaw() { if (lawForm.id) { await api.put(`/api/ohdp/basic/laws/${lawForm.id}`, { ...lawForm }) } else { await api.post('/api/ohdp/basic/laws', { ...lawForm }) }; Object.assign(lawForm, { id: null, title: '', docNo: '', level: 'NATIONAL', publishDate: '', isValid: true, notes: '' }); fetchLaws() }
async function deleteLaw(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/laws/${id}`); fetchLaws() }
function editLaw(item: OhdpLaw) { Object.assign(lawForm, item) }

async function fetchStandards() { const r = await api.get('/api/ohdp/basic/standards', { params: { page: 0, size: 100 } }); standards.value = r.data.content || [] }
async function saveStandard() { if (standardForm.id) { await api.put(`/api/ohdp/basic/standards/${standardForm.id}`, { ...standardForm }) } else { await api.post('/api/ohdp/basic/standards', { ...standardForm }) }; Object.assign(standardForm, { id: null, standardName: '', standardCode: '', level: 'NATIONAL', isValid: true, notes: '' }); fetchStandards() }
async function deleteStandard(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/standards/${id}`); fetchStandards() }
function editStandard(item: OhdpStandard) { Object.assign(standardForm, item) }

async function fetchRegions() { const r = await api.get('/api/ohdp/basic/regions'); regions.value = r.data || [] }
async function saveRegion() { if (regionForm.id) { await api.put(`/api/ohdp/basic/regions/${regionForm.id}`, { ...regionForm }) } else { await api.post('/api/ohdp/basic/regions', { ...regionForm }) }; Object.assign(regionForm, { id: null, parentId: null, regionCode: '', regionName: '', level: 1, sortNo: 0 }); fetchRegions() }
async function deleteRegion(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/regions/${id}`); fetchRegions() }
function editRegion(item: OhdpRegion) { Object.assign(regionForm, item) }

async function fetchDevices() { const r = await api.get('/api/ohdp/devices', { params: { page: 0, size: 100 } }); devices.value = r.data.content || [] }
async function saveDevice() { if (deviceForm.id) { await api.put(`/api/ohdp/devices/${deviceForm.id}`, { ...deviceForm }) } else { await api.post('/api/ohdp/devices', { ...deviceForm }) }; Object.assign(deviceForm, { id: null, deviceName: '', deviceType: 'LAB_INSTRUMENT', deviceModel: '', deviceNo: '', status: 'NORMAL', notes: '' }); fetchDevices() }
async function deleteDevice(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/devices/${id}`); fetchDevices() }
function editDevice(item: OhdpDevice) { Object.assign(deviceForm, item) }

async function fetchHazardFactors() { const r = await api.get('/api/ohdp/hazard-factors', { params: { page: 0, size: 200 } }); hazardFactors.value = r.data.content || [] }
async function saveHazardFactor() { if (hazardFactorForm.id) { await api.put(`/api/ohdp/hazard-factors/${hazardFactorForm.id}`, { ...hazardFactorForm }) } else { await api.post('/api/ohdp/hazard-factors', { ...hazardFactorForm }) }; Object.assign(hazardFactorForm, { id: null, factorName: '', factorCategory: 'CHEMICAL', factorCode: '', casNo: '', enabled: true }); fetchHazardFactors() }
async function deleteHazardFactor(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/hazard-factors/${id}`); fetchHazardFactors() }
function editHazardFactor(item: OhdpHazardFactor) { Object.assign(hazardFactorForm, item) }

async function fetchSdsList() { const r = await api.get('/api/ohdp/sds', { params: { page: 0, size: 100 } }); sdsList.value = r.data.content || [] }
async function saveSds() { if (sdsForm.id) { await api.put(`/api/ohdp/sds/${sdsForm.id}`, { ...sdsForm }) } else { await api.post('/api/ohdp/sds', { ...sdsForm }) }; Object.assign(sdsForm, { id: null, substanceName: '', casNo: '', factorId: null, version: '', notes: '' }); fetchSdsList() }
async function deleteSds(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/sds/${id}`); fetchSdsList() }
function editSds(item: OhdpSds) { Object.assign(sdsForm, item) }

async function fetchDetectionMethods() { const r = await api.get('/api/ohdp/config/detection-methods', { params: { page: 0, size: 100 } }); detectionMethods.value = r.data.content || [] }
async function saveDetectionMethod() { if (detectionMethodForm.id) { await api.put(`/api/ohdp/config/detection-methods/${detectionMethodForm.id}`, { ...detectionMethodForm }) } else { await api.post('/api/ohdp/config/detection-methods', { ...detectionMethodForm }) }; Object.assign(detectionMethodForm, { id: null, methodName: '', methodCode: '', methodAbbr: '', formula: '', enabled: true }); fetchDetectionMethods() }
async function deleteDetectionMethod(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/config/detection-methods/${id}`); fetchDetectionMethods() }
function editDetectionMethod(item: OhdpDetectionMethod) { Object.assign(detectionMethodForm, item) }

async function fetchDetectTypes() { const r = await api.get('/api/ohdp/config/detect-types'); detectTypes.value = r.data || [] }
async function saveDetectType() { if (detectTypeForm.id) { await api.put(`/api/ohdp/config/detect-types/${detectTypeForm.id}`, { ...detectTypeForm }) } else { await api.post('/api/ohdp/config/detect-types', { ...detectTypeForm }) }; Object.assign(detectTypeForm, { id: null, typeName: '', typeCode: '', typeAbbr: '', sortNo: 0, enabled: true }); fetchDetectTypes() }
async function deleteDetectType(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/config/detect-types/${id}`); fetchDetectTypes() }
function editDetectType(item: OhdpDetectType) { Object.assign(detectTypeForm, item) }

async function fetchOrgConfig() { try { const r = await api.get('/api/ohdp/config/org'); if (r.data) Object.assign(orgConfigForm, r.data) } catch (e) {} }
async function saveOrgConfig() { await api.put('/api/ohdp/config/org', { ...orgConfigForm }); alert('保存成功') }

async function fetchLabTasks() { const r = await api.get('/api/ohdp/lab/tasks', { params: { page: 0, size: 50 } }); labTasks.value = r.data.content || [] }
async function createLabTask() { await api.post('/api/ohdp/lab/tasks', { ...labTaskForm, samples: [] }); Object.assign(labTaskForm, { id: null, projectId: null, issuerName: '', notes: '' }); fetchLabTasks() }
async function receiveTask(id: number, receiverName: string) { await api.post(`/api/ohdp/lab/tasks/${id}/receive`, { receiverName }); fetchLabTasks() }
async function submitTask(id: number) { await api.post(`/api/ohdp/lab/tasks/${id}/submit`, {}); fetchLabTasks() }

async function fetchLabResults() { const r = await api.get('/api/ohdp/lab/results', { params: { page: 0, size: 50 } }); labResults.value = r.data.content || [] }
async function calcLabResult(id: number) { await api.post(`/api/ohdp/lab/results/${id}/calculate`, {}); fetchLabResults(); alert('计算完成') }

async function fetchSysDepts() { const r = await api.get('/api/system/departments'); sysDepts.value = r.data || [] }
async function saveDept() { if (deptForm.id) { await api.put(`/api/system/departments/${deptForm.id}`, { ...deptForm }) } else { await api.post('/api/system/departments', { ...deptForm }) }; Object.assign(deptForm, { id: null, deptName: '', deptCode: '', parentId: null, enabled: true }); fetchSysDepts() }
async function deleteDept(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/system/departments/${id}`); fetchSysDepts() }
function editDept(item: SysDept) { Object.assign(deptForm, item) }

async function fetchSysPosts() { const r = await api.get('/api/system/posts', { params: { page: 0, size: 100 } }); sysPosts.value = r.data.content || [] }
async function savePost() { if (postForm.id) { await api.put(`/api/system/posts/${postForm.id}`, { ...postForm }) } else { await api.post('/api/system/posts', { ...postForm }) }; Object.assign(postForm, { id: null, postName: '', postCode: '', enabled: true }); fetchSysPosts() }
async function deletePost(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/system/posts/${id}`); fetchSysPosts() }
function editPost(item: SysPost) { Object.assign(postForm, item) }

async function fetchSysNotices() { const r = await api.get('/api/system/notices', { params: { page: 0, size: 20 } }); sysNotices.value = r.data.content || [] }
async function saveNotice() { if (noticeForm.id) { await api.put(`/api/system/notices/${noticeForm.id}`, { ...noticeForm }) } else { await api.post('/api/system/notices', { ...noticeForm }) }; Object.assign(noticeForm, { id: null, title: '', content: '', noticeType: 'NOTICE', publisher: '', enabled: true }); fetchSysNotices() }
async function deleteNotice(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/system/notices/${id}`); fetchSysNotices() }

async function fetchSysLogs() { const r = await api.get('/api/system/logs', { params: { page: 0, size: 50 } }); sysLogs.value = r.data.content || [] }

async function fetchSysConfigs() { const r = await api.get('/api/system/configs', { params: { page: 0, size: 100 } }); sysConfigs.value = r.data.content || [] }
async function saveSysConfig() { if (sysConfigForm.id) { await api.put(`/api/system/configs/${sysConfigForm.id}`, { ...sysConfigForm }) } else { await api.post('/api/system/configs', { ...sysConfigForm }) }; Object.assign(sysConfigForm, { id: null, configKey: '', configValue: '', configName: '' }); fetchSysConfigs() }
async function deleteSysConfig(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/system/configs/${id}`); fetchSysConfigs() }
function editSysConfig(item: SysConfig) { Object.assign(sysConfigForm, item) }
// ===== END 新模块 =====

onMounted(async () => {
  if (token.value) {
    await initAfterLogin()
  }
  setupAutoSaveTimer()
})

onUnmounted(() => {
  if (autoSaveTimer) {
    window.clearInterval(autoSaveTimer)
  }
})

watch([autoSaveEnabled, autoSaveIntervalSec, token], () => {
  setupAutoSaveTimer()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <h1>职业危害数据处理平台（MVP）</h1>
      <div class="topbar-right">
        <span v-if="loading" class="loading">处理中...</span>
        <button v-if="isLoggedIn" class="ghost" @click="logout">退出</button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

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

    <section v-else class="admin-layout" :class="{ collapsed: isMenuCollapse }">
      <aside class="card menu-tree pro-menu">
        <div class="menu-header">
          <h2>功能菜单</h2>
          <button class="ghost tiny" @click="toggleSideCollapse">{{ isMenuCollapse ? '展开' : '收起' }}</button>
        </div>
        <el-menu
          :default-active="selectedMenuKey"
          class="el-side-menu"
          :collapse="isMenuCollapse"
          unique-opened
          @select="(index: string) => selectMenu(index as MenuKey)"
        >
          <el-menu-item index="project">项目管理</el-menu-item>
          <el-sub-menu index="project-group">
            <template #title>项目子功能</template>
            <el-menu-item index="project.workspace">项目工作台</el-menu-item>
            <el-menu-item index="project.report">报告管理</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="maintenance">信息维护</el-menu-item>
          <el-sub-menu index="maintenance-group">
            <template #title>信息维护子菜单</template>
            <el-menu-item index="maintenance.company">单位信息</el-menu-item>
            <el-menu-item index="maintenance.position">岗位模板</el-menu-item>
            <el-menu-item index="maintenance.factor">危害因素模板</el-menu-item>
            <el-menu-item index="maintenance.dict">字典项</el-menu-item>
            <el-menu-item index="maintenance.staff">人员资质</el-menu-item>
            <el-menu-item index="maintenance.reportText">报告文本模板</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="system-group">
            <template #title>系统管理</template>
            <el-menu-item index="system.user">用户管理</el-menu-item>
            <el-menu-item index="system.role">角色管理</el-menu-item>
            <el-menu-item index="system.menu">菜单管理</el-menu-item>
            <el-menu-item index="system.permission">权限管理</el-menu-item>
            <el-menu-item index="system.dept">部门管理</el-menu-item>
            <el-menu-item index="system.post">岗位管理</el-menu-item>
            <el-menu-item index="system.notice">通知公告</el-menu-item>
            <el-menu-item index="system.syslog">操作日志</el-menu-item>
            <el-menu-item index="system.sysconfig">参数配置</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="basicData-group">
            <template #title>基础数据</template>
            <el-menu-item index="basicData.law">法律法规</el-menu-item>
            <el-menu-item index="basicData.standard">标准规范</el-menu-item>
            <el-menu-item index="basicData.region">地区字典</el-menu-item>
            <el-menu-item index="basicData.device">设备管理</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="hazardFactor-group">
            <template #title>危害因素管理</template>
            <el-menu-item index="hazardFactor.factor">危害因素库</el-menu-item>
            <el-menu-item index="hazardFactor.sds">SDS说明书</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="configCenter-group">
            <template #title>配置中心</template>
            <el-menu-item index="configCenter.detectMethod">检测方法</el-menu-item>
            <el-menu-item index="configCenter.detectType">检测分类</el-menu-item>
            <el-menu-item index="configCenter.orgConfig">机构配置</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="labManage-group">
            <template #title>实验管理</template>
            <el-menu-item index="labManage.task">检测任务单</el-menu-item>
            <el-menu-item index="labManage.result">检测结果计算</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </aside>

      <div class="admin-content">
    <section v-if="activeTab === 'project'" class="project-workbench" :data-child-menu="projectChildMenu">
      <section class="card project-select-card">
        <h2>项目选择</h2>
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
        <p class="small">共 {{ totalProjects }} 条，当前：{{ selectedProject?.projectCode || '未选择项目' }}</p>
        <p v-if="selectedProject" class="current-project">
          当前项目：<strong>{{ selectedProject.projectCode }}</strong>
          <span>状态：{{ statusText[selectedProject.status] }}</span>
          <span v-if="lastManualSaveAt">最近保存：{{ lastManualSaveAt }}</span>
        </p>
        <el-table
          :data="projects"
          height="260"
          stripe
          border
          highlight-current-row
          :row-key="(row: Project) => row.id"
          @row-click="onProjectRowClick"
        >
          <el-table-column prop="projectCode" label="项目编号" min-width="220" sortable />
          <el-table-column prop="companyName" label="单位名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="projectStatusTagType(row.status as ProjectStatus)" size="small">{{ statusText[row.status as ProjectStatus] }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="card">
        <el-tabs v-model="projectWorkTab" class="work-tabs">
          <el-tab-pane label="项目信息" name="base">
            <el-form label-position="top" class="pro-form">
              <el-row :gutter="14">
                <el-col :xs="24" :md="12">
                  <el-form-item label="受检单位（单位库）">
                    <select v-model="projectForm.companyProfileId" @change="applyCompanyProfile(String(projectForm.companyProfileId || ''))">
                      <option :value="null">请选择单位</option>
                      <option v-for="item in companyProfiles" :key="item.id" :value="item.id">{{ item.companyName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="检测类别（字典）">
                    <select v-model="projectForm.detectTypeDictItemId" @change="applyDetectTypeDict(String(projectForm.detectTypeDictItemId || ''))">
                      <option :value="null">请选择检测类别</option>
                      <option v-for="item in detectTypeItems" :key="item.id" :value="item.id">{{ item.itemName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="单位地址">
                    <input v-model="projectForm.companyAddress" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="采样日期（默认当天）">
                    <input v-model="projectForm.samplingDate" type="date" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="报告日期（默认当天）">
                    <input v-model="projectForm.reportDate" type="date" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="样品名称">
                    <input v-model="projectForm.sampleName" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="送检单位">
                    <select
                      v-model="projectForm.submitUnitCompanyId"
                      @change="applyProjectUnitSelection('submitUnit', String(projectForm.submitUnitCompanyId || ''))"
                    >
                      <option :value="null">请选择送检单位</option>
                      <option v-for="item in companyProfiles" :key="`submit-${item.id}`" :value="item.id">{{ item.companyName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="委托单位">
                    <select
                      v-model="projectForm.entrustUnitCompanyId"
                      @change="applyProjectUnitSelection('entrustUnit', String(projectForm.entrustUnitCompanyId || ''))"
                    >
                      <option :value="null">请选择委托单位</option>
                      <option v-for="item in companyProfiles" :key="`entrust-${item.id}`" :value="item.id">{{ item.companyName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item label="声明日期（默认报告日期）">
                    <input v-model="projectForm.statementDate" type="date" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24">
                  <el-form-item label="备注">
                    <input v-model="projectForm.notes" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="报告补充" name="supplement">
            <el-form label-position="top" class="pro-form">
              <el-row :gutter="14">
                <el-col :xs="24" :md="8">
                  <el-form-item label="编写人（人员资质）">
                    <select v-model="projectForm.writerStaffId" @change="applyStaffSelection('writer', String(projectForm.writerStaffId || ''))">
                      <option :value="null">请选择编写人</option>
                      <option v-for="item in staffProfiles" :key="item.id" :value="item.id">{{ item.staffName }} / {{ item.certNo }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="8">
                  <el-form-item label="审核人（人员资质）">
                    <select v-model="projectForm.reviewerStaffId" @change="applyStaffSelection('reviewer', String(projectForm.reviewerStaffId || ''))">
                      <option :value="null">请选择审核人</option>
                      <option v-for="item in staffProfiles" :key="item.id" :value="item.id">{{ item.staffName }} / {{ item.certNo }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="8">
                  <el-form-item label="签发人（人员资质）">
                    <select v-model="projectForm.approverStaffId" @change="applyStaffSelection('approver', String(projectForm.approverStaffId || ''))">
                      <option :value="null">请选择签发人</option>
                      <option v-for="item in staffProfiles" :key="item.id" :value="item.id">{{ item.staffName }} / {{ item.certNo }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="8"><el-form-item label="编写人证书编号"><input v-model="projectForm.writerCertNo" /></el-form-item></el-col>
                <el-col :xs="24" :md="8"><el-form-item label="审核人证书编号"><input v-model="projectForm.reviewerCertNo" /></el-form-item></el-col>
                <el-col :xs="24" :md="8"><el-form-item label="签发人证书编号"><input v-model="projectForm.approverCertNo" /></el-form-item></el-col>
                <el-col :xs="24">
                  <el-form-item label="任务由来模板">
                    <select v-model="projectForm.taskOriginTemplateId" @change="applyTaskOriginTemplate(String(projectForm.taskOriginTemplateId || ''))">
                      <option :value="null">请选择任务由来模板</option>
                      <option v-for="item in taskOriginTemplates" :key="item.id" :value="item.id">{{ item.templateName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24"><el-form-item label="任务由来内容"><textarea v-model="projectForm.taskOrigin" rows="4" /></el-form-item></el-col>
                <el-col :xs="24">
                  <el-form-item label="评价标准模板">
                    <select v-model="projectForm.evaluationBasisTemplateId" @change="applyEvaluationBasisTemplate(String(projectForm.evaluationBasisTemplateId || ''))">
                      <option :value="null">请选择评价标准模板</option>
                      <option v-for="item in evaluationBasisTemplates" :key="item.id" :value="item.id">{{ item.templateName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24"><el-form-item label="评价标准内容"><textarea v-model="projectForm.evaluationBasis" rows="4" /></el-form-item></el-col>
              </el-row>
            </el-form>
          <p class="small">提示：优先通过主数据下拉选择，选中后自动回填文本与证书编号。</p>
          </el-tab-pane>

          <el-tab-pane label="岗位信息" name="position">
            <div v-if="selectedProject">
              <el-form label-position="top" class="pro-form">
                <el-row :gutter="14">
                  <el-col :xs="24" :md="8">
                    <el-form-item label="岗位模板">
                      <select v-model="positionForm.positionTemplateId" @change="applyPositionTemplate(String(positionForm.positionTemplateId || ''))">
                        <option :value="null">请选择岗位模板</option>
                        <option v-for="item in positionTemplates" :key="item.id" :value="item.id">{{ item.jobName }}</option>
                      </select>
                    </el-form-item>
                  </el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="岗位"><input v-model="positionForm.jobName" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="班制"><input v-model="positionForm.shiftSystem" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="工时"><input v-model="positionForm.workingHours" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="人数"><input v-model.number="positionForm.workerCount" type="number" min="0" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="地点"><input v-model="positionForm.workLocation" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="作业类型"><input v-model="positionForm.operationType" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="作业内容"><input v-model="positionForm.operationContent" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="作业时间"><input v-model="positionForm.operationDuration" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="危害因素"><input v-model="positionForm.hazards" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="防护用品"><input v-model="positionForm.ppe" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="防护设施"><input v-model="positionForm.protectionFacilities" /></el-form-item></el-col>
                </el-row>
              </el-form>
              <div class="actions">
                <button :disabled="loading || actionBusy.addPosition" @click="addPosition">{{ actionBusy.addPosition ? '添加中...' : '添加岗位' }}</button>
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
            </div>
            <p v-else class="small">请先在上方选择或创建项目。</p>
          </el-tab-pane>

          <el-tab-pane label="检测结果" name="result">
            <div v-if="selectedProject">
              <el-form label-position="top" class="pro-form">
                <el-row :gutter="14">
                  <el-col :xs="24" :md="8">
                    <el-form-item label="危害因素模板">
                      <select v-model="resultForm.factorTemplateId" @change="applyFactorTemplate(String(resultForm.factorTemplateId || ''))">
                        <option :value="null">请选择危害因素模板</option>
                        <option v-for="item in factorTemplates" :key="item.id" :value="item.id">
                          {{ item.factorName }} / {{ item.metricType }}
                        </option>
                      </select>
                    </el-form-item>
                  </el-col>
                  <el-col :xs="24" :md="8">
                    <el-form-item label="岗位（从项目岗位选择）">
                      <select v-model="resultForm.positionId" @change="applyResultPosition(String(resultForm.positionId || ''))">
                        <option :value="null">请选择岗位</option>
                        <option v-for="item in positions" :key="item.id" :value="item.id">{{ item.jobName }}</option>
                      </select>
                    </el-form-item>
                  </el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="岗位名称"><input v-model="resultForm.jobName" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="危害因素"><input v-model="resultForm.factorName" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="指标类型"><input v-model="resultForm.metricType" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="原始值"><input v-model="resultForm.valueRaw" placeholder="如 <0.04" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="数值"><input v-model="resultForm.valueNum" type="number" step="0.001" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="单位"><input v-model="resultForm.unit" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="限值"><input v-model="resultForm.limitValue" type="number" step="0.001" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="限值单位"><input v-model="resultForm.limitUnit" /></el-form-item></el-col>
                </el-row>
              </el-form>
          <div class="actions wrap">
            <button :disabled="loading || actionBusy.addResult" @click="addResult">{{ actionBusy.addResult ? '添加中...' : '添加结果' }}</button>
            <button class="ghost" :disabled="loading || actionBusy.judgeProject" @click="judgeProject">
              {{ actionBusy.judgeProject ? '判定中...' : '自动判定' }}
            </button>
            <button class="ghost" :disabled="loading || actionBusy.precheckProject" @click="precheckCurrentProject">
              {{ actionBusy.precheckProject ? '预检中...' : '报告预检' }}
            </button>
            <button class="ghost" :disabled="loading || actionBusy.previewReport" @click="previewReport">
              {{ actionBusy.previewReport ? '预览加载中...' : '报告预览' }}
            </button>
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
            </div>
            <p v-else class="small">请先在上方选择或创建项目。</p>
          </el-tab-pane>

          <el-tab-pane label="报告管理" name="report">
            <div v-if="selectedProject">
              <section class="card" v-if="showPreviewPanel && reportPreview">
                <h2>报告预览（生成前）</h2>
                <p>项目：{{ reportPreview.project.projectCode }} / {{ reportPreview.project.companyName }}</p>
                <p>样品名称：{{ reportPreview.project.sampleName || '-' }}；检测类别：{{ reportPreview.project.detectType || '-' }}</p>
                <p>送检单位：{{ reportPreview.project.submitUnit || '-' }}；委托单位：{{ reportPreview.project.entrustUnit || '-' }}</p>
                <p>声明日期：{{ reportPreview.project.statementDate || '-' }}</p>
                <p>岗位数：{{ reportPreview.positions.length }}；结果数：{{ reportPreview.results.length }}</p>
                <p>预检：<strong>{{ reportPreview.precheck.passed ? '通过' : '未通过' }}</strong></p>
              </section>
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
                    <td>
                      <button class="ghost tiny" :disabled="loading || actionBusy.downloadReport" @click="downloadReport(item.id, item.fileName)">
                        {{ actionBusy.downloadReport ? '下载中...' : '下载' }}
                      </button>
                    </td>
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
            </div>
            <p v-else class="small">请先在上方选择或创建项目。</p>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section class="card floating-actions">
        <div class="actions wrap">
          <button :disabled="loading || actionBusy.saveProject" @click="createProject">{{ actionBusy.saveProject ? '创建中...' : '创建项目' }}</button>
          <button class="primary-strong" :disabled="!selectedProject || loading || actionBusy.saveProject" @click="updateProject()">
            {{ actionBusy.saveProject ? '保存中...' : '手动保存' }}
          </button>
          <button class="ghost" :disabled="loading || actionBusy.saveProject" @click="startNewDraft">清空表单</button>
          <button class="ghost" :disabled="!selectedProject || loading || actionBusy.copyProject" @click="copyProject">
            {{ actionBusy.copyProject ? '复制中...' : '复制项目' }}
          </button>
          <button class="danger" :disabled="!selectedProject || loading || actionBusy.deleteProject" @click="deleteProject">
            {{ actionBusy.deleteProject ? '删除中...' : '删除项目' }}
          </button>
          <button class="ghost" :disabled="!selectedProject || loading || actionBusy.generateReport" @click="generateReport">
            {{ actionBusy.generateReport ? '生成中...' : '生成报告（覆盖）' }}
          </button>
          <label class="autosave-inline">
            <input v-model="autoSaveEnabled" type="checkbox" />
            自动保存
          </label>
          <label class="autosave-inline">
            <input v-model="clearAfterCreate" type="checkbox" />
            创建后清空表单
          </label>
          <label class="autosave-inline">
            间隔(秒)
            <input v-model.number="autoSaveIntervalSec" type="number" min="10" max="600" :disabled="!autoSaveEnabled" />
          </label>
          <span class="hint">{{ autoSaveEnabled ? `自动保存已开启：每 ${Math.max(10, Number(autoSaveIntervalSec) || 30)} 秒` : '自动保存已关闭' }}</span>
        </div>
      </section>
    </section>

    <section v-else-if="activeTab === 'maintenance'" class="maintenance-grid">
      <section class="card" v-if="maintenanceChildMenu === 'company'">
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

      <section class="card" v-else-if="maintenanceChildMenu === 'position'">
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

      <section class="card" v-else-if="maintenanceChildMenu === 'factor'">
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

      <section class="card" v-else-if="maintenanceChildMenu === 'dict'">
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

      <section class="card" v-else-if="maintenanceChildMenu === 'staff'">
        <h2>人员资质维护</h2>
        <div class="grid2">
          <label>姓名<input v-model="staffProfileForm.staffName" /></label>
          <label>职务/职称<input v-model="staffProfileForm.staffTitle" /></label>
          <label>证书编号<input v-model="staffProfileForm.certNo" /></label>
          <label>联系电话<input v-model="staffProfileForm.contactPhone" /></label>
          <label class="full">备注<input v-model="staffProfileForm.notes" /></label>
        </div>
        <div class="actions">
          <button @click="saveStaffProfile">{{ staffProfileForm.id ? '更新人员' : '新增人员' }}</button>
          <button class="ghost" @click="clearStaffProfileForm">清空</button>
          <button class="ghost" @click="fetchStaffProfiles">刷新</button>
        </div>
        <table>
          <thead><tr><th>姓名</th><th>职务/职称</th><th>证书编号</th><th>电话</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in staffProfiles" :key="item.id">
              <td>{{ item.staffName }}</td>
              <td>{{ item.staffTitle || '-' }}</td>
              <td>{{ item.certNo }}</td>
              <td>{{ item.contactPhone || '-' }}</td>
              <td>
                <button class="ghost tiny" @click="editStaffProfile(item)">编辑</button>
                <button class="danger tiny" @click="deleteStaffProfile(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section class="card" v-else>
        <h2>报告文本模板维护</h2>
        <div class="grid2">
          <label>
            模板分类
            <select v-model="reportTextTypeQuery">
              <option value="TASK_ORIGIN">任务由来</option>
              <option value="EVALUATION_BASIS">评价标准</option>
            </select>
          </label>
          <label>
            启用
            <select v-model="reportTextTemplateForm.enabled">
              <option :value="true">是</option>
              <option :value="false">否</option>
            </select>
          </label>
          <label>模板名称<input v-model="reportTextTemplateForm.templateName" /></label>
          <label class="full">模板内容<textarea v-model="reportTextTemplateForm.templateContent" rows="5" /></label>
        </div>
        <div class="actions">
          <button @click="saveReportTextTemplate">{{ reportTextTemplateForm.id ? '更新模板' : '新增模板' }}</button>
          <button class="ghost" @click="clearReportTextTemplateForm">清空</button>
          <button class="ghost" @click="fetchReportTextTemplates">刷新</button>
        </div>
        <table>
          <thead><tr><th>分类</th><th>名称</th><th>启用</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in reportTextTemplates" :key="item.id">
              <td>{{ item.templateType }}</td>
              <td>{{ item.templateName }}</td>
              <td>{{ item.enabled ? '是' : '否' }}</td>
              <td>
                <button class="ghost tiny" @click="editReportTextTemplate(item)">编辑</button>
                <button class="danger tiny" @click="deleteReportTextTemplate(item.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>
    </section>

      <!-- 系统管理 -->
      <section v-else-if="activeTab === 'system'" class="maintenance-grid">
        <section class="card" v-if="systemChildMenu === 'user'">
          <h2>用户管理</h2>
          <p class="small">用户列表（功能待实现）</p>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'role'">
          <h2>角色管理</h2>
          <p class="small">角色列表（功能待实现）</p>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'menu'">
          <h2>菜单管理</h2>
          <p class="small">菜单列表（功能待实现）</p>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'permission'">
          <h2>权限管理</h2>
          <p class="small">权限列表（功能待实现）</p>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'dept'">
          <h2>部门管理</h2>
          <div class="grid2">
            <label>部门名称<input v-model="deptForm.deptName" /></label>
            <label>部门代码<input v-model="deptForm.deptCode" /></label>
          </div>
          <div class="actions"><button @click="saveDept">{{ deptForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchSysDepts">刷新</button></div>
          <table><thead><tr><th>部门名称</th><th>代码</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in sysDepts" :key="item.id"><td>{{ item.deptName }}</td><td>{{ item.deptCode }}</td><td><button class="ghost tiny" @click="editDept(item)">编辑</button><button class="danger tiny" @click="deleteDept(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'post'">
          <h2>岗位管理</h2>
          <div class="grid2">
            <label>岗位名称<input v-model="postForm.postName" /></label>
            <label>岗位代码<input v-model="postForm.postCode" /></label>
          </div>
          <div class="actions"><button @click="savePost">{{ postForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchSysPosts">刷新</button></div>
          <table><thead><tr><th>岗位名称</th><th>代码</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in sysPosts" :key="item.id"><td>{{ item.postName }}</td><td>{{ item.postCode }}</td><td><button class="ghost tiny" @click="editPost(item)">编辑</button><button class="danger tiny" @click="deletePost(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'notice'">
          <h2>通知公告</h2>
          <div class="grid2">
            <label>标题<input v-model="noticeForm.title" /></label>
            <label>类型<select v-model="noticeForm.noticeType"><option value="NOTICE">通知</option><option value="ANNOUNCEMENT">公告</option></select></label>
            <label>发布人<input v-model="noticeForm.publisher" /></label>
            <label class="full">内容<textarea v-model="noticeForm.content" rows="3" style="width:100%"></textarea></label>
          </div>
          <div class="actions"><button @click="saveNotice">{{ noticeForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchSysNotices">刷新</button></div>
          <table><thead><tr><th>标题</th><th>类型</th><th>发布人</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in sysNotices" :key="item.id"><td>{{ item.title }}</td><td>{{ item.noticeType }}</td><td>{{ item.publisher }}</td><td><button class="danger tiny" @click="deleteNotice(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'syslog'">
          <h2>操作日志</h2>
          <div class="actions"><button class="ghost" @click="fetchSysLogs">刷新</button></div>
          <table><thead><tr><th>用户</th><th>操作</th><th>模块</th><th>结果</th><th>时间</th></tr></thead>
            <tbody><tr v-for="item in sysLogs" :key="item.id"><td>{{ item.userName }}</td><td>{{ item.operation }}</td><td>{{ item.module }}</td><td>{{ item.resultType }}</td><td>{{ item.createdAt }}</td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'sysconfig'">
          <h2>参数配置</h2>
          <div class="grid2">
            <label>参数键<input v-model="sysConfigForm.configKey" /></label>
            <label>参数值<input v-model="sysConfigForm.configValue" /></label>
            <label class="full">参数名称<input v-model="sysConfigForm.configName" /></label>
          </div>
          <div class="actions"><button @click="saveSysConfig">{{ sysConfigForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchSysConfigs">刷新</button></div>
          <table><thead><tr><th>参数键</th><th>参数值</th><th>名称</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in sysConfigs" :key="item.id"><td>{{ item.configKey }}</td><td>{{ item.configValue }}</td><td>{{ item.configName }}</td><td><button class="ghost tiny" @click="editSysConfig(item)">编辑</button><button class="danger tiny" @click="deleteSysConfig(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
      </section>

      <!-- 基础数据 -->
      <section v-else-if="activeTab === 'basicData'" class="maintenance-grid">
        <section class="card" v-if="basicDataChildMenu === 'law'">
          <h2>法律法规</h2>
          <div class="grid2">
            <label>标题<input v-model="lawForm.title" /></label>
            <label>文号<input v-model="lawForm.docNo" /></label>
            <label>级别
              <select v-model="lawForm.level"><option value="NATIONAL">国家</option><option value="PROVINCIAL">省级</option><option value="LOCAL">地方</option></select>
            </label>
            <label>发布日期<input v-model="lawForm.publishDate" type="date" /></label>
            <label class="full">备注<input v-model="lawForm.notes" /></label>
          </div>
          <div class="actions"><button @click="saveLaw">{{ lawForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchLaws">刷新</button></div>
          <table><thead><tr><th>标题</th><th>文号</th><th>级别</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in laws" :key="item.id"><td>{{ item.title }}</td><td>{{ item.docNo }}</td><td>{{ item.level }}</td><td><button class="ghost tiny" @click="editLaw(item)">编辑</button><button class="danger tiny" @click="deleteLaw(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'standard'">
          <h2>标准规范</h2>
          <div class="grid2">
            <label>标准名称<input v-model="standardForm.standardName" /></label>
            <label>标准编号<input v-model="standardForm.standardCode" /></label>
            <label>级别<select v-model="standardForm.level"><option value="NATIONAL">国标</option><option value="INDUSTRY">行标</option></select></label>
            <label class="full">备注<input v-model="standardForm.notes" /></label>
          </div>
          <div class="actions"><button @click="saveStandard">{{ standardForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchStandards">刷新</button></div>
          <table><thead><tr><th>标准名称</th><th>编号</th><th>级别</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in standards" :key="item.id"><td>{{ item.standardName }}</td><td>{{ item.standardCode }}</td><td>{{ item.level }}</td><td><button class="ghost tiny" @click="editStandard(item)">编辑</button><button class="danger tiny" @click="deleteStandard(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'region'">
          <h2>地区字典</h2>
          <div class="grid2">
            <label>区域名称<input v-model="regionForm.regionName" /></label>
            <label>区域代码<input v-model="regionForm.regionCode" /></label>
            <label>级别<select v-model="regionForm.level"><option :value="1">省级</option><option :value="2">市级</option><option :value="3">区县</option></select></label>
            <label>排序<input v-model.number="regionForm.sortNo" type="number" /></label>
          </div>
          <div class="actions"><button @click="saveRegion">{{ regionForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchRegions">刷新</button></div>
          <table><thead><tr><th>名称</th><th>代码</th><th>级别</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in regions" :key="item.id"><td>{{ item.regionName }}</td><td>{{ item.regionCode }}</td><td>{{ item.level }}</td><td><button class="ghost tiny" @click="editRegion(item)">编辑</button><button class="danger tiny" @click="deleteRegion(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'device'">
          <h2>设备管理</h2>
          <div class="grid2">
            <label>设备名称<input v-model="deviceForm.deviceName" /></label>
            <label>类型<select v-model="deviceForm.deviceType"><option value="LAB_INSTRUMENT">实验仪器</option><option value="SAMPLER">采样仪器</option></select></label>
            <label>型号<input v-model="deviceForm.deviceModel" /></label>
            <label>设备编号<input v-model="deviceForm.deviceNo" /></label>
            <label>状态<select v-model="deviceForm.status"><option value="NORMAL">正常</option><option value="MAINTENANCE">维修中</option><option value="SCRAPPED">报废</option></select></label>
            <label class="full">备注<input v-model="deviceForm.notes" /></label>
          </div>
          <div class="actions"><button @click="saveDevice">{{ deviceForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchDevices">刷新</button></div>
          <table><thead><tr><th>名称</th><th>型号</th><th>类型</th><th>状态</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in devices" :key="item.id"><td>{{ item.deviceName }}</td><td>{{ item.deviceModel }}</td><td>{{ item.deviceType === 'LAB_INSTRUMENT' ? '实验仪器' : '采样仪器' }}</td><td>{{ item.status }}</td><td><button class="ghost tiny" @click="editDevice(item)">编辑</button><button class="danger tiny" @click="deleteDevice(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
      </section>

      <!-- 危害因素管理 -->
      <section v-else-if="activeTab === 'hazardFactor'" class="maintenance-grid">
        <section class="card" v-if="hazardChildMenu === 'factor'">
          <h2>危害因素库</h2>
          <div class="grid2">
            <label>因素名称<input v-model="hazardFactorForm.factorName" /></label>
            <label>分类<select v-model="hazardFactorForm.factorCategory"><option value="PHYSICAL">物理因素</option><option value="CHEMICAL">化学因素</option><option value="DUST">粉尘</option><option value="BIOLOGICAL">生物因素</option><option value="RADIATION">放射因素</option><option value="OTHER">其他</option></select></label>
            <label>因素编码<input v-model="hazardFactorForm.factorCode" /></label>
            <label>CAS号<input v-model="hazardFactorForm.casNo" /></label>
          </div>
          <div class="actions"><button @click="saveHazardFactor">{{ hazardFactorForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchHazardFactors">刷新</button></div>
          <table><thead><tr><th>因素名称</th><th>分类</th><th>CAS号</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in hazardFactors" :key="item.id"><td>{{ item.factorName }}</td><td>{{ item.factorCategory }}</td><td>{{ item.casNo }}</td><td><button class="ghost tiny" @click="editHazardFactor(item)">编辑</button><button class="danger tiny" @click="deleteHazardFactor(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="hazardChildMenu === 'sds'">
          <h2>SDS安全技术说明书</h2>
          <div class="grid2">
            <label>物质名称<input v-model="sdsForm.substanceName" /></label>
            <label>CAS号<input v-model="sdsForm.casNo" /></label>
            <label>版本<input v-model="sdsForm.version" /></label>
            <label class="full">备注<input v-model="sdsForm.notes" /></label>
          </div>
          <div class="actions"><button @click="saveSds">{{ sdsForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchSdsList">刷新</button></div>
          <table><thead><tr><th>物质名称</th><th>CAS号</th><th>版本</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in sdsList" :key="item.id"><td>{{ item.substanceName }}</td><td>{{ item.casNo }}</td><td>{{ item.version }}</td><td><button class="ghost tiny" @click="editSds(item)">编辑</button><button class="danger tiny" @click="deleteSds(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
      </section>

      <!-- 配置中心 -->
      <section v-else-if="activeTab === 'configCenter'" class="maintenance-grid">
        <section class="card" v-if="configChildMenu === 'detectMethod'">
          <h2>检测方法配置</h2>
          <div class="grid2">
            <label>方法名称<input v-model="detectionMethodForm.methodName" /></label>
            <label>方法代码<input v-model="detectionMethodForm.methodCode" /></label>
            <label>简称<input v-model="detectionMethodForm.methodAbbr" /></label>
            <label>计算公式<input v-model="detectionMethodForm.formula" /></label>
          </div>
          <div class="actions"><button @click="saveDetectionMethod">{{ detectionMethodForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchDetectionMethods">刷新</button></div>
          <table><thead><tr><th>名称</th><th>代码</th><th>简称</th><th>公式</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in detectionMethods" :key="item.id"><td>{{ item.methodName }}</td><td>{{ item.methodCode }}</td><td>{{ item.methodAbbr }}</td><td>{{ item.formula }}</td><td><button class="ghost tiny" @click="editDetectionMethod(item)">编辑</button><button class="danger tiny" @click="deleteDetectionMethod(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="configChildMenu === 'detectType'">
          <h2>检测分类配置</h2>
          <div class="grid2">
            <label>分类名称<input v-model="detectTypeForm.typeName" /></label>
            <label>分类代码<input v-model="detectTypeForm.typeCode" /></label>
            <label>简称<input v-model="detectTypeForm.typeAbbr" /></label>
            <label>排序<input v-model.number="detectTypeForm.sortNo" type="number" /></label>
          </div>
          <div class="actions"><button @click="saveDetectType">{{ detectTypeForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchDetectTypes">刷新</button></div>
          <table><thead><tr><th>名称</th><th>代码</th><th>简称</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in detectTypes" :key="item.id"><td>{{ item.typeName }}</td><td>{{ item.typeCode }}</td><td>{{ item.typeAbbr }}</td><td><button class="ghost tiny" @click="editDetectType(item)">编辑</button><button class="danger tiny" @click="deleteDetectType(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="configChildMenu === 'orgConfig'">
          <h2>机构基础配置</h2>
          <div class="grid2">
            <label>机构名称<input v-model="orgConfigForm.orgName" /></label>
            <label>统一社会信用代码<input v-model="orgConfigForm.orgCode" /></label>
            <label>机构地址<input v-model="orgConfigForm.orgAddress" /></label>
            <label>联系人<input v-model="orgConfigForm.contactName" /></label>
            <label>联系电话<input v-model="orgConfigForm.contactPhone" /></label>
            <label>资质证书编号<input v-model="orgConfigForm.certNo" /></label>
            <label>样品编号前缀<input v-model="orgConfigForm.sampleNoPrefix" /></label>
            <label>报告编号前缀<input v-model="orgConfigForm.reportNoPrefix" /></label>
          </div>
          <div class="actions"><button @click="saveOrgConfig">保存配置</button><button class="ghost" @click="fetchOrgConfig">刷新</button></div>
        </section>
      </section>

      <!-- 实验管理 -->
      <section v-else-if="activeTab === 'labManage'" class="maintenance-grid">
        <section class="card" v-if="labChildMenu === 'task'">
          <h2>内部检测任务单</h2>
          <div class="grid2">
            <label>关联项目ID<input v-model.number="labTaskForm.projectId" type="number" /></label>
            <label>签发人<input v-model="labTaskForm.issuerName" /></label>
            <label class="full">备注<input v-model="labTaskForm.notes" /></label>
          </div>
          <div class="actions"><button @click="createLabTask">创建任务单</button><button class="ghost" @click="fetchLabTasks">刷新</button></div>
          <table><thead><tr><th>任务单号</th><th>项目ID</th><th>状态</th><th>签发人</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in labTasks" :key="item.id">
              <td>{{ item.taskCode }}</td><td>{{ item.projectId }}</td><td>{{ item.taskStatus }}</td><td>{{ item.issuerName }}</td>
              <td>
                <button v-if="item.taskStatus === 'PENDING'" class="ghost tiny" @click="receiveTask(item.id, '当前用户')">领取</button>
                <button v-if="item.taskStatus === 'IN_PROGRESS'" class="ghost tiny" @click="submitTask(item.id)">提交审核</button>
              </td>
            </tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="labChildMenu === 'result'">
          <h2>检测结果计算</h2>
          <div class="actions"><button class="ghost" @click="fetchLabResults">刷新</button></div>
          <table><thead><tr><th>任务单ID</th><th>因素名称</th><th>检测方法</th><th>截距a</th><th>斜率b</th><th>相关系数R</th><th>结果值</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in labResults" :key="item.id">
              <td>{{ item.taskId }}</td><td>{{ item.factorName }}</td><td>{{ item.methodName }}</td>
              <td>{{ item.interceptA }}</td><td>{{ item.slopeB }}</td><td>{{ item.correlationR }}</td><td>{{ item.resultValue }} {{ item.resultUnit }}</td>
              <td><button class="ghost tiny" @click="calcLabResult(item.id)">触发计算</button></td>
            </tr></tbody>
          </table>
        </section>
      </section>
      </div>
    </section>
  </div>
</template>
