<script setup lang="ts">
import axios from 'axios'
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { codeLabel, formatFileSize, regionLevelLabel } from './codeLabels'
import { findHelpAnchor, findHelpTopic, helpTopicByMenuKey, type HelpTopicId } from './helpContent'
import ModuleLoadError from './components/ModuleLoadError.vue'
import HelpMark from './components/help/HelpMark.vue'
import type { AsyncComponentLoader, Component } from 'vue'

function loadView<T extends Component>(loader: AsyncComponentLoader<T>) { return defineAsyncComponent({ loader, errorComponent: ModuleLoadError }) }
const RadiologyWorkbench = loadView(() => import('./components/radiology/RadiologyWorkbench.vue'))
const FeedbackCenter = loadView(() => import('./components/feedback/FeedbackCenter.vue'))
const HelpCenter = loadView(() => import('./components/help/HelpCenter.vue'))
const ManualDownloads = loadView(() => import('./components/help/ManualDownloads.vue'))
const ContextHelpDrawer = loadView(() => import('./components/help/ContextHelpDrawer.vue'))
const ReportSupplementEditor = loadView(() => import('./components/ohdp/ReportSupplementEditor.vue'))

type ProjectStatus = 'DRAFT' | 'DATA_FILLED' | 'JUDGED' | 'REPORT_READY'

type UserRole = 'ADMIN' | 'USER'

interface ReportSupplement {
  reportHeaderName: string
  socialCreditCode: string
  legalRepresentative: string
  registeredAddress: string
  technicalServiceAddress: string
  contactName: string
  contactPhone: string
  industryCategory: string
  commissioningDate: string
  economicType: string
  companyScale: string
  employeeCount: number
  outsourcedWorkerCount: number
  dispatchedWorkerCount: number
  detectionScope: string
  lawsRegulations: string
  basicMaterials: string
  productionProcess: string
  radiationSourceSummary: string
  radiationSourceMode: string
  radiationSeparateReportNo: string
  vocAnalysisConclusion: string
  vocSamplingDate: string
  vocDetectionDate: string
  vocDetectionBasis: string
  vocDetectionMethod: string
  vocTestingOrganization: string
  vocSourceReportNo: string
  mixedExposureAssessment: string
  mixedExposureAssessmentBasis: string
  vocInstrument: string
  vocTotalPeakAreaPercent: string
  vocInorganicPeakAreaPercent: string
  exclusionScreeningConclusion: string
  rawRecordCompletenessNote: string
  qualificationCertificatePlan: string
  qualificationCertificateFileName: string
  qualificationCertificateImageDataUrl: string
  attachmentDeliveryPlan: string
  photoEmbeddingPlan: string
  reportPhotos: Record<string, any>[]
  multiDayDisplayRule: string
  overtimeReductionRule: string
  physicalFactorScope: string
  specialMeasurementRule: string
  signatureStampMode: string
  productionCondition: string
  samplingLayoutDescription: string
  detectionConditions: string
  environmentConditions: string
  onsiteInspectors: string
  planDeviation: string
  riskCategory: string
  majorHazards: string
  overLimitSummary: string
  majorProblems: string
  recommendations: string
  materials: Record<string, any>[]
  vocAnalysisPerformed: boolean
  vocAnalysisItems: Record<string, any>[]
  xrayDevices: Record<string, any>[]
  sealedSources: Record<string, any>[]
  unsealedSources: Record<string, any>[]
  exclusionScreeningPerformed: boolean
  exclusionScreeningItems: Record<string, any>[]
  equipment: Record<string, any>[]
  protectionFacilities: Record<string, any>[]
  personalProtectiveEquipment: Record<string, any>[]
  samplingPlans: Record<string, any>[]
  materialsConfirmedNotApplicable: boolean
  equipmentConfirmedNotApplicable: boolean
  protectionFacilitiesConfirmedNotApplicable: boolean
  ppeConfirmedNotApplicable: boolean
}

function createEmptyReportSupplement(): ReportSupplement {
  return {
    reportHeaderName: '', socialCreditCode: '', legalRepresentative: '', registeredAddress: '', technicalServiceAddress: '',
    contactName: '', contactPhone: '', industryCategory: '', commissioningDate: '', economicType: '',
    companyScale: '', employeeCount: 0, outsourcedWorkerCount: 0, dispatchedWorkerCount: 0,
    detectionScope: '', lawsRegulations: '', basicMaterials: '', productionProcess: '', radiationSourceSummary: '', radiationSourceMode: 'NONE', radiationSeparateReportNo: '',
    vocAnalysisConclusion: '', vocSamplingDate: '', vocDetectionDate: '', vocDetectionBasis: '', vocDetectionMethod: '', vocTestingOrganization: '', vocSourceReportNo: '', mixedExposureAssessment: '', mixedExposureAssessmentBasis: '', vocInstrument: '', vocTotalPeakAreaPercent: '', vocInorganicPeakAreaPercent: '',
    exclusionScreeningConclusion: '', exclusionScreeningPerformed: false, exclusionScreeningItems: [], rawRecordCompletenessNote: '',
    qualificationCertificatePlan: '使用当前有效资质认定证书，嵌入报告第3页。', qualificationCertificateFileName: '', qualificationCertificateImageDataUrl: '',
    attachmentDeliveryPlan: '委托书、合同、现场调查表、检测计划及其他附件不随主报告提供。',
    photoEmbeddingPlan: '现场照片由报告编制者上传并直接嵌入Word。', reportPhotos: [],
    multiDayDisplayRule: '单日保留日期列；两日及以上逐条显示具体检测日期。',
    overtimeReductionRule: '日接触超过8小时或周接触超过40小时时，按确认公式折减PC-TWA。', physicalFactorScope: '',
    specialMeasurementRule: '检测人员按实际选择检测部位和波段；留空时报告不显示。', signatureStampMode: '保留打印后手签盖章位置',
    productionCondition: '', samplingLayoutDescription: '', detectionConditions: '', environmentConditions: '',
    onsiteInspectors: '', planDeviation: '已按现场采样与测量计划开展检测，未对计划进行变更。',
    riskCategory: '', majorHazards: '', overLimitSummary: '', majorProblems: '', recommendations: '',
    materials: [], vocAnalysisPerformed: false, vocAnalysisItems: [], xrayDevices: [], sealedSources: [], unsealedSources: [], equipment: [], protectionFacilities: [], personalProtectiveEquipment: [], samplingPlans: [],
    materialsConfirmedNotApplicable: false, equipmentConfirmedNotApplicable: false,
    protectionFacilitiesConfirmedNotApplicable: false, ppeConfirmedNotApplicable: false,
  }
}

interface LoginResponse {
  token: string
  userId: number
  username: string
  role: UserRole
  mustChangePassword: boolean
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
  reportSupplement?: ReportSupplement
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
  factorCategory?: string
  pointCode?: string
  sampleCode?: string
  detectionMode?: string
  noiseType?: string
  laboratoryDate?: string
  detectionBasis?: string
  detectionMethod?: string
  instrument?: string
  testingOrganization?: string
  sourceRecordNo?: string
  detectionLimit?: string
  quantificationLimit?: string
  evaluationBasis?: string
  overLimitReason?: string
  weeklyWorkDays?: number
  reductionApplicable?: boolean
  peakLimitMultiplier?: number
  peakExposureMinutes?: number
  peakTimesPerShift?: number
  peakIntervalMinutes?: number
  mixedExposureGroup?: string
  mixedExposureBasis?: string
  measurementConditions?: string
  measurementDate?: string
  measurementBodyPart?: string
  measurementBand?: string
  dailyExposureHours?: number
  weeklyExposureHours?: number
  reductionFactor?: number
  adjustedLimitValue?: number
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
  operationTypeLabel?: string
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
  detectionBasis?: string
  detectionMethod?: string
  detectionInstruments?: string
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
  profession?: string
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

interface SysUser {
  id: number
  username: string
  employeeId?: number
  role: 'ADMIN' | 'USER'
  status: 'ACTIVE' | 'INACTIVE'
  mustChangePassword?: boolean
  createdAt?: string
}

interface SysRole {
  id: number
  roleCode: string
  roleName: string
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
}

interface SysMenu {
  id: number
  permCode: string
  permName: string
  parentId?: number | null
  sortNo?: number
  status: 'ACTIVE' | 'INACTIVE'
  children?: SysMenu[]
}

interface SysPermission {
  id: number
  permCode: string
  permName: string
  permType: 'MENU' | 'BUTTON' | 'API'
  apiMethod?: string
  apiPath?: string
  parentId?: number | null
  sortNo?: number
  status: 'ACTIVE' | 'INACTIVE'
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
})

// ===== 新模块状态 =====
const basicDataChildMenu = ref<'law' | 'standard' | 'region' | 'device'>('law')
const hazardChildMenu = ref<'factor' | 'sds'>('factor')
const configChildMenu = ref<'detectMethod' | 'orgConfig' | 'detectType'>('detectMethod')
const labChildMenu = ref<'task' | 'result'>('task')
const radiologyChildMenu = ref<'DR' | 'CT'>('DR')

interface ReferenceDocument { id: number; referenceType: 'LAW' | 'STANDARD'; referenceId: number; versionNo: number; versionLabel?: string; originalName: string; contentType: string; fileSize: number; sha256: string; sourceUrl?: string; uploadedBy?: string; uploadedAt: string; current?: boolean }
interface OhdpLaw { id: number; title: string; docNo?: string; level?: string; levelLabel?: string; publishDate?: string; effectiveDate?: string; publisher?: string; isValid?: boolean; isOpen?: boolean; fileId?: number; currentDocument?: ReferenceDocument; notes?: string }
interface OhdpStandard { id: number; standardName: string; standardCode?: string; level?: string; levelLabel?: string; useScope?: string; publishDate?: string; isValid?: boolean; isOpen?: boolean; fileId?: number; currentDocument?: ReferenceDocument; notes?: string }
interface OhdpRegion { id: number; parentId?: number; regionCode: string; regionName: string; level: number; levelLabel?: string; sortNo?: number }
interface OhdpDevice { id: number; deviceName: string; deviceType: string; deviceTypeLabel?: string; deviceModel?: string; deviceNo?: string; status?: string; statusLabel?: string; calibrationExpiry?: string; calibrationDate?: string; certificateNo?: string; notes?: string }
interface OhdpHazardFactor { id: number; factorName: string; factorCategory: string; factorCategoryLabel?: string; factorCode?: string; casNo?: string; enabled?: boolean }
interface OhdpHazardFactorLimit { id: number; factorId: number; limitType: string; limitTypeLabel?: string; limitValue?: number; limitUnit?: string; standardCode?: string; notes?: string }
interface OhdpSds { id: number; substanceName: string; casNo?: string; factorId?: number; version?: string; notes?: string }
interface OhdpDetectionMethod { id: number; methodName: string; methodCode?: string; methodAbbr?: string; formula?: string; standardCode?: string; enabled?: boolean }
interface OhdpDetectType { id: number; typeName: string; typeCode: string; typeAbbr?: string; enabled?: boolean }
interface OhdpOrgConfig { orgName?: string; orgCode?: string; orgAddress?: string; contactName?: string; contactPhone?: string; certNo?: string; sampleNoPrefix?: string; reportNoPrefix?: string }
interface OhdpLabTask { id: number; taskCode: string; projectId: number; taskStatus: string; taskStatusLabel?: string; issuerName?: string; receiverName?: string; notes?: string }
interface OhdpLabResult { id: number; taskId: number; factorName: string; methodName?: string; interceptA?: number; slopeB?: number; correlationR?: number; resultValue?: number; resultUnit?: string; analystName?: string }
interface SysDept { id: number; deptName: string; parentId?: number; deptCode?: string; enabled?: boolean }
interface SysPost { id: number; postName: string; postCode?: string; enabled?: boolean }
interface SysNotice { id: number; title: string; noticeType?: string; publisher?: string; enabled?: boolean }
interface SysSysLog { id: number; userName?: string; operation?: string; resultType?: string; module?: string; createdAt?: string }
interface SysConfig { id: number; configKey: string; configValue?: string; configName?: string }
// ===== END 新模块接口 =====

function readSessionValue(key: string) {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

interface ReportCenterRow {
  projectId: number
  projectCode: string
  companyName: string
  projectStatus: ProjectStatus
  projectStatusLabel?: string
  reportStatus: 'GENERATED' | 'PENDING'
  reportStatusLabel?: string
  reportId?: number
  fileName?: string
  generatedBy?: string
  generatedAt?: string
  projectCreatedAt?: string
}

function clearStoredSession() {
  for (const key of ['ohdp_token', 'ohdp_username', 'ohdp_role']) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

const token = ref(readSessionValue('ohdp_token'))
const currentUsername = ref(readSessionValue('ohdp_username'))
const currentRole = ref<UserRole>((readSessionValue('ohdp_role') as UserRole) || 'USER')
const permissionCodes = ref<Set<string>>(new Set())
const loading = ref(false)
const message = ref('')
const messageType = ref<'info' | 'success' | 'error'>('info')
const latestAction = ref('')
const actionBusy = reactive({
  saveProject: false,
  copyProject: false,
  deleteProject: false,
  cancelCreate: false,
  addPosition: false,
  addResult: false,
  judgeProject: false,
  precheckProject: false,
  previewReport: false,
  generateReport: false,
  downloadReport: false,
  reportCenter: false,
  uploadReferenceDocument: false,
})
const activeTab = ref<'project' | 'reportCenter' | 'maintenance' | 'system' | 'basicData' | 'hazardFactor' | 'configCenter' | 'labManage' | 'radiology' | 'feedback' | 'help'>('project')
const projectChildMenu = ref<'workspace' | 'report'>('workspace')
const projectWorkTab = ref<'base' | 'supplement' | 'position' | 'result'>('base')
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
  | 'radiology' | 'radiology.dr' | 'radiology.ct'
  | 'feedback'
  | 'help'
const selectedMenuKey = ref<MenuKey>('project.workspace')
const helpTopicId = ref<HelpTopicId>('overview')
const helpReturnKey = ref<MenuKey>('project.workspace')
const helpActiveAnchorId = ref<string | null>(null)
const contextHelpAnchorId = ref<string | null>(null)
const standaloneHelpPage = ref(new URL(window.location.href).searchParams.get('view') === 'help')
const sideMenuRenderKey = ref(0)
const showPreviewPanel = ref(false)
const feedbackSourceModule = ref('项目工作台')

const menuMeta = computed(() => {
  const entries: Record<MenuKey, { group: string; title: string; description: string }> = {
    project: { group: '项目与报告', title: '项目工作台', description: '建档、数据录入、判定与报告归档' },
    'project.workspace': { group: '项目与报告', title: '项目工作台', description: '建档、数据录入、判定与报告归档' },
    'project.report': { group: '项目与报告', title: '报告管理', description: '跨项目管理报告生成、预览、下载与操作留痕' },
    maintenance: { group: '单位与人员', title: '受检单位', description: '维护受检单位和业务联系人' },
    'maintenance.company': { group: '单位与人员', title: '受检单位', description: '维护受检单位和业务联系人' },
    'maintenance.position': { group: '业务规则与模板', title: '岗位模板', description: '复用岗位、班制和防护信息' },
    'maintenance.factor': { group: '业务规则与模板', title: '岗位危害因素模板', description: '维护检测指标、单位和默认限值' },
    'maintenance.dict': { group: '业务规则与模板', title: '业务字典', description: '统一检测类别与枚举数据' },
    'maintenance.staff': { group: '单位与人员', title: '人员资质', description: '维护编写、审核与签发人员资质' },
    'maintenance.reportText': { group: '业务规则与模板', title: '报告文本模板', description: '维护任务由来和评价依据文本' },
    system: { group: '系统管理', title: '用户管理', description: '管理系统账号和访问状态' },
    'system.user': { group: '系统管理', title: '用户管理', description: '管理系统账号和访问状态' },
    'system.role': { group: '系统管理', title: '角色管理', description: '配置角色与授权范围' },
    'system.menu': { group: '系统管理', title: '菜单管理', description: '维护系统功能菜单结构' },
    'system.permission': { group: '系统管理', title: '权限管理', description: '维护细粒度功能权限' },
    'system.dept': { group: '系统管理', title: '部门管理', description: '维护组织部门信息' },
    'system.post': { group: '系统管理', title: '岗位管理', description: '维护组织岗位信息' },
    'system.notice': { group: '系统管理', title: '通知公告', description: '发布内部通知和业务公告' },
    'system.syslog': { group: '系统管理', title: '操作日志', description: '追踪关键业务与系统操作' },
    'system.sysfile': { group: '系统管理', title: '文件管理', description: '管理系统上传文件' },
    'system.sysconfig': { group: '系统管理', title: '参数配置', description: '维护系统运行参数' },
    basicData: { group: '检测基础库', title: '法律法规', description: '维护业务引用的法规文件' },
    'basicData.law': { group: '检测基础库', title: '法律法规', description: '维护业务引用的法规文件' },
    'basicData.standard': { group: '检测基础库', title: '标准规范', description: '维护检测和评价标准' },
    'basicData.region': { group: '检测基础库', title: '行政区划', description: '维护行政区域基础数据' },
    'basicData.device': { group: '检测基础库', title: '检测仪器设备', description: '维护采样与实验仪器台账' },
    hazardFactor: { group: '检测基础库', title: '危害因素库', description: '维护因素档案与职业接触限值' },
    'hazardFactor.factor': { group: '检测基础库', title: '危害因素库', description: '维护因素档案与职业接触限值' },
    'hazardFactor.sds': { group: '检测基础库', title: 'SDS 说明书', description: '维护化学品安全技术资料' },
    configCenter: { group: '业务规则与模板', title: '检测方法', description: '维护方法、公式和标准依据' },
    'configCenter.detectMethod': { group: '业务规则与模板', title: '检测方法', description: '维护方法、公式和标准依据' },
    'configCenter.orgConfig': { group: '业务规则与模板', title: '机构与编号规则', description: '维护机构、资质和编号规则' },
    'configCenter.detectType': { group: '业务规则与模板', title: '检测分类', description: '维护检测分类及排序' },
    labManage: { group: '实验检测', title: '检测任务单', description: '签发、领取和提交实验任务' },
    'labManage.task': { group: '实验检测', title: '检测任务单', description: '签发、领取和提交实验任务' },
    'labManage.result': { group: '实验检测', title: '结果计算与复核', description: '核对曲线参数、计算结果并完成复核' },
    radiology: { group: '放射诊疗检测', title: 'DR 质量控制', description: '依据标准完成设备性能与场所防护检测' },
    'radiology.dr': { group: '放射诊疗检测', title: 'DR 质量控制', description: '依据 WS 76-2020 与 GBZ 130-2020' },
    'radiology.ct': { group: '放射诊疗检测', title: 'CT 质量控制', description: '依据 WS 519-2019 与 GBZ 130-2020' },
    feedback: { group: '意见反馈', title: '意见跟踪', description: '提交改进意见并跟踪采纳、处理与确认记录' },
    help: { group: '帮助与支持', title: '帮助中心', description: '按功能查看操作步骤、字段说明和常见问题' },
  }
  return entries[selectedMenuKey.value]
})

const helpReturnLabel = computed(() => {
  if (helpReturnKey.value === 'help') return '项目工作台'
  const returnMeta = ({
    project: '项目工作台',
    'project.workspace': '项目工作台',
    'project.report': '报告管理',
    'radiology.dr': 'DR 质量控制',
    'radiology.ct': 'CT 质量控制',
  } as Partial<Record<MenuKey, string>>)[helpReturnKey.value]
  return returnMeta || '原功能'
})

const projectFlowStage = computed(() => {
  if (!selectedProject.value) return 1
  if (selectedProject.value.status === 'REPORT_READY') return 5
  if (selectedProject.value.status === 'JUDGED') return 4
  if (projectWorkTab.value === 'result' || selectedProject.value.status === 'DATA_FILLED') return 3
  if (projectWorkTab.value === 'supplement' || projectWorkTab.value === 'position') return 2
  return 1
})

const nextProjectAction = computed(() => {
  if (!selectedProject.value) return '先创建或选择一个项目，录入受检单位与检测类别。'
  if (selectedProject.value.status === 'DRAFT') return '补全项目信息、岗位和原始检测数据后手动保存。'
  if (selectedProject.value.status === 'DATA_FILLED') return '核对原始值与限值，然后执行自动判定和报告预检。'
  if (selectedProject.value.status === 'JUDGED') return '确认判定依据、编写审核人员和报告补充信息。'
  return '报告已具备生成条件，可进入报告管理生成和归档。'
})

function openProjectMenu(child: 'workspace' | 'report' = 'workspace') {
  projectChildMenu.value = child
  selectedMenuKey.value = child === 'workspace' ? 'project.workspace' : 'project.report'
  if (child === 'report') {
    activeTab.value = 'reportCenter'
    void fetchReportCenter()
  } else {
    activeTab.value = 'project'
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
  if (!isAdmin.value) {
    setMessage('当前账号没有系统管理权限', 'error')
    return
  }
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
  if ((key === 'system' || key.startsWith('system.')) && !isAdmin.value) {
    setMessage('当前账号没有系统管理权限', 'error')
    return
  }
  if (key === 'help') {
    openHelpInNewTab('overview')
    void nextTick(() => { sideMenuRenderKey.value += 1 })
    return
  }
  if (key === 'feedback') {
    if (selectedMenuKey.value !== 'feedback') feedbackSourceModule.value = menuMeta.value.title
    selectedMenuKey.value = 'feedback'
    activeTab.value = 'feedback'
    return
  }
  void loadMenuData(key)
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
  if (key === 'radiology' || key === 'radiology.dr') { activeTab.value = 'radiology'; radiologyChildMenu.value = 'DR'; selectedMenuKey.value = 'radiology.dr'; return }
  if (key === 'radiology.ct') { activeTab.value = 'radiology'; radiologyChildMenu.value = 'CT'; selectedMenuKey.value = 'radiology.ct'; return }
  if (key === 'system.dept') { openSystemMenu('dept'); return }
  if (key === 'system.post') { openSystemMenu('post'); return }
  if (key === 'system.notice') { openSystemMenu('notice'); return }
  if (key === 'system.syslog') { openSystemMenu('syslog'); return }
  if (key === 'system.sysfile') { openSystemMenu('sysfile'); return }
  if (key === 'system.sysconfig') { openSystemMenu('sysconfig'); return }
  openMaintenanceMenu('reportText')
}

function resolveHelpTopic(topicOrMenuKey: HelpTopicId | MenuKey): HelpTopicId {
  return helpTopicByMenuKey[topicOrMenuKey] || findHelpTopic(topicOrMenuKey as HelpTopicId).id
}

function syncStandaloneHelpUrl(topicId: HelpTopicId, anchorId?: string | null) {
  if (!standaloneHelpPage.value) return
  const url = new URL(window.location.href)
  url.searchParams.set('view', 'help')
  url.searchParams.set('topic', topicId)
  if (anchorId) url.searchParams.set('anchor', anchorId)
  else url.searchParams.delete('anchor')
  window.history.replaceState(null, '', url)
}

function showHelpInCurrentTab(topicOrMenuKey: HelpTopicId | MenuKey, anchorId?: string | null) {
  if (selectedMenuKey.value !== 'help') helpReturnKey.value = selectedMenuKey.value
  helpTopicId.value = resolveHelpTopic(topicOrMenuKey)
  helpActiveAnchorId.value = anchorId || null
  selectedMenuKey.value = 'help'
  activeTab.value = 'help'
  syncStandaloneHelpUrl(helpTopicId.value, helpActiveAnchorId.value)
}

function openHelpInNewTab(topicOrMenuKey: HelpTopicId | MenuKey = selectedMenuKey.value, anchorId?: string | null) {
  const anchor = findHelpAnchor(anchorId)
  const topicId = anchor?.topicId || resolveHelpTopic(topicOrMenuKey)
  const url = new URL(window.location.pathname, window.location.origin)
  url.searchParams.set('view', 'help')
  url.searchParams.set('topic', topicId)
  if (anchorId) url.searchParams.set('anchor', anchorId)
  window.open(url.toString(), '_blank', 'noopener,noreferrer')
}

function activateStandaloneHelpRequest() {
  const url = new URL(window.location.href)
  if (url.searchParams.get('view') !== 'help') return
  standaloneHelpPage.value = true
  const anchorId = url.searchParams.get('anchor')
  const anchor = findHelpAnchor(anchorId)
  const topicId = anchor?.topicId || findHelpTopic(url.searchParams.get('topic') as HelpTopicId).id
  showHelpInCurrentTab(topicId, anchor ? anchorId : null)
}

function selectHelpTopic(topicId: HelpTopicId) {
  helpTopicId.value = topicId
  helpActiveAnchorId.value = null
  syncStandaloneHelpUrl(topicId)
}

function selectHelpAnchor(payload: { topicId: HelpTopicId; anchorId: string }) {
  helpTopicId.value = payload.topicId
  helpActiveAnchorId.value = payload.anchorId
  syncStandaloneHelpUrl(payload.topicId, payload.anchorId)
}

function openDetailedHelp(payload: { topicId: HelpTopicId; anchorId: string }) {
  contextHelpAnchorId.value = null
  openHelpInNewTab(payload.topicId, payload.anchorId)
}

function handleContextHelpEvent(event: Event) {
  const anchorId = (event as CustomEvent<{ anchorId?: string }>).detail?.anchorId
  if (anchorId) contextHelpAnchorId.value = anchorId
}

function returnFromHelp() {
  if (standaloneHelpPage.value) {
    window.close()
    return
  }
  const returnKey = helpReturnKey.value === 'help' ? 'project.workspace' : helpReturnKey.value
  selectMenu(returnKey)
}

const loginForm = reactive({
  username: 'admin',
  password: '',
  rememberMe: true,
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
const reportCenterRows = ref<ReportCenterRow[]>([])
const reportCenterTotal = ref(0)
const selectedReportCenterRow = ref<ReportCenterRow | null>(null)
const reportCenterPreview = ref<ReportPreview | null>(null)
const reportCenterLogs = ref<ReportLog[]>([])
const deleteNotApplicableOnGenerate = ref(true)
const reportCenterQuery = reactive({
  projectCode: '',
  companyName: '',
  reportStatus: '' as '' | 'GENERATED' | 'PENDING',
  generatedFrom: '',
  generatedTo: '',
  page: 0,
  size: 20,
})
const operating = ref(false)
const operationType = ref<'create' | 'edit' | ''>('')
const selectedProjectRows = ref<Project[]>([])

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
  reportSupplement: createEmptyReportSupplement(),
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
  id: null as number | null,
  factorTemplateId: null as number | null,
  positionId: null as number | null,
  factorId: null as number | null,
  jobName: '',
  factorName: '',
  metricType: 'TWA',
  valueRaw: '',
  valueNum: '',
  unit: 'mg/m3',
  limitValue: '',
  limitUnit: 'mg/m3',
  factorCategory: '',
  pointCode: '',
  sampleCode: '',
  detectionMode: '',
  noiseType: '',
  laboratoryDate: '',
  detectionBasis: '',
  detectionMethod: '',
  instrument: '',
  testingOrganization: '',
  sourceRecordNo: '',
  detectionLimit: '',
  quantificationLimit: '',
  evaluationBasis: '',
  overLimitReason: '',
  weeklyWorkDays: '',
  reductionApplicable: null as boolean | null,
  peakLimitMultiplier: '',
  peakExposureMinutes: '',
  peakTimesPerShift: '',
  peakIntervalMinutes: '',
  mixedExposureGroup: '',
  mixedExposureBasis: '',
  measurementConditions: '',
  measurementDate: '',
  measurementBodyPart: '',
  measurementBand: '',
  dailyExposureHours: '',
  weeklyExposureHours: '',
})

const batchResultText = ref('')
const projectDirty = ref(false)
let syncingProjectForm = false
const lastManualSaveAt = ref('')
const lastCreatedProjectId = ref<number | null>(null)
const lastCreatedProjectCode = ref('')
const lastCreatedProjectStatus = ref<ProjectStatus | null>(null)
const listOrderHint = ref('默认按创建时间倒序')

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
  detectionBasis: '',
  detectionMethod: '',
  detectionInstruments: '',
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
  profession: '',
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

// 系统管理 - 用户
const users = ref<SysUser[]>([])
const totalUsers = ref(0)
const userQuery = reactive({ username: '', page: 0, size: 20 })
const userForm = reactive({
  id: null as number | null,
  username: '',
  password: '',
  employeeId: null as number | null,
  role: 'USER' as 'ADMIN' | 'USER',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
})
const resetPasswordForm = reactive({ userId: null as number | null, username: '', newPassword: '' })
const showResetDialog = ref(false)

// 系统管理 - 用户分配角色
const showRoleAssignDialog = ref(false)
const assigningUserId = ref<number | null>(null)
const assigningUsername = ref('')
const selectedRoleIds = ref<number[]>([])

// 系统管理 - 角色
const roles = ref<SysRole[]>([])
const totalRoles = ref(0)
const roleQuery = reactive({ roleCode: '', roleName: '', page: 0, size: 20 })
const roleForm = reactive({
  id: null as number | null,
  roleCode: '',
  roleName: '',
  remark: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
})

// 系统管理 - 角色分配权限
const showRolePermDialog = ref(false)
const assigningRoleId = ref<number | null>(null)
const assigningRoleName = ref('')
const selectedPermIds = ref<number[]>([])

// 系统管理 - 菜单管理
const menuTree = ref<SysMenu[]>([])
const menuForm = reactive({
  id: null as number | null,
  permCode: '',
  permName: '',
  parentId: null as number | null,
  sortNo: null as number | null,
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
})

// 系统管理 - 权限管理
const permissions = ref<SysPermission[]>([])
const totalPermissions = ref(0)
const permQuery = reactive({ permCode: '', permName: '', page: 0, size: 20 })
const permForm = reactive({
  id: null as number | null,
  permCode: '',
  permName: '',
  permType: 'API' as 'MENU' | 'BUTTON' | 'API',
  apiMethod: '',
  apiPath: '',
  parentId: null as number | null,
  sortNo: null as number | null,
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
})

const statusText = computed<Record<ProjectStatus, string>>(() => ({
  DRAFT: '草稿',
  DATA_FILLED: '已录入',
  JUDGED: '已判定',
  REPORT_READY: '已生成报告',
}))

const canCreateProject = computed(() => !operating.value && !loading.value && !actionBusy.saveProject)
const canManualSave = computed(() => operating.value && !loading.value && !actionBusy.saveProject)
const canClearForm = computed(() => operating.value && !loading.value && !actionBusy.saveProject)
const canCancelCreate = computed(() => operating.value && !loading.value && !actionBusy.cancelCreate)
const canBatchDelete = computed(() => !operating.value && !loading.value && selectedProjectRows.value.length > 0)
const canListRowEdit = computed(() => !operating.value && !loading.value)
const canListRowCopy = computed(() => !operating.value && !loading.value && !actionBusy.copyProject)
const canListRowDelete = computed(() => !operating.value && !loading.value && !actionBusy.deleteProject)
const canProjectOperate = computed(() => operating.value && !!selectedProject.value)

const isLoggedIn = computed(() => Boolean(token.value))
const isAdmin = computed(() => currentRole.value === 'ADMIN')
const operatorLabel = computed(() => currentUsername.value || (isAdmin.value ? '管理员' : '业务用户'))
const operatorRoleLabel = computed(() => isAdmin.value ? '管理员' : '业务人员')
const operatorAvatar = computed(() => operatorLabel.value.slice(0, 1).toUpperCase())
const hasPermission = (code: string) => isAdmin.value || permissionCodes.value.has(code)
const canReviewRadiology = computed(() => hasPermission('RADIOLOGY:WORKFLOW:REVIEW'))
const canApproveRadiology = computed(() => hasPermission('RADIOLOGY:WORKFLOW:APPROVE'))
const canGenerateRadiologyReport = computed(() => hasPermission('RADIOLOGY:REPORT:GENERATE'))
const canManageRadiologyRules = computed(() => hasPermission('RADIOLOGY:RULE:MANAGE'))
const canReadAllFeedback = computed(() => hasPermission('FEEDBACK:READ_ALL') || hasPermission('FEEDBACK:HANDLE'))
const canHandleFeedback = computed(() => hasPermission('FEEDBACK:HANDLE'))
const canCloseFeedback = computed(() => hasPermission('FEEDBACK:CLOSE'))

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
  latestAction.value = `${nowTimeText()} ${text}`
  window.setTimeout(() => {
    if (message.value === text) {
      message.value = ''
    }
  }, type === 'error' ? 7000 : 5500)
}

function nowTimeText() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function resolveApiMessage(err: any, fallback: string) {
  const raw = err?.response?.data?.message || ''
  if (typeof raw === 'string' && raw.includes('garbled text')) {
    return '检测到乱码数据，请先在业务规则与模板中修复对应模板，或使用标准 UTF-8 文本'
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
  projectForm.reportSupplement = createEmptyReportSupplement()
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
  projectForm.reportSupplement = project.reportSupplement
    ? JSON.parse(JSON.stringify(project.reportSupplement))
    : createEmptyReportSupplement()
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
    'report.reportHeaderName': '报告页眉单位简称',
    'report.socialCreditCode': '统一社会信用代码',
    'report.legalRepresentative': '法定代表人/负责人',
    'report.industryCategory': '行业类型（大类/中类/小类）',
    'report.detectionScope': '检测范围',
    'report.lawsRegulations': '法律、法规与规章',
    'report.basicMaterials': '基础资料',
    'report.productionProcess': '生产工艺说明',
    'report.radiationSourceSummary': '辐射源项情况（需要明确无辐射源、另行检测、另出报告或纳入本报告）',
    'report.radiationSourceMode': '辐射源项处理方式',
    'report.radiationSources.empty': '选择了③纳入本报告，但尚未录入任何辐射源项',
    'report.radiationSeparateReportNo': '放射性因素另行报告编号',
    'report.vocAnalysisConclusion': '物料 VOC 定性分析开展情况',
    'report.vocAnalysisItems.empty': '已确认开展 VOC 定性分析，但尚未录入组分结果',
    'report.exclusionScreeningConclusion': '排除性检测开展情况及结论',
    'report.exclusionScreeningItems.empty': '已确认开展排除性检测，但尚未录入结果',
    'report.rawRecordCompletenessNote': '原始记录完整性确认（采样编号、日期、时间、仪器与检测条件）',
    'report.qualificationCertificatePlan': '资质证书版本、有效期与嵌入方式',
    'report.qualificationCertificateImage': '当前有效资质证书图片（嵌入报告第3页）',
    'report.attachmentDeliveryPlan': '委托书、调查表、检测计划、照片等附件交付方案',
    'report.photoEmbeddingPlan': '检测照片水印与 Word 嵌入方式',
    'report.multiDayDisplayRule': '单日及多日检测的日期列展示规则',
    'report.overtimeReductionRule': '超过 8 小时工作制的折减计算与报告规则',
    'report.physicalFactorScope': '物理因素检测项目及不涉及项的确认范围',
    'report.specialMeasurementRule': '高频、微波、紫外等特殊检测的部位与波段规则',
    'report.signatureStampMode': '签名与盖章方式',
    'report.productionCondition': '现场检测时生产情况',
    'report.samplingLayoutDescription': '检测布点说明',
    'report.detectionConditions': '检测条件说明',
    'report.environmentConditions': '现场环境气象条件',
    'report.onsiteInspectors': '现场检测人员',
    'report.planDeviation': '检测计划执行/变更说明',
    'report.riskCategory': '职业病危害风险分类',
    'report.majorHazards': '主要职业病危害因素',
    'report.overLimitSummary': '岗位超标情况及原因（无超标也需要明确说明）',
    'report.majorProblems': '职业病危害防治主要问题',
    'report.recommendations': '职业病危害防治建议',
    'report.samplingPlans.empty': '至少需要 1 条现场采样与测量计划',
    'report.materials.unconfirmed': '原辅材料及产品情况未录入或未确认“不涉及”',
    'report.equipment.unconfirmed': '主要生产设备未录入或未确认“不涉及”',
    'report.protectionFacilities.unconfirmed': '职业病防护设施未录入或未确认“不涉及”',
    'report.ppe.unconfirmed': '个人防护用品未录入或未确认“不涉及”',
  }
  return map[item] || item
}

function projectStatusTagType(status: ProjectStatus): 'info' | 'success' | 'warning' | 'danger' {
  if (status === 'REPORT_READY') return 'success'
  if (status === 'JUDGED') return 'warning'
  if (status === 'DATA_FILLED') return 'info'
  return 'danger'
}

function latestCreatedStatusLabel() {
  if (!lastCreatedProjectStatus.value) return ''
  return statusText.value[lastCreatedProjectStatus.value]
}

function onProjectRowClick(row: Project) {
  if (operating.value && selectedProject.value?.id !== row.id) {
    setMessage('请先完成或取消当前操作，再切换项目', 'error')
    return
  }
  void selectProject(row)
}

function onProjectSelectionChange(rows: Project[]) {
  selectedProjectRows.value = rows
}

async function editProjectFromList(project: Project) {
  if (operating.value) {
    setMessage('请先完成或取消当前操作', 'error')
    return
  }
  await selectProject(project)
  operating.value = true
  operationType.value = 'edit'
  setMessage(`已进入操作中：${project.projectCode}`, 'info')
}

function beginCreateOperation() {
  if (operating.value) return
  startNewDraft(true)
  operating.value = true
  operationType.value = 'create'
  setMessage('已进入操作中：新建项目', 'info')
}

function clearOperationForm() {
  if (!operating.value) return
  if (operationType.value === 'edit') {
    const ok = window.confirm('当前是编辑模式，清空后若保存将覆盖原项目。是否继续清空？')
    if (!ok) return
  }
  resetProjectForm()
  projectDirty.value = false
  setMessage('已清空当前操作表单', 'info')
}

function focusProjectField(fieldId: string) {
  window.setTimeout(() => {
    const el = document.getElementById(fieldId) as HTMLInputElement | HTMLSelectElement | null
    if (!el) return
    el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    el.focus()
  }, 50)
}

function selectLatestProject() {
  if (!projects.value.length) return
  void selectProject(projects.value[0])
  projectWorkTab.value = 'base'
}

async function selectLastCreatedProject() {
  if (!lastCreatedProjectId.value) {
    selectLatestProject()
    return
  }
  const target = projects.value.find((item) => item.id === lastCreatedProjectId.value)
  if (target) {
    await selectProject(target)
  } else {
    await fetchProjects()
    const refreshed = projects.value.find((item) => item.id === lastCreatedProjectId.value)
    if (refreshed) {
      await selectProject(refreshed)
    } else {
      selectLatestProject()
    }
  }
  projectWorkTab.value = 'base'
}

function openBaseTab() {
  projectWorkTab.value = 'base'
}

async function login() {
  loading.value = true
  try {
    const { data } = await api.post<LoginResponse>('/api/auth/login', {
      username: loginForm.username,
      password: loginForm.password,
      clientType: 'web',
      rememberMe: loginForm.rememberMe,
    })
    token.value = data.token
    currentUsername.value = data.username
    currentRole.value = data.role
    clearStoredSession()
    const storage = loginForm.rememberMe ? localStorage : sessionStorage
    storage.setItem('ohdp_token', data.token)
    storage.setItem('ohdp_username', data.username)
    storage.setItem('ohdp_role', data.role)
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
  currentUsername.value = ''
  currentRole.value = 'USER'
  permissionCodes.value = new Set()
  clearStoredSession()
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
    fetchCurrentPermissions(),
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

async function loadMenuData(key: MenuKey) {
  if (!token.value) return
  const loaders: Partial<Record<MenuKey, () => Promise<unknown>>> = {
    project: fetchProjects,
    'project.workspace': fetchProjects,
    'project.report': fetchReportCenter,
    maintenance: fetchCompanyProfiles,
    'maintenance.company': fetchCompanyProfiles,
    'maintenance.position': fetchPositionTemplates,
    'maintenance.factor': fetchFactorTemplates,
    'maintenance.dict': fetchDictionaryItems,
    'maintenance.staff': fetchStaffProfiles,
    'maintenance.reportText': fetchReportTextTemplates,
    system: fetchUsers,
    'system.user': fetchUsers,
    'system.role': fetchRoles,
    'system.menu': fetchMenuTree,
    'system.permission': fetchPermissions,
    'system.dept': fetchSysDepts,
    'system.post': fetchSysPosts,
    'system.notice': fetchSysNotices,
    'system.syslog': fetchSysLogs,
    'system.sysconfig': fetchSysConfigs,
    basicData: fetchLaws,
    'basicData.law': fetchLaws,
    'basicData.standard': fetchStandards,
    'basicData.region': fetchRegions,
    'basicData.device': fetchDevices,
    hazardFactor: fetchHazardFactors,
    'hazardFactor.factor': fetchHazardFactors,
    'hazardFactor.sds': async () => Promise.all([fetchSdsList(), fetchHazardFactors()]),
    configCenter: fetchDetectionMethods,
    'configCenter.detectMethod': fetchDetectionMethods,
    'configCenter.orgConfig': fetchOrgConfig,
    'configCenter.detectType': fetchDetectTypes,
    labManage: fetchLabTasks,
    'labManage.task': fetchLabTasks,
    'labManage.result': fetchLabResults,
  }
  try {
    await loaders[key]?.()
  } catch (err: any) {
    setMessage(err?.response?.data?.message || `${menuMeta.value.title}数据加载失败`, 'error')
  }
}

async function fetchCurrentPermissions() {
  try {
    const { data } = await api.get<string[]>('/api/system/current-user/permission-codes')
    permissionCodes.value = new Set(data || [])
  } catch {
    permissionCodes.value = new Set()
  }
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
  projects.value = (data.content || []).slice().sort((a, b) => {
    const ta = new Date(a.createdAt || '').getTime()
    const tb = new Date(b.createdAt || '').getTime()
    return (Number.isFinite(tb) ? tb : 0) - (Number.isFinite(ta) ? ta : 0)
  })
  totalProjects.value = data.totalElements || 0
  listOrderHint.value = '默认按创建时间倒序'
  const projectIds = new Set(projects.value.map((item) => item.id))
  selectedProjectRows.value = selectedProjectRows.value.filter((item) => projectIds.has(item.id))
  if (selectedProject.value) {
    const current = projects.value.find((item) => item.id === selectedProject.value?.id)
    if (current) {
      selectedProject.value = current
    }
  }
}

function applyProjectQueue(status: '' | ProjectStatus) {
  query.status = status
  query.page = 0
  fetchProjects()
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
  lastCreatedProjectId.value = project.id
  lastCreatedProjectCode.value = project.projectCode
  lastCreatedProjectStatus.value = project.status
}

async function createProject(): Promise<boolean> {
  if (!validateProjectSelections()) return false
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
      reportSupplement: projectForm.reportSupplement,
    }
    const { data } = await api.post<Project>('/api/ohdp/projects', payload)
    await fetchProjects()
    lastCreatedProjectId.value = data.id
    lastCreatedProjectCode.value = data.projectCode
    lastCreatedProjectStatus.value = data.status
    await selectProject(data)
    operating.value = true
    operationType.value = 'edit'
    projectWorkTab.value = 'base'
    projectDirty.value = false
    lastManualSaveAt.value = nowTimeText()
    setMessage(`项目已创建并进入操作中：${data.projectCode}`, 'success')
    return true
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '创建项目失败'), 'error')
    return false
  } finally {
    actionBusy.saveProject = false
    loading.value = false
  }
}

async function updateProject(silent = false): Promise<boolean> {
  if (!selectedProject.value) return false
  if (!validateProjectSelections()) return false
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
      reportSupplement: projectForm.reportSupplement,
    }
    const { data } = await api.put<Project>(`/api/ohdp/projects/${selectedProject.value.id}`, payload)
    selectedProject.value = data
    projectDirty.value = false
    await fetchProjects()
    lastManualSaveAt.value = nowTimeText()
    if (!silent) {
      setMessage('项目已更新', 'success')
    }
    return true
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '更新项目失败'), 'error')
    return false
  } finally {
    actionBusy.saveProject = false
  }
}

async function cancelCreateProject() {
  if (!operating.value) return
  const ok = window.confirm('确认取消当前操作吗？未保存内容将丢失。')
  if (!ok) return
  if (operationType.value === 'edit' && selectedProject.value) {
    fillProjectForm(selectedProject.value)
  } else {
    startNewDraft(true)
  }
  projectDirty.value = false
  operating.value = false
  operationType.value = ''
  setMessage('已取消当前操作，未保存内容已丢弃', 'success')
}

async function copyProjectFromList(projectId: number) {
  if (operating.value) {
    setMessage('请先完成或取消当前操作', 'error')
    return
  }
  loading.value = true
  actionBusy.copyProject = true
  try {
    const { data } = await api.post<Project>(`/api/ohdp/projects/${projectId}/copy`)
    await fetchProjects()
    await selectProject(data)
    operating.value = true
    operationType.value = 'edit'
    setMessage(`项目已复制并进入操作中：${data.projectCode}`, 'success')
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '复制项目失败'), 'error')
  } finally {
    actionBusy.copyProject = false
    loading.value = false
  }
}

async function deleteProjectsByIds(ids: number[]) {
  if (!ids.length) return
  if (operating.value) {
    setMessage('操作中状态不可删除项目，请先取消当前操作', 'error')
    return
  }
  const ok = window.confirm(`确认删除选中的 ${ids.length} 个项目吗？`)
  if (!ok) return
  loading.value = true
  actionBusy.deleteProject = true
  try {
    for (const id of ids) {
      await api.delete(`/api/ohdp/projects/${id}`)
      if (selectedProject.value?.id === id) {
        selectedProject.value = null
      }
    }
    await fetchProjects()
    selectedProjectRows.value = []
    if (!selectedProject.value) {
      startNewDraft()
      operating.value = false
      operationType.value = ''
    }
    setMessage(`已删除 ${ids.length} 个项目`, 'success')
  } catch (err: any) {
    setMessage(resolveApiMessage(err, '删除项目失败'), 'error')
  } finally {
    actionBusy.deleteProject = false
    loading.value = false
  }
}

async function deleteProjectFromList(id: number) {
  await deleteProjectsByIds([id])
}

async function batchDeleteProjects() {
  const ids = selectedProjectRows.value.map((item) => item.id)
  await deleteProjectsByIds(ids)
}

async function saveCurrentOperation(): Promise<boolean> {
  if (!operating.value) return false
  if (operationType.value === 'create' || !selectedProject.value) {
    return await createProject()
  }
  return await updateProject()
}

async function fetchPositions(projectId: number) {
  const { data } = await api.get<Position[]>(`/api/ohdp/projects/${projectId}/positions`)
  positions.value = data
}

async function addPosition() {
  if (!canProjectOperate.value || !selectedProject.value) return
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
  if (!canProjectOperate.value || !selectedProject.value) return
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

function editResult(item: ResultItem) {
  resultForm.id = item.id
  resultForm.factorTemplateId = null
  resultForm.positionId = item.positionId ?? null
  resultForm.factorId = (item as any).factorId ?? null
  resultForm.jobName = item.jobName
  resultForm.factorName = item.factorName
  resultForm.metricType = item.metricType
  resultForm.valueRaw = item.valueRaw ?? ''
  resultForm.valueNum = item.valueNum != null ? String(item.valueNum) : ''
  resultForm.unit = item.unit ?? 'mg/m3'
  resultForm.limitValue = item.limitValue != null ? String(item.limitValue) : ''
  resultForm.limitUnit = item.limitUnit ?? 'mg/m3'
  resultForm.factorCategory = item.factorCategory != null ? String(item.factorCategory) : ''
  resultForm.pointCode = item.pointCode != null ? String(item.pointCode) : ''
  resultForm.sampleCode = item.sampleCode != null ? String(item.sampleCode) : ''
  resultForm.detectionMode = item.detectionMode != null ? String(item.detectionMode) : ''
  resultForm.noiseType = item.noiseType != null ? String(item.noiseType) : ''
  resultForm.laboratoryDate = item.laboratoryDate != null ? String(item.laboratoryDate) : ''
  resultForm.detectionBasis = item.detectionBasis != null ? String(item.detectionBasis) : ''
  resultForm.detectionMethod = item.detectionMethod != null ? String(item.detectionMethod) : ''
  resultForm.instrument = item.instrument != null ? String(item.instrument) : ''
  resultForm.testingOrganization = item.testingOrganization != null ? String(item.testingOrganization) : ''
  resultForm.sourceRecordNo = item.sourceRecordNo != null ? String(item.sourceRecordNo) : ''
  resultForm.detectionLimit = item.detectionLimit != null ? String(item.detectionLimit) : ''
  resultForm.quantificationLimit = item.quantificationLimit != null ? String(item.quantificationLimit) : ''
  resultForm.evaluationBasis = item.evaluationBasis != null ? String(item.evaluationBasis) : ''
  resultForm.overLimitReason = item.overLimitReason != null ? String(item.overLimitReason) : ''
  resultForm.weeklyWorkDays = item.weeklyWorkDays != null ? String(item.weeklyWorkDays) : ''
  resultForm.reductionApplicable = item.reductionApplicable ?? null
  resultForm.peakLimitMultiplier = item.peakLimitMultiplier != null ? String(item.peakLimitMultiplier) : ''
  resultForm.peakExposureMinutes = item.peakExposureMinutes != null ? String(item.peakExposureMinutes) : ''
  resultForm.peakTimesPerShift = item.peakTimesPerShift != null ? String(item.peakTimesPerShift) : ''
  resultForm.peakIntervalMinutes = item.peakIntervalMinutes != null ? String(item.peakIntervalMinutes) : ''
  resultForm.mixedExposureGroup = item.mixedExposureGroup != null ? String(item.mixedExposureGroup) : ''
  resultForm.mixedExposureBasis = item.mixedExposureBasis != null ? String(item.mixedExposureBasis) : ''
  resultForm.measurementConditions = item.measurementConditions != null ? String(item.measurementConditions) : ''
  resultForm.measurementDate = item.measurementDate ?? ''
  resultForm.measurementBodyPart = item.measurementBodyPart ?? ''
  resultForm.measurementBand = item.measurementBand ?? ''
  resultForm.dailyExposureHours = item.dailyExposureHours != null ? String(item.dailyExposureHours) : ''
  resultForm.weeklyExposureHours = item.weeklyExposureHours != null ? String(item.weeklyExposureHours) : ''
}

function clearResultForm() {
  resultForm.id = null
  resultForm.factorTemplateId = null
  resultForm.positionId = null
  resultForm.factorId = null
  resultForm.jobName = ''
  resultForm.factorName = ''
  resultForm.metricType = 'TWA'
  resultForm.valueRaw = ''
  resultForm.valueNum = ''
  resultForm.unit = 'mg/m3'
  resultForm.limitValue = ''
  resultForm.limitUnit = 'mg/m3'
  resultForm.factorCategory = ''
  resultForm.pointCode = ''
  resultForm.sampleCode = ''
  resultForm.detectionMode = ''
  resultForm.noiseType = ''
  resultForm.laboratoryDate = ''
  resultForm.detectionBasis = ''
  resultForm.detectionMethod = ''
  resultForm.instrument = ''
  resultForm.testingOrganization = ''
  resultForm.sourceRecordNo = ''
  resultForm.detectionLimit = ''
  resultForm.quantificationLimit = ''
  resultForm.evaluationBasis = ''
  resultForm.overLimitReason = ''
  resultForm.weeklyWorkDays = ''
  resultForm.reductionApplicable = null
  resultForm.peakLimitMultiplier = ''
  resultForm.peakExposureMinutes = ''
  resultForm.peakTimesPerShift = ''
  resultForm.peakIntervalMinutes = ''
  resultForm.mixedExposureGroup = ''
  resultForm.mixedExposureBasis = ''
  resultForm.measurementConditions = ''
  resultForm.measurementDate = ''
  resultForm.measurementBodyPart = ''
  resultForm.measurementBand = ''
  resultForm.dailyExposureHours = ''
  resultForm.weeklyExposureHours = ''
}

async function addResult() {
  if (!canProjectOperate.value || !selectedProject.value) return
  const isEdit = resultForm.id != null
  if (!resultForm.factorTemplateId && (!resultForm.factorName.trim() || !resultForm.metricType.trim())) {
    setMessage('请选择模板，或填写危害因素和指标类型', 'error')
    return
  }
  if (!resultForm.positionId && !resultForm.jobName) {
    setMessage('请先选择岗位或填写岗位名称', 'error')
    return
  }
  loading.value = true
  actionBusy.addResult = true
  try {
    const payload = {
      factorTemplateId: resultForm.factorTemplateId,
      positionId: resultForm.positionId,
      factorId: resultForm.factorId,
      jobName: resultForm.jobName,
      factorName: resultForm.factorName,
      metricType: resultForm.metricType,
      valueRaw: resultForm.valueRaw || null,
      valueNum: resultForm.valueNum === '' ? null : Number(resultForm.valueNum),
      unit: resultForm.unit || null,
      limitValue: resultForm.limitValue === '' ? null : Number(resultForm.limitValue),
      limitUnit: resultForm.limitUnit || null,
      factorCategory: resultForm.factorCategory || null,
      pointCode: resultForm.pointCode || null,
      sampleCode: resultForm.sampleCode || null,
      detectionMode: resultForm.detectionMode || null,
      noiseType: resultForm.noiseType || null,
      laboratoryDate: resultForm.laboratoryDate || null,
      detectionBasis: resultForm.detectionBasis || null,
      detectionMethod: resultForm.detectionMethod || null,
      instrument: resultForm.instrument || null,
      testingOrganization: resultForm.testingOrganization || null,
      sourceRecordNo: resultForm.sourceRecordNo || null,
      detectionLimit: resultForm.detectionLimit || null,
      quantificationLimit: resultForm.quantificationLimit || null,
      evaluationBasis: resultForm.evaluationBasis || null,
      overLimitReason: resultForm.overLimitReason || null,
      weeklyWorkDays: resultForm.weeklyWorkDays === '' ? null : Number(resultForm.weeklyWorkDays),
      reductionApplicable: resultForm.reductionApplicable,
      peakLimitMultiplier: resultForm.peakLimitMultiplier === '' ? null : Number(resultForm.peakLimitMultiplier),
      peakExposureMinutes: resultForm.peakExposureMinutes === '' ? null : Number(resultForm.peakExposureMinutes),
      peakTimesPerShift: resultForm.peakTimesPerShift === '' ? null : Number(resultForm.peakTimesPerShift),
      peakIntervalMinutes: resultForm.peakIntervalMinutes === '' ? null : Number(resultForm.peakIntervalMinutes),
      mixedExposureGroup: resultForm.mixedExposureGroup || null,
      mixedExposureBasis: resultForm.mixedExposureBasis || null,
      measurementConditions: resultForm.measurementConditions || null,
      measurementDate: resultForm.measurementDate || null,
      measurementBodyPart: resultForm.measurementBodyPart || null,
      measurementBand: resultForm.measurementBand || null,
      dailyExposureHours: resultForm.dailyExposureHours === '' ? null : Number(resultForm.dailyExposureHours),
      weeklyExposureHours: resultForm.weeklyExposureHours === '' ? null : Number(resultForm.weeklyExposureHours),
    }
    if (isEdit) {
      await api.put(`/api/ohdp/results/${resultForm.id}`, payload)
      setMessage('检测结果已更新', 'success')
    } else {
      await api.post(`/api/ohdp/projects/${selectedProject.value.id}/results`, payload)
      setMessage('检测结果已添加', 'success')
    }
    clearResultForm()
    await fetchResults(selectedProject.value.id)
    await fetchProjects()
  } catch (err: any) {
    setMessage(resolveApiMessage(err, isEdit ? '更新检测结果失败' : '添加检测结果失败'), 'error')
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
  if (!canProjectOperate.value || !selectedProject.value) return
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
  if (!canProjectOperate.value || !selectedProject.value) return
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
  if (!canProjectOperate.value || !selectedProject.value) return
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
  if (!canProjectOperate.value || !selectedProject.value) return
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
  if (!canProjectOperate.value || !selectedProject.value) return
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

async function fetchReportCenter() {
  actionBusy.reportCenter = true
  try {
    const { data } = await api.get<PageResult<ReportCenterRow>>('/api/ohdp/reports', {
      params: {
        projectCode: reportCenterQuery.projectCode || undefined,
        companyName: reportCenterQuery.companyName || undefined,
        reportStatus: reportCenterQuery.reportStatus || undefined,
        generatedFrom: reportCenterQuery.generatedFrom || undefined,
        generatedTo: reportCenterQuery.generatedTo || undefined,
        page: reportCenterQuery.page,
        size: reportCenterQuery.size,
      },
    })
    reportCenterRows.value = data.content || []
    reportCenterTotal.value = data.totalElements || 0
    if (selectedReportCenterRow.value) {
      selectedReportCenterRow.value = reportCenterRows.value.find(
        (item) => item.projectId === selectedReportCenterRow.value?.projectId,
      ) || null
    }
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告管理加载失败', 'error')
  } finally {
    actionBusy.reportCenter = false
  }
}

async function selectReportCenterRow(row: ReportCenterRow) {
  selectedReportCenterRow.value = row
  reportCenterPreview.value = null
  reportCenterLogs.value = []
  try {
    const requests: Promise<unknown>[] = [
      api.get<ReportPreview>(`/api/ohdp/projects/${row.projectId}/report/preview`).then(({ data }) => {
        reportCenterPreview.value = data
      }),
    ]
    if (row.reportId) {
      requests.push(api.get<ReportLog[]>(`/api/ohdp/projects/${row.projectId}/report/logs`).then(({ data }) => {
        reportCenterLogs.value = data
      }))
    }
    await Promise.all(requests)
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告详情加载失败', 'error')
  }
}

async function generateReportFromCenter(row: ReportCenterRow) {
  const verb = row.reportId ? '重新生成并覆盖现有报告' : '生成报告'
  const contentMode = deleteNotApplicableOnGenerate.value
    ? '删除不涉及的章节和表格'
    : '保留不涉及的章节和表格'
  if (!window.confirm(`确定为项目 ${row.projectCode} ${verb}吗？\n本次将${contentMode}。`)) return
  actionBusy.generateReport = true
  try {
    await api.post(`/api/ohdp/projects/${row.projectId}/report/generate`, null, {
      params: { deleteNotApplicable: deleteNotApplicableOnGenerate.value },
    })
    await fetchReportCenter()
    const refreshed = reportCenterRows.value.find((item) => item.projectId === row.projectId)
    if (refreshed) await selectReportCenterRow(refreshed)
    setMessage(`${row.projectCode} 报告已生成`, 'success')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '报告生成失败，请检查预检缺项', 'error')
  } finally {
    actionBusy.generateReport = false
  }
}

async function openProjectFromReport(row: ReportCenterRow) {
  try {
    const { data } = await api.get<Project>(`/api/ohdp/projects/${row.projectId}`)
    await selectProject(data)
    openProjectMenu('workspace')
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '项目加载失败', 'error')
  }
}

async function fetchReportLogs(projectId: number) {
  const { data } = await api.get<ReportLog[]>(`/api/ohdp/projects/${projectId}/report/logs`)
  reportLogs.value = data
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
    projectWorkTab.value = 'base'
    setMessage('请先选择受检单位', 'error')
    focusProjectField('project-company-profile')
    return false
  }
  if (!projectForm.submitUnitCompanyId) {
    projectWorkTab.value = 'base'
    setMessage('请先选择送检单位', 'error')
    focusProjectField('project-submit-unit')
    return false
  }
  if (!projectForm.entrustUnitCompanyId) {
    projectWorkTab.value = 'base'
    setMessage('请先选择委托单位', 'error')
    focusProjectField('project-entrust-unit')
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
  factorTemplateForm.detectionBasis = item.detectionBasis || ''
  factorTemplateForm.detectionMethod = item.detectionMethod || ''
  factorTemplateForm.detectionInstruments = item.detectionInstruments || ''
  factorTemplateForm.notes = item.notes || ''
}

function clearFactorTemplateForm() {
  factorTemplateForm.id = null
  factorTemplateForm.factorName = ''
  factorTemplateForm.metricType = 'TWA'
  factorTemplateForm.defaultUnit = ''
  factorTemplateForm.defaultLimitValue = ''
  factorTemplateForm.defaultLimitUnit = ''
  factorTemplateForm.detectionBasis = ''
  factorTemplateForm.detectionMethod = ''
  factorTemplateForm.detectionInstruments = ''
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
  staffProfileForm.profession = item.profession || ''
  staffProfileForm.certNo = item.certNo || ''
  staffProfileForm.contactPhone = item.contactPhone || ''
  staffProfileForm.notes = item.notes || ''
}

function clearStaffProfileForm() {
  staffProfileForm.id = null
  staffProfileForm.staffName = ''
  staffProfileForm.staffTitle = ''
  staffProfileForm.profession = ''
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

function startNewDraft(silent = false) {
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
  if (!silent) {
    setMessage('已清空表单', 'info')
  }
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
const selectedHazardFactorId = ref<number | null>(null)
const selectedHazardFactorName = ref('')
const factorLimits = ref<OhdpHazardFactorLimit[]>([])
const limitForm = reactive({ id: null as number|null, limitType: 'PC_TWA', limitValue: '' as string, limitUnit: 'mg/m3', standardCode: '', notes: '' })
const sdsList = ref<OhdpSds[]>([])
const detectionMethods = ref<OhdpDetectionMethod[]>([])
const detectTypes = ref<OhdpDetectType[]>([])
const labTasks = ref<OhdpLabTask[]>([])
const labResults = ref<OhdpLabResult[]>([])
const sysDepts = ref<SysDept[]>([])
const sysPosts = ref<SysPost[]>([])
const sysNotices = ref<SysNotice[]>([])
const sysLogs = ref<SysSysLog[]>([])
const sysConfigs = ref<SysConfig[]>([])

const lawForm = reactive({ id: null as number|null, title: '', docNo: '', level: 'NATIONAL', publishDate: '', effectiveDate: '', publisher: '', isValid: true, isOpen: true, notes: '' })
const standardForm = reactive({ id: null as number|null, standardName: '', standardCode: '', level: 'NATIONAL', useScope: '', publishDate: '', isValid: true, isOpen: true, notes: '' })
const referenceDocuments = ref<ReferenceDocument[]>([])
const referenceDocumentFile = ref<File | null>(null)
const referenceVersionLabel = ref('')
const referenceSourceUrl = ref('')
const referenceDocumentInputKey = ref(0)
const regionForm = reactive({ id: null as number|null, parentId: null as number|null, regionCode: '', regionName: '', level: 1, sortNo: 0 })
const deviceForm = reactive({ id: null as number|null, deviceName: '', deviceType: 'LAB_INSTRUMENT', deviceModel: '', deviceNo: '', status: 'NORMAL', notes: '', certificateNo:'',calibrationDate:'',calibrationExpiry:'' })
const hazardFactorForm = reactive({ id: null as number|null, factorName: '', factorCategory: 'CHEMICAL', factorCode: '', casNo: '', enabled: true })
const sdsForm = reactive({ id: null as number|null, substanceName: '', casNo: '', factorId: null as number|null, version: '', notes: '' })
const detectionMethodForm = reactive({ id: null as number|null, methodName: '', methodCode: '', methodAbbr: '', formula: '', standardCode: '', enabled: true })
const detectTypeForm = reactive({ id: null as number|null, typeName: '', typeCode: '', typeAbbr: '', sortNo: 0, enabled: true })
const orgConfigForm = reactive<OhdpOrgConfig>({ orgName: '', orgCode: '', orgAddress: '', contactName: '', contactPhone: '', certNo: '', sampleNoPrefix: 'S', reportNoPrefix: 'R' })
const labTaskForm = reactive({ id: null as number|null, projectId: null as number|null, issuerName: '', notes: '' })
const deptForm = reactive({ id: null as number|null, deptName: '', deptCode: '', parentId: null as number|null, enabled: true })
const postForm = reactive({ id: null as number|null, postName: '', postCode: '', enabled: true })
const noticeForm = reactive({ id: null as number|null, title: '', content: '', noticeType: 'NOTICE', publisher: '', enabled: true })
const sysConfigForm = reactive({ id: null as number|null, configKey: '', configValue: '', configName: '' })

// ===== 新模块 API 函数 =====
async function fetchLaws() { const r = await api.get('/api/ohdp/basic/laws', { params: { page: 0, size: 100 } }); laws.value = r.data.content || [] }
async function saveLaw() {
  const payload = { title: lawForm.title, docNo: lawForm.docNo, level: lawForm.level, publishDate: lawForm.publishDate || null, effectiveDate: lawForm.effectiveDate || null, publisher: lawForm.publisher, isValid: lawForm.isValid, isOpen: lawForm.isOpen, notes: lawForm.notes }
  const { data } = lawForm.id
    ? await api.put<OhdpLaw>(`/api/ohdp/basic/laws/${lawForm.id}`, payload)
    : await api.post<OhdpLaw>('/api/ohdp/basic/laws', payload)
  if (referenceDocumentFile.value) await uploadReferenceDocument('LAW', data.id)
  clearLawForm()
  await fetchLaws()
  setMessage('法律法规已保存', 'success')
}
async function deleteLaw(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/laws/${id}`); fetchLaws() }
async function editLaw(item: OhdpLaw) {
  Object.assign(lawForm, { id: item.id, title: item.title || '', docNo: item.docNo || '', level: item.level || 'NATIONAL', publishDate: item.publishDate || '', effectiveDate: item.effectiveDate || '', publisher: item.publisher || '', isValid: item.isValid !== false, isOpen: item.isOpen !== false, notes: item.notes || '' })
  await fetchReferenceDocuments('LAW', item.id)
}

function clearLawForm() {
  Object.assign(lawForm, { id: null, title: '', docNo: '', level: 'NATIONAL', publishDate: '', effectiveDate: '', publisher: '', isValid: true, isOpen: true, notes: '' })
  clearReferenceDocumentSelection()
}

async function fetchStandards() { const r = await api.get('/api/ohdp/basic/standards', { params: { page: 0, size: 100 } }); standards.value = r.data.content || [] }
async function saveStandard() {
  const payload = { standardName: standardForm.standardName, standardCode: standardForm.standardCode, level: standardForm.level, useScope: standardForm.useScope, publishDate: standardForm.publishDate || null, isValid: standardForm.isValid, isOpen: standardForm.isOpen, notes: standardForm.notes }
  const { data } = standardForm.id
    ? await api.put<OhdpStandard>(`/api/ohdp/basic/standards/${standardForm.id}`, payload)
    : await api.post<OhdpStandard>('/api/ohdp/basic/standards', payload)
  if (referenceDocumentFile.value) await uploadReferenceDocument('STANDARD', data.id)
  clearStandardForm()
  await fetchStandards()
  setMessage('标准规范已保存', 'success')
}
async function deleteStandard(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/standards/${id}`); fetchStandards() }
async function editStandard(item: OhdpStandard) {
  Object.assign(standardForm, { id: item.id, standardName: item.standardName || '', standardCode: item.standardCode || '', level: item.level || 'NATIONAL', useScope: item.useScope || '', publishDate: item.publishDate || '', isValid: item.isValid !== false, isOpen: item.isOpen !== false, notes: item.notes || '' })
  await fetchReferenceDocuments('STANDARD', item.id)
}

function clearStandardForm() {
  Object.assign(standardForm, { id: null, standardName: '', standardCode: '', level: 'NATIONAL', useScope: '', publishDate: '', isValid: true, isOpen: true, notes: '' })
  clearReferenceDocumentSelection()
}

function onReferenceDocumentSelected(event: Event) {
  const input = event.target as HTMLInputElement
  referenceDocumentFile.value = input.files?.[0] || null
}

function clearReferenceDocumentSelection() {
  referenceDocuments.value = []
  referenceDocumentFile.value = null
  referenceVersionLabel.value = ''
  referenceSourceUrl.value = ''
  referenceDocumentInputKey.value += 1
}

async function fetchReferenceDocuments(type: 'LAW' | 'STANDARD', id: number) {
  const { data } = await api.get<ReferenceDocument[]>(`/api/ohdp/basic/${type}/${id}/documents`)
  referenceDocuments.value = data || []
  referenceDocumentFile.value = null
  referenceVersionLabel.value = ''
  referenceSourceUrl.value = ''
  referenceDocumentInputKey.value += 1
}

async function uploadReferenceDocument(type: 'LAW' | 'STANDARD', id: number) {
  if (!referenceDocumentFile.value) return
  actionBusy.uploadReferenceDocument = true
  try {
    const form = new FormData()
    form.append('file', referenceDocumentFile.value)
    if (referenceVersionLabel.value.trim()) form.append('versionLabel', referenceVersionLabel.value.trim())
    if (referenceSourceUrl.value.trim()) form.append('sourceUrl', referenceSourceUrl.value.trim())
    await api.post(`/api/ohdp/basic/${type}/${id}/documents`, form)
  } finally {
    actionBusy.uploadReferenceDocument = false
  }
}

async function openReferenceDocument(item: ReferenceDocument, download = false) {
  try {
    const { data } = await api.get(`/api/ohdp/basic/documents/${item.id}/content`, { params: { download }, responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([data], { type: item.contentType }))
    if (download) {
      const link = document.createElement('a')
      link.href = url
      link.download = item.originalName
      link.click()
      URL.revokeObjectURL(url)
      return
    }
    window.open(url, '_blank', 'noopener,noreferrer')
    window.setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (err: any) {
    setMessage(err?.response?.data?.message || '法规原文打开失败', 'error')
  }
}

async function fetchRegions() { const r = await api.get('/api/ohdp/basic/regions'); regions.value = r.data || [] }
async function saveRegion() { if (regionForm.id) { await api.put(`/api/ohdp/basic/regions/${regionForm.id}`, { ...regionForm }) } else { await api.post('/api/ohdp/basic/regions', { ...regionForm }) }; Object.assign(regionForm, { id: null, parentId: null, regionCode: '', regionName: '', level: 1, sortNo: 0 }); fetchRegions() }
async function deleteRegion(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/basic/regions/${id}`); fetchRegions() }
function editRegion(item: OhdpRegion) { Object.assign(regionForm, item) }

async function fetchDevices() { const r = await api.get('/api/ohdp/devices', { params: { page: 0, size: 100 } }); devices.value = r.data.content || [] }
async function saveDevice() { if (deviceForm.id) { await api.put(`/api/ohdp/devices/${deviceForm.id}`, { ...deviceForm,calibrationDate:deviceForm.calibrationDate || null,calibrationExpiry:deviceForm.calibrationExpiry || null }) } else { await api.post('/api/ohdp/devices', { ...deviceForm,calibrationDate:deviceForm.calibrationDate || null,calibrationExpiry:deviceForm.calibrationExpiry || null }) }; Object.assign(deviceForm, { id: null, deviceName: '', deviceType: 'LAB_INSTRUMENT', deviceModel: '', deviceNo: '', status: 'NORMAL', notes: '', certificateNo:'',calibrationDate:'',calibrationExpiry:'' }); fetchDevices() }
async function deleteDevice(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/devices/${id}`); fetchDevices() }
function editDevice(item: OhdpDevice) { Object.assign(deviceForm, item) }

async function fetchHazardFactors() { const r = await api.get('/api/ohdp/hazard-factors', { params: { page: 0, size: 200 } }); hazardFactors.value = r.data.content || [] }
async function saveHazardFactor() { if (hazardFactorForm.id) { await api.put(`/api/ohdp/hazard-factors/${hazardFactorForm.id}`, { ...hazardFactorForm }) } else { await api.post('/api/ohdp/hazard-factors', { ...hazardFactorForm }) }; Object.assign(hazardFactorForm, { id: null, factorName: '', factorCategory: 'CHEMICAL', factorCode: '', casNo: '', enabled: true }); fetchHazardFactors() }
async function deleteHazardFactor(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/hazard-factors/${id}`); fetchHazardFactors() }
function editHazardFactor(item: OhdpHazardFactor) { Object.assign(hazardFactorForm, item) }

async function fetchFactorLimits(factorId: number) {
  const r = await api.get(`/api/ohdp/hazard-factors/${factorId}/limits`)
  factorLimits.value = r.data || []
}
function viewFactorLimits(item: OhdpHazardFactor) {
  selectedHazardFactorId.value = item.id
  selectedHazardFactorName.value = item.factorName
  fetchFactorLimits(item.id)
  resetLimitForm()
}
async function saveFactorLimit() {
  if (!selectedHazardFactorId.value) return
  const payload = { limitType: limitForm.limitType, limitValue: limitForm.limitValue === '' ? null : Number(limitForm.limitValue), limitUnit: limitForm.limitUnit, standardCode: limitForm.standardCode, notes: limitForm.notes }
  try {
    if (limitForm.id) { await api.put(`/api/ohdp/hazard-factor-limits/${limitForm.id}`, payload) }
    else { await api.post(`/api/ohdp/hazard-factors/${selectedHazardFactorId.value}/limits`, payload) }
    resetLimitForm()
    fetchFactorLimits(selectedHazardFactorId.value)
  } catch (e: any) { alert(e?.response?.data?.message || '保存失败') }
}
async function deleteFactorLimit(limitId: number) {
  if (!confirm('确认删除？')) return
  await api.delete(`/api/ohdp/hazard-factor-limits/${limitId}`)
  if (selectedHazardFactorId.value) fetchFactorLimits(selectedHazardFactorId.value)
}
function editFactorLimit(item: OhdpHazardFactorLimit) {
  Object.assign(limitForm, { id: item.id, limitType: item.limitType, limitValue: item.limitValue != null ? String(item.limitValue) : '', limitUnit: item.limitUnit || 'mg/m3', standardCode: item.standardCode || '', notes: item.notes || '' })
}
function resetLimitForm() { Object.assign(limitForm, { id: null, limitType: 'PC_TWA', limitValue: '', limitUnit: 'mg/m3', standardCode: '', notes: '' }) }

async function fetchSdsList() { const r = await api.get('/api/ohdp/sds', { params: { page: 0, size: 100 } }); sdsList.value = r.data.content || [] }
async function saveSds() { if (sdsForm.id) { await api.put(`/api/ohdp/sds/${sdsForm.id}`, { ...sdsForm }) } else { await api.post('/api/ohdp/sds', { ...sdsForm }) }; Object.assign(sdsForm, { id: null, substanceName: '', casNo: '', factorId: null, version: '', notes: '' }); fetchSdsList() }
async function deleteSds(id: number) { if (!confirm('确认删除？')) return; await api.delete(`/api/ohdp/sds/${id}`); fetchSdsList() }
function editSds(item: OhdpSds) { Object.assign(sdsForm, item) }

async function fetchDetectionMethods() { const r = await api.get('/api/ohdp/config/detection-methods', { params: { page: 0, size: 100 } }); detectionMethods.value = r.data.content || [] }
async function saveDetectionMethod() { if (detectionMethodForm.id) { await api.put(`/api/ohdp/config/detection-methods/${detectionMethodForm.id}`, { ...detectionMethodForm }) } else { await api.post('/api/ohdp/config/detection-methods', { ...detectionMethodForm }) }; Object.assign(detectionMethodForm, { id: null, methodName: '', methodCode: '', methodAbbr: '', formula: '', standardCode: '', enabled: true }); fetchDetectionMethods() }
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

// ===== 用户管理 =====
async function fetchUsers() {
  const res = await api.get('/api/users', { params: { username: userQuery.username || undefined, page: userQuery.page, size: userQuery.size } })
  users.value = res.data.content
  totalUsers.value = res.data.totalElements
}

function clearUserForm() {
  userForm.id = null
  userForm.username = ''
  userForm.password = ''
  userForm.employeeId = null
  userForm.role = 'USER'
  userForm.status = 'ACTIVE'
}

function editUser(item: SysUser) {
  userForm.id = item.id
  userForm.username = item.username
  userForm.password = ''
  userForm.employeeId = item.employeeId ?? null
  userForm.role = item.role
  userForm.status = item.status
}

async function saveUser() {
  if (!userForm.username.trim()) { setMessage('用户名不能为空', 'error'); return }
  if (!userForm.id && !userForm.password.trim()) { setMessage('新建用户需填写密码', 'error'); return }
  try {
    const payload: Record<string, unknown> = {
      username: userForm.username,
      role: userForm.role,
      status: userForm.status,
      employeeId: userForm.employeeId || undefined,
    }
    if (userForm.password) payload.password = userForm.password
    if (userForm.id) {
      await api.put(`/api/users/${userForm.id}`, payload)
      setMessage('用户更新成功', 'success')
    } else {
      await api.post('/api/users', payload)
      setMessage('用户创建成功', 'success')
    }
    clearUserForm()
    await fetchUsers()
  } catch (e: unknown) {
    setMessage('操作失败：' + ((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(e)), 'error')
  }
}

async function deleteUser(id: number) {
  if (!confirm('确认删除该用户？')) return
  try {
    await api.delete(`/api/users/${id}`)
    setMessage('删除成功', 'success')
    await fetchUsers()
  } catch { setMessage('删除失败', 'error') }
}

function openResetDialog(item: SysUser) {
  resetPasswordForm.userId = item.id
  resetPasswordForm.username = item.username
  resetPasswordForm.newPassword = ''
  showResetDialog.value = true
}

async function doResetPassword() {
  if (!resetPasswordForm.newPassword.trim()) { setMessage('请输入新密码', 'error'); return }
  try {
    await api.post(`/api/users/${resetPasswordForm.userId}/admin-reset-password`, { initialPassword: resetPasswordForm.newPassword })
    setMessage(`用户 ${resetPasswordForm.username} 密码已重置`, 'success')
    showResetDialog.value = false
  } catch { setMessage('重置失败', 'error') }
}

async function openRoleAssignDialog(user: SysUser) {
  assigningUserId.value = user.id
  assigningUsername.value = user.username
  selectedRoleIds.value = []
  showRoleAssignDialog.value = true
  try {
    // 加载所有角色（用于展示复选框）
    const allRes = await api.get('/api/system/roles', { params: { page: 0, size: 200 } })
    roles.value = allRes.data.content ?? allRes.data
    // 加载用户已有角色
    const userRolesRes = await api.get(`/api/system/users/${user.id}/roles`)
    selectedRoleIds.value = (userRolesRes.data as number[]) ?? []
  } catch { setMessage('加载角色失败', 'error') }
}

async function saveUserRoles() {
  if (assigningUserId.value == null) return
  try {
    await api.post(`/api/system/users/${assigningUserId.value}/roles`, { ids: selectedRoleIds.value })
    setMessage(`用户 ${assigningUsername.value} 角色已保存`, 'success')
    showRoleAssignDialog.value = false
  } catch { setMessage('保存角色失败', 'error') }
}

// ===== 角色管理 =====
async function fetchRoles() {
  const res = await api.get('/api/system/roles', { params: { roleCode: roleQuery.roleCode || undefined, roleName: roleQuery.roleName || undefined, page: roleQuery.page, size: roleQuery.size } })
  roles.value = res.data.content
  totalRoles.value = res.data.totalElements
}

function clearRoleForm() {
  roleForm.id = null
  roleForm.roleCode = ''
  roleForm.roleName = ''
  roleForm.remark = ''
  roleForm.status = 'ACTIVE'
}

function editRole(item: SysRole) {
  roleForm.id = item.id
  roleForm.roleCode = item.roleCode
  roleForm.roleName = item.roleName
  roleForm.remark = item.remark ?? ''
  roleForm.status = item.status
}

async function saveRole() {
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) { setMessage('角色编码和名称不能为空', 'error'); return }
  try {
    const payload = { roleCode: roleForm.roleCode, roleName: roleForm.roleName, remark: roleForm.remark, status: roleForm.status }
    if (roleForm.id) {
      await api.put(`/api/system/roles/${roleForm.id}`, payload)
      setMessage('角色更新成功', 'success')
    } else {
      await api.post('/api/system/roles', payload)
      setMessage('角色创建成功', 'success')
    }
    clearRoleForm()
    await fetchRoles()
  } catch (e: unknown) {
    setMessage('操作失败：' + ((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? String(e)), 'error')
  }
}

async function deleteRole(id: number) {
  if (!confirm('确认删除该角色？')) return
  try {
    await api.delete(`/api/system/roles/${id}`)
    setMessage('删除成功', 'success')
    await fetchRoles()
  } catch { setMessage('删除失败', 'error') }
}

// ===== 角色分配权限 =====
async function openRolePermDialog(role: SysRole) {
  assigningRoleId.value = role.id
  assigningRoleName.value = role.roleName
  selectedPermIds.value = []
  showRolePermDialog.value = true
  try {
    const [allRes, rolePermsRes] = await Promise.all([
      api.get('/api/system/permissions', { params: { page: 0, size: 500 } }),
      api.get(`/api/system/roles/${role.id}/permissions`),
    ])
    permissions.value = allRes.data.content ?? allRes.data
    selectedPermIds.value = Array.from((rolePermsRes.data as Set<number>) ?? [])
  } catch { setMessage('加载权限失败', 'error') }
}

async function saveRolePerms() {
  if (assigningRoleId.value == null) return
  try {
    await api.post(`/api/system/roles/${assigningRoleId.value}/permissions`, { ids: selectedPermIds.value })
    setMessage(`角色 ${assigningRoleName.value} 权限已保存`, 'success')
    showRolePermDialog.value = false
  } catch { setMessage('保存失败', 'error') }
}

// ===== 菜单管理 =====
async function fetchMenuTree() {
  try {
    const res = await api.get('/api/system/menus/tree')
    menuTree.value = res.data
  } catch { setMessage('加载菜单失败', 'error') }
}

function editMenu(item: SysMenu) {
  menuForm.id = item.id
  menuForm.permCode = item.permCode
  menuForm.permName = item.permName
  menuForm.parentId = item.parentId ?? null
  menuForm.sortNo = item.sortNo ?? null
  menuForm.status = item.status
}

function clearMenuForm() {
  menuForm.id = null
  menuForm.permCode = ''
  menuForm.permName = ''
  menuForm.parentId = null
  menuForm.sortNo = null
  menuForm.status = 'ACTIVE'
}

async function saveMenu() {
  if (!menuForm.permCode.trim() || !menuForm.permName.trim()) {
    setMessage('权限码和菜单名称不能为空', 'error')
    return
  }
  const payload = {
    permCode: menuForm.permCode,
    permName: menuForm.permName,
    parentId: menuForm.parentId || null,
    sortNo: menuForm.sortNo,
    status: menuForm.status,
  }
  try {
    if (menuForm.id) {
      await api.put(`/api/system/menus/${menuForm.id}`, payload)
      setMessage('菜单已更新', 'success')
    } else {
      await api.post('/api/system/menus', payload)
      setMessage('菜单已创建', 'success')
    }
    clearMenuForm()
    await fetchMenuTree()
  } catch (err: any) { setMessage(resolveApiMessage(err, '保存失败'), 'error') }
}

async function deleteMenu(id: number) {
  if (!confirm('确认删除该菜单？子菜单也将受影响。')) return
  try {
    await api.delete(`/api/system/menus/${id}`)
    setMessage('删除成功', 'success')
    await fetchMenuTree()
  } catch { setMessage('删除失败', 'error') }
}

// ===== 权限管理 =====
async function fetchPermissions() {
  try {
    const res = await api.get('/api/system/permissions', {
      params: {
        permCode: permQuery.permCode || undefined,
        permName: permQuery.permName || undefined,
        page: permQuery.page,
        size: permQuery.size,
      },
    })
    permissions.value = res.data.content
    totalPermissions.value = res.data.totalElements
  } catch { setMessage('加载权限失败', 'error') }
}

function editPermission(item: SysPermission) {
  permForm.id = item.id
  permForm.permCode = item.permCode
  permForm.permName = item.permName
  permForm.permType = item.permType
  permForm.apiMethod = item.apiMethod ?? ''
  permForm.apiPath = item.apiPath ?? ''
  permForm.parentId = item.parentId ?? null
  permForm.sortNo = item.sortNo ?? null
  permForm.status = item.status
}

function clearPermForm() {
  permForm.id = null
  permForm.permCode = ''
  permForm.permName = ''
  permForm.permType = 'API'
  permForm.apiMethod = ''
  permForm.apiPath = ''
  permForm.parentId = null
  permForm.sortNo = null
  permForm.status = 'ACTIVE'
}

async function savePermission() {
  if (!permForm.permCode.trim() || !permForm.permName.trim()) {
    setMessage('权限码和名称不能为空', 'error')
    return
  }
  const payload = {
    permCode: permForm.permCode,
    permName: permForm.permName,
    permType: permForm.permType,
    apiMethod: permForm.apiMethod || null,
    apiPath: permForm.apiPath || null,
    parentId: permForm.parentId || null,
    sortNo: permForm.sortNo,
    status: permForm.status,
  }
  try {
    if (permForm.id) {
      await api.put(`/api/system/permissions/${permForm.id}`, payload)
      setMessage('权限已更新', 'success')
    } else {
      await api.post('/api/system/permissions', payload)
      setMessage('权限已创建', 'success')
    }
    clearPermForm()
    await fetchPermissions()
  } catch (err: any) { setMessage(resolveApiMessage(err, '保存失败'), 'error') }
}

async function deletePermission(id: number) {
  if (!confirm('确认删除该权限？')) return
  try {
    await api.delete(`/api/system/permissions/${id}`)
    setMessage('删除成功', 'success')
    await fetchPermissions()
  } catch { setMessage('删除失败', 'error') }
}

// 将菜单树拍平（用于父菜单下拉选择）
function flattenMenuTree(tree: SysMenu[], result: SysMenu[] = []): SysMenu[] {
  for (const node of tree) {
    result.push(node)
    if (node.children?.length) flattenMenuTree(node.children, result)
  }
  return result
}

onMounted(async () => {
  window.addEventListener('ohdp:open-context-help', handleContextHelpEvent)
  activateStandaloneHelpRequest()
  if (token.value) {
    if (!readSessionValue('ohdp_username') || !readSessionValue('ohdp_role')) {
      token.value = ''
      clearStoredSession()
      setMessage('界面已升级，请重新登录以确认账号权限', 'info')
      return
    }
    await initAfterLogin()
  }
})

onBeforeUnmount(() => window.removeEventListener('ohdp:open-context-help', handleContextHelpEvent))
</script>

<template>
  <div class="app-shell">
    <header class="topbar" :class="{ 'topbar-login': !isLoggedIn }">
      <div class="brand-lockup">
        <span class="brand-mark" aria-hidden="true"><i></i><i></i></span>
        <div>
          <h1>职业危害与放射诊疗检测数据平台</h1>
          <p>10月14日补齐版 · <a href="/test1013-changes.html" target="_blank" rel="noopener" style="color:inherit">查看改动记录</a></p>
        </div>
      </div>
      <div v-if="isLoggedIn" class="topbar-context">
        <div><span>当前模块</span><strong>{{ menuMeta.title }}</strong></div>
        <div>
          <span>{{ activeTab === 'project' ? '当前项目' : '工作对象' }}</span>
          <strong>{{ activeTab === 'project' ? (selectedProject?.projectCode || '未选择') : menuMeta.title }}</strong>
        </div>
        <div>
          <span>流转状态</span>
          <strong>{{ activeTab === 'project' ? (selectedProject ? statusText[selectedProject.status] : '待建档') : '模块就绪' }}</strong>
        </div>
      </div>
      <div class="topbar-right">
        <span v-if="loading" class="loading">处理中...</span>
        <div v-if="isLoggedIn" class="operator-chip" :title="operatorRoleLabel">
          <span class="operator-avatar">{{ operatorAvatar }}</span>
          <span>{{ operatorLabel }} · {{ operatorRoleLabel }}</span>
        </div>
        <button v-if="isLoggedIn" class="ghost" @click="logout">退出系统</button>
      </div>
    </header>

    <p v-if="message" class="message" :class="messageType">{{ message }}</p>

    <main v-if="!isLoggedIn" class="login-stage">
      <section class="login-intro">
        <span class="intro-seal">受控数据</span>
        <h2>从原始记录到检测报告，<br />每一步都可追溯。</h2>
        <p>面向检测人员与报告人员的统一工作台，集中完成项目建档、数据录入、自动计算、审核批准和报告归档。</p>
        <ol class="login-flow" aria-label="检测业务流程">
          <li><strong>01</strong><span>项目建档</span></li>
          <li><strong>02</strong><span>数据录入</span></li>
          <li><strong>03</strong><span>计算判定</span></li>
          <li><strong>04</strong><span>审核报告</span></li>
        </ol>
      </section>
      <form class="card login-card" @submit.prevent="login">
        <div class="login-heading">
          <span>内部业务系统</span>
          <h2>登录工作台</h2>
          <p>请输入管理员分配的账号与密码。</p>
        </div>
        <div class="login-fields">
          <label>
            账号
            <input v-model="loginForm.username" autocomplete="username" placeholder="请输入账号" />
          </label>
          <label>
            密码
            <input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
          </label>
        </div>
        <label class="remember-session">
          <input v-model="loginForm.rememberMe" type="checkbox" />
          <span><strong>保持登录 30 天 <HelpMark anchor-id="session.remember" /></strong><small>浏览器不会保存明文密码</small></span>
        </label>
        <button class="login-submit" type="submit">登陆</button>
        <p class="login-note">系统操作会记录账号、时间和关键数据变更。</p>
      </form>
    </main>

    <section v-else class="admin-layout">
      <aside class="card menu-tree pro-menu">
        <div class="menu-header">
          <div><span>业务导航</span><h2>样品全流程 <HelpMark anchor-id="navigation.structure" tone="inverse" /></h2></div>
        </div>
        <el-menu
          :key="sideMenuRenderKey"
          :default-active="selectedMenuKey"
          class="el-side-menu"
          unique-opened
          @select="(index: string) => selectMenu(index as MenuKey)"
        >
          <el-sub-menu index="project-group">
            <template #title>项目与报告</template>
            <el-menu-item index="project.workspace">项目工作台</el-menu-item>
            <el-menu-item index="project.report">报告管理</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="radiology-group">
            <template #title>放射诊疗检测</template>
            <el-menu-item index="radiology.dr">DR 质量控制</el-menu-item>
            <el-menu-item index="radiology.ct">CT 质量控制</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="labManage-group">
            <template #title>实验检测</template>
            <el-menu-item index="labManage.task">检测任务单</el-menu-item>
            <el-menu-item index="labManage.result">结果计算与复核</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="unit-people-group">
            <template #title>单位与人员</template>
            <el-menu-item index="maintenance.company">受检单位</el-menu-item>
            <el-menu-item index="maintenance.staff">人员资质</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="foundation-group">
            <template #title>检测基础库</template>
            <el-menu-item index="basicData.law">法律法规</el-menu-item>
            <el-menu-item index="basicData.standard">标准规范</el-menu-item>
            <el-menu-item index="basicData.device">检测仪器设备</el-menu-item>
            <el-menu-item index="hazardFactor.factor">危害因素库</el-menu-item>
            <el-menu-item index="hazardFactor.sds">SDS 说明书</el-menu-item>
            <el-menu-item index="basicData.region">行政区划</el-menu-item>
          </el-sub-menu>

          <el-sub-menu index="rules-template-group">
            <template #title>业务规则与模板</template>
            <el-menu-item index="maintenance.position">岗位模板</el-menu-item>
            <el-menu-item index="maintenance.factor">岗位危害因素模板</el-menu-item>
            <el-menu-item index="maintenance.reportText">报告文本模板</el-menu-item>
            <el-menu-item index="configCenter.detectMethod">检测方法</el-menu-item>
            <el-menu-item index="configCenter.detectType">检测分类</el-menu-item>
            <el-menu-item index="maintenance.dict">业务字典</el-menu-item>
            <el-menu-item index="configCenter.orgConfig">机构与编号规则</el-menu-item>
          </el-sub-menu>

          <el-menu-item index="feedback" class="feedback-menu-entry">
            <span class="feedback-menu-dot" aria-hidden="true"></span>
            <span>意见反馈</span>
          </el-menu-item>

          <el-sub-menu v-if="isAdmin" index="system-group" class="system-menu-group">
            <template #title>系统管理</template>
            <el-menu-item index="system.user">用户管理</el-menu-item>
            <el-menu-item index="system.role">角色管理</el-menu-item>
            <el-menu-item index="system.permission">权限管理</el-menu-item>
            <el-menu-item index="system.menu">菜单管理</el-menu-item>
            <el-menu-item index="system.dept">部门管理</el-menu-item>
            <el-menu-item index="system.post">岗位管理</el-menu-item>
            <el-menu-item index="system.notice">通知公告</el-menu-item>
            <el-menu-item index="system.sysfile">文件管理</el-menu-item>
            <el-menu-item index="system.syslog">操作日志</el-menu-item>
            <el-menu-item index="system.sysconfig">参数配置</el-menu-item>
          </el-sub-menu>

          <div class="utility-menu-divider" role="separator" aria-label="系统辅助功能"></div>
          <el-menu-item index="help" class="help-menu-entry">
            <span class="help-menu-mark" aria-hidden="true">?</span>
            <span>帮助中心</span>
          </el-menu-item>
        </el-menu>
        <ManualDownloads compact />
      </aside>

      <div class="admin-content">
        <section class="workspace-heading">
          <div>
            <div class="workspace-title-row">
              <h2>{{ menuMeta.title }}</h2>
              <button
                v-if="activeTab !== 'help'"
                type="button"
                class="context-help-button"
                :title="`查看${menuMeta.title}帮助`"
                :aria-label="`查看${menuMeta.title}帮助`"
                @click="openHelpInNewTab(selectedMenuKey)"
              >?</button>
            </div>
            <span>{{ menuMeta.description }}</span>
          </div>
          <div class="workspace-heading-meta">
            <span>数据状态</span>
            <strong>{{ loading ? '正在同步' : '已连接' }}</strong>
          </div>
        </section>
        <nav v-if="activeTab === 'project'" class="sample-flow" aria-label="项目流转阶段">
          <HelpMark class="sample-flow-help" anchor-id="workflow.stages" />
          <button v-for="(item, index) in ['项目设置','数据录入','自动计算','待复核','报告生成']" :key="item" type="button" :class="{ active: projectFlowStage === index + 1, done: projectFlowStage > index + 1 }">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <strong>{{ item }}</strong>
          </button>
        </nav>
    <HelpCenter
      v-if="activeTab === 'help'"
      :active-topic-id="helpTopicId"
      :active-anchor-id="helpActiveAnchorId"
      :return-label="helpReturnLabel"
      :standalone="standaloneHelpPage"
      @select-topic="selectHelpTopic"
      @select-anchor="selectHelpAnchor"
      @return="returnFromHelp"
    />
    <section v-if="activeTab === 'project'" class="project-workbench" :data-child-menu="projectChildMenu">
      <section class="card project-select-card">
        <div class="project-queue-heading">
          <h2>我的工作队列 <HelpMark anchor-id="project.create" /></h2>
          <span>按状态快速查看当前待办 <HelpMark anchor-id="status.chinese" /></span>
        </div>
        <div class="project-queue-filter" aria-label="项目待办状态筛选">
          <button type="button" :class="{ active: query.status === '' }" @click="applyProjectQueue('')">全部项目</button>
          <button type="button" :class="{ active: query.status === 'DRAFT' }" @click="applyProjectQueue('DRAFT')">待补充</button>
          <button type="button" :class="{ active: query.status === 'DATA_FILLED' }" @click="applyProjectQueue('DATA_FILLED')">待计算</button>
          <button type="button" :class="{ active: query.status === 'JUDGED' }" @click="applyProjectQueue('JUDGED')">待出报告</button>
          <button type="button" :class="{ active: query.status === 'REPORT_READY' }" @click="applyProjectQueue('REPORT_READY')">已出报告</button>
        </div>
        <h3 class="project-search-title">项目查询</h3>
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
        <div class="actions compact">
          <button class="danger" :disabled="!canBatchDelete || actionBusy.deleteProject" @click="batchDeleteProjects">
            {{ actionBusy.deleteProject ? '删除中...' : `批量删除(${selectedProjectRows.length})` }}
          </button>
          <HelpMark anchor-id="project.delete" />
        </div>
        <p class="small order-hint">{{ listOrderHint }}</p>
        <p class="small">共 {{ totalProjects }} 条，当前：{{ selectedProject?.projectCode || '未选择项目' }}</p>
        <p v-if="selectedProject" class="current-project">
          当前项目：<strong>{{ selectedProject.projectCode }}</strong>
          <span>状态：{{ statusText[selectedProject.status] }}</span>
          <span v-if="lastManualSaveAt">最近保存：{{ lastManualSaveAt }}</span>
        </p>
        <div v-else-if="lastCreatedProjectCode" class="created-tip">
          <span>最近创建：<strong>{{ lastCreatedProjectCode }}</strong></span>
          <span v-if="lastCreatedProjectStatus">状态：{{ latestCreatedStatusLabel() }}</span>
          <button class="ghost tiny" @click="selectLastCreatedProject">选中最近创建</button>
        </div>
        <el-table
          :data="projects"
          height="260"
          stripe
          border
          highlight-current-row
          :row-key="(row: Project) => row.id"
          @row-click="onProjectRowClick"
          @selection-change="onProjectSelectionChange"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="projectCode" label="项目编号" min-width="220" />
          <el-table-column prop="createdAt" label="创建时间" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="companyName" label="单位名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="projectStatusTagType(row.status as ProjectStatus)" size="small">{{ statusText[row.status as ProjectStatus] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <button class="ghost tiny" :disabled="!canListRowEdit" @click.stop="editProjectFromList(row as Project)">编辑</button>
              <button class="ghost tiny" :disabled="!canListRowCopy" @click.stop="copyProjectFromList((row as Project).id)">复制</button>
              <button class="danger tiny" :disabled="!canListRowDelete" @click.stop="deleteProjectFromList((row as Project).id)">删除</button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="card">
        <div class="detail-status">
          <span class="label">操作状态</span>
          <el-tag :type="operating ? 'warning' : 'info'" size="large">{{ operating ? '操作中' : '非操作中' }}</el-tag>
          <span class="empty">模式：{{ operationType || '-' }}</span>
        </div>
        <div class="detail-status">
          <span class="label">项目状态 <HelpMark anchor-id="project.status" /></span>
          <el-tag
            v-if="selectedProject"
            :type="projectStatusTagType(selectedProject.status)"
            size="large"
          >
            {{ statusText[selectedProject.status] }}（{{ selectedProject.projectCode }}）
          </el-tag>
          <span v-else class="empty">未选择项目</span>
          <button v-if="!selectedProject && lastCreatedProjectCode" class="ghost tiny" @click="selectLastCreatedProject">选中最近创建</button>
        </div>
        <el-tabs v-model="projectWorkTab" class="work-tabs">
          <el-tab-pane label="项目信息" name="base">
            <fieldset class="op-fieldset" :disabled="!operating">
            <el-form label-position="top" class="pro-form">
              <el-row :gutter="14">
                <el-col :xs="24" :md="12">
                  <el-form-item>
                    <template #label>受检单位（单位库） <HelpMark anchor-id="project.company" /></template>
                    <select id="project-company-profile" v-model="projectForm.companyProfileId" @change="applyCompanyProfile(String(projectForm.companyProfileId || ''))">
                      <option :value="null">请选择单位</option>
                      <option v-for="item in companyProfiles" :key="item.id" :value="item.id">{{ item.companyName }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item>
                    <template #label>检测类别（字典） <HelpMark anchor-id="project.detect-type" /></template>
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
                  <el-form-item>
                    <template #label>采样日期（默认当天） <HelpMark anchor-id="project.dates" /></template>
                    <input v-model="projectForm.samplingDate" type="date" />
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="12">
                  <el-form-item>
                    <template #label>报告日期（默认当天） <HelpMark anchor-id="project.dates" /></template>
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
                      id="project-submit-unit"
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
                      id="project-entrust-unit"
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
            </fieldset>
          </el-tab-pane>

          <el-tab-pane label="报告补充" name="supplement">
            <fieldset class="op-fieldset" :disabled="!operating">
            <el-form label-position="top" class="pro-form">
              <el-row :gutter="14">
                <el-col :xs="24" :md="8">
                  <el-form-item>
                    <template #label>编写人（人员资质） <HelpMark anchor-id="project.staff" /></template>
                    <select v-model="projectForm.writerStaffId" @change="applyStaffSelection('writer', String(projectForm.writerStaffId || ''))">
                      <option :value="null">请选择编写人</option>
                      <option v-for="item in staffProfiles" :key="item.id" :value="item.id">{{ item.staffName }} / {{ item.certNo }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="8">
                  <el-form-item>
                    <template #label>审核人（人员资质） <HelpMark anchor-id="project.staff" /></template>
                    <select v-model="projectForm.reviewerStaffId" @change="applyStaffSelection('reviewer', String(projectForm.reviewerStaffId || ''))">
                      <option :value="null">请选择审核人</option>
                      <option v-for="item in staffProfiles" :key="item.id" :value="item.id">{{ item.staffName }} / {{ item.certNo }}</option>
                    </select>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :md="8">
                  <el-form-item>
                    <template #label>签发人（人员资质） <HelpMark anchor-id="project.staff" /></template>
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
            <ReportSupplementEditor v-model="projectForm.reportSupplement" />
            </fieldset>
          <p class="small">提示：优先通过主数据下拉选择；“北海定期检测报告内容”中的项目将按 2026 年北海正式模板写入报告，红色模板说明不会出现在正式报告中。</p>
          </el-tab-pane>

          <el-tab-pane label="岗位信息" name="position" :disabled="!selectedProject">
            <div v-if="selectedProject">
              <el-form label-position="top" class="pro-form">
                <el-row :gutter="14">
                  <el-col :xs="24" :md="8">
                    <el-form-item>
                      <template #label>岗位模板 <HelpMark anchor-id="project.position" /></template>
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
                <button :disabled="!canProjectOperate || loading || actionBusy.addPosition" @click="addPosition">{{ actionBusy.addPosition ? '添加中...' : '添加岗位' }}</button>
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
                    <td><button class="danger tiny" :disabled="!canProjectOperate" @click="removePosition(item.id)">删除</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="empty-state">
              <p class="small">请先在上方选择项目，或选中刚创建的项目后再录入岗位。</p>
              <div class="actions">
                <button class="ghost tiny" @click="openBaseTab">返回项目信息</button>
                <button class="ghost tiny" @click="selectLastCreatedProject">选中最近创建</button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="检测结果" name="result" :disabled="!selectedProject">
            <div v-if="selectedProject">
              <el-form label-position="top" class="pro-form">
                <el-row :gutter="14">
                  <el-col :xs="24" :md="8">
                    <el-form-item>
                      <template #label>危害因素模板 <HelpMark anchor-id="project.factor-template" /></template>
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
                  <el-col :xs="24" :md="8"><el-form-item label="指标类型"><input v-model="resultForm.metricType" list="report-metrics" /><datalist id="report-metrics"><option v-for="metric in ['TWA','STEL','MAC','PE','SILICA','LEX8H','LAEQ','PULSE_NOISE','WBGT','WBGT_LOCATION','POWER_FREQUENCY_E8','PF_E','PF_B','HF_E8','HF_E','HF_H8','HF_H','UHF','MICROWAVE_DOSE','MICROWAVE','UVA','UVB','UVC','LASER','HAV','VIBRATION_X','VIBRATION_Y','VIBRATION_Z','X_GAMMA']" :key="metric" :value="metric" /></datalist></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item><template #label>原始值 <HelpMark anchor-id="project.raw-value" /></template><input v-model="resultForm.valueRaw" placeholder="如 <0.04" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="数值"><input v-model="resultForm.valueNum" type="number" step="0.001" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="单位"><input v-model="resultForm.unit" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="限值"><input v-model="resultForm.limitValue" type="number" step="0.001" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="限值单位"><input v-model="resultForm.limitUnit" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测日期（单日也保留）"><input v-model="resultForm.measurementDate" type="date" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测部位（不涉及留空）"><input v-model="resultForm.measurementBodyPart" placeholder="如：头部、胸部、手部" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测波段（不涉及留空）"><input v-model="resultForm.measurementBand" placeholder="如：UVA 315‑400 nm" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="因素分类"><input v-model="resultForm.factorCategory" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="测点编号"><input v-model="resultForm.pointCode" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="样品编号"><input v-model="resultForm.sampleCode" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="采样/测量方式"><select v-model="resultForm.detectionMode"><option value="">请选择</option><option v-for="mode in ['个体', '定点长时间', '定点短时间', '定点', '直读']" :key="mode">{{ mode }}</option></select></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="噪声类型"><input v-model="resultForm.noiseType" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="实验室检测日期"><input v-model="resultForm.laboratoryDate" type="date" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测依据"><input v-model="resultForm.detectionBasis" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测方法"><input v-model="resultForm.detectionMethod" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="实际仪器名称及编号"><input v-model="resultForm.instrument" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检测机构"><input v-model="resultForm.testingOrganization" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="原始记录/外部报告编号"><input v-model="resultForm.sourceRecordNo" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="检出限（含单位）"><input v-model="resultForm.detectionLimit" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="定量下限（含单位）"><input v-model="resultForm.quantificationLimit" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="限值/折减/PE适用依据"><input v-model="resultForm.evaluationBasis" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="本结果超标原因"><input v-model="resultForm.overLimitReason" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="每周工作天数"><input v-model="resultForm.weeklyWorkDays" type="number" step="any" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="是否适用TWA折减"><select v-model="resultForm.reductionApplicable"><option :value="null">待确认</option><option :value="true">适用</option><option :value="false">不适用或豁免</option></select></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="PE限值倍数（专业确认）"><input v-model="resultForm.peakLimitMultiplier" type="number" step="any" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="PE每次接触分钟"><input v-model="resultForm.peakExposureMinutes" type="number" step="any" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="PE每班次数"><input v-model="resultForm.peakTimesPerShift" type="number" step="any" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="PE间隔分钟"><input v-model="resultForm.peakIntervalMinutes" type="number" step="any" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="混合接触组合编号"><input v-model="resultForm.mixedExposureGroup" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="混合接触共同健康效应依据"><input v-model="resultForm.mixedExposureBasis" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="测量条件（频率/劳动强度等）"><input v-model="resultForm.measurementConditions" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="每日接触小时"><input v-model="resultForm.dailyExposureHours" type="number" min="0" max="24" step="0.5" /></el-form-item></el-col>
                  <el-col :xs="24" :md="8"><el-form-item label="每周接触小时"><input v-model="resultForm.weeklyExposureHours" type="number" min="0" max="168" step="0.5" /></el-form-item></el-col>
                </el-row>
              </el-form>
          <div class="actions wrap">
            <button :disabled="!canProjectOperate || loading || actionBusy.addResult" @click="addResult">{{ actionBusy.addResult ? (resultForm.id ? '更新中...' : '添加中...') : (resultForm.id ? '更新结果' : '添加结果') }}</button>
            <button v-if="resultForm.id" class="ghost" @click="clearResultForm">取消编辑</button>
            <button class="ghost" :disabled="!canProjectOperate || loading || actionBusy.judgeProject" @click="judgeProject">
              {{ actionBusy.judgeProject ? '判定中...' : '自动判定' }}
            </button>
            <HelpMark anchor-id="project.auto-calc" />
            <button class="ghost" :disabled="!canProjectOperate || loading || actionBusy.precheckProject" @click="precheckCurrentProject">
              {{ actionBusy.precheckProject ? '预检中...' : '报告预检' }}
            </button>
            <HelpMark anchor-id="project.report-precheck" />
            <button class="ghost" :disabled="!canProjectOperate || loading || actionBusy.previewReport" @click="previewReport">
              {{ actionBusy.previewReport ? '预览加载中...' : '报告预览' }}
            </button>
            <HelpMark anchor-id="report.preview" />
          </div>

          <label>
            批量粘贴（每行：岗位,因素,指标,原始值,数值,单位,限值,限值单位；支持逗号/Tab/竖线） <HelpMark anchor-id="project.batch-paste" />
            <textarea v-model="batchResultText" rows="4" placeholder="焊接,锰及其无机化合物,TWA,<0.04,,mg/m3,0.15,mg/m3"></textarea>
          </label>
          <div class="actions">
            <button class="ghost" :disabled="!canProjectOperate" @click="addBatchResults">批量导入</button>
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
                <th>日期/特殊条件</th>
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
                <td>{{ item.adjustedLimitValue ?? item.limitValue }} {{ item.limitUnit }}<small v-if="item.reductionFactor && item.reductionFactor < 1"><br />折减系数 {{ item.reductionFactor }}</small></td>
                <td>{{ item.measurementDate || '—' }}<small v-if="item.measurementBodyPart || item.measurementBand"><br />{{ [item.measurementBodyPart, item.measurementBand].filter(Boolean).join(' / ') }}</small></td>
                <td>{{ codeLabel(item.judgement, '未判定') }}</td>
                <td>
                  <button class="ghost tiny" :disabled="!canProjectOperate" @click="editResult(item)">编辑</button>
                  <button class="danger tiny" :disabled="!canProjectOperate" @click="removeResult(item.id)" style="margin-left:4px">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
            </div>
            <div v-else class="empty-state">
              <p class="small">请先在上方选择项目，或选中刚创建的项目后再录入检测结果。</p>
              <div class="actions">
                <button class="ghost tiny" @click="openBaseTab">返回项目信息</button>
                <button class="ghost tiny" @click="selectLastCreatedProject">选中最近创建</button>
              </div>
            </div>
          </el-tab-pane>

        </el-tabs>
        <section v-if="selectedProject" class="report-overview-strip">
          <div>
            <strong>报告概况</strong>
            <span v-if="reports.length">最近生成：{{ formatDateTime(reports[0].generatedAt) }} · {{ reports[0].fileName }}</span>
            <span v-else>当前项目尚未生成报告</span>
          </div>
          <button class="ghost" type="button" @click="openProjectMenu('report')">进入报告管理</button>
        </section>
      </section>

      <section class="card floating-actions">
        <h2>流转状态 <HelpMark anchor-id="form.disabled-reason" /></h2>
        <dl class="flow-status-list">
          <div><dt>当前项目</dt><dd>{{ selectedProject?.projectCode || '未选择' }}</dd></div>
          <div><dt>项目状态</dt><dd>{{ selectedProject ? statusText[selectedProject.status] : '待建档' }}</dd></div>
          <div><dt>操作模式</dt><dd>{{ operating ? operationType || '编辑中' : '只读浏览' }}</dd></div>
        </dl>
        <div class="next-action-panel">
          <strong>下一步操作</strong>
          <p>{{ nextProjectAction }}</p>
        </div>
        <div class="actions wrap">
          <button :disabled="!canCreateProject" :title="canCreateProject ? '创建新项目' : '当前账号没有创建项目权限'" @click="beginCreateOperation">创建项目</button>
          <button class="primary-strong" :disabled="!canManualSave" :title="canManualSave ? '保存当前表单' : '需要先创建或编辑项目，并确认当前账号具有保存权限'" @click="saveCurrentOperation">
            {{ actionBusy.saveProject ? '保存中...' : '手动保存' }}
          </button>
          <button class="ghost" :disabled="!canClearForm" :title="canClearForm ? '清空当前未保存内容' : '当前没有可清空的编辑表单'" @click="clearOperationForm">清空表单</button>
          <button class="danger" :disabled="!canCancelCreate" :title="canCancelCreate ? '取消当前创建操作' : '只有正在创建且尚未保存的项目可以取消'" @click="cancelCreateProject">
            {{ actionBusy.cancelCreate ? '取消中...' : '取消操作' }}
          </button>
          <button class="ghost" :disabled="!selectedProject" :title="selectedProject ? '在报告管理中打开当前项目' : '需要先选择项目'" @click="openProjectMenu('report')">进入报告管理</button>
          <span class="hint" v-if="latestAction">最近操作：{{ latestAction }}</span>
        </div>
      </section>
    </section>

    <section v-else-if="activeTab === 'reportCenter'" class="report-center-layout">
      <section class="card report-center-main">
        <div class="section-title-row">
          <div>
            <h2>报告队列 <HelpMark anchor-id="report.generate" /></h2>
            <p>集中处理全部项目的预检、生成和归档，不依赖项目工作台当前选项。</p>
          </div>
          <el-tag type="info">共 {{ reportCenterTotal }} 个项目</el-tag>
        </div>
        <div class="report-center-filters">
          <label>项目编号<input v-model="reportCenterQuery.projectCode" placeholder="输入项目编号" /></label>
          <label>单位名称<input v-model="reportCenterQuery.companyName" placeholder="输入受检单位" /></label>
          <label>报告状态
            <select v-model="reportCenterQuery.reportStatus">
              <option value="">全部状态</option>
              <option value="PENDING">待生成</option>
              <option value="GENERATED">已生成</option>
            </select>
          </label>
          <label>生成日期从<input v-model="reportCenterQuery.generatedFrom" type="date" /></label>
          <label>生成日期至<input v-model="reportCenterQuery.generatedTo" type="date" /></label>
          <label class="report-generate-option" title="关闭后保留原模板中不涉及的章节和表格">
            <span>生成选项</span>
            <span class="report-generate-check">
              <input v-model="deleteNotApplicableOnGenerate" type="checkbox" />
              删除不涉及内容
            </span>
          </label>
          <button :disabled="actionBusy.reportCenter" @click="fetchReportCenter">
            {{ actionBusy.reportCenter ? '查询中...' : '查询' }}
          </button>
        </div>

        <el-table
          :data="reportCenterRows"
          border
          stripe
          highlight-current-row
          row-key="projectId"
          empty-text="没有符合条件的项目"
          @row-click="(row: ReportCenterRow) => selectReportCenterRow(row)"
        >
          <el-table-column prop="projectCode" label="项目编号" min-width="205" />
          <el-table-column prop="companyName" label="受检单位" min-width="220" show-overflow-tooltip />
          <el-table-column label="项目状态" width="120">
            <template #default="{ row }">{{ row.projectStatusLabel || codeLabel(row.projectStatus) }}</template>
          </el-table-column>
          <el-table-column label="报告状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.reportId ? 'success' : 'warning'" size="small">{{ row.reportStatusLabel || codeLabel(row.reportStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生成时间" min-width="150">
            <template #default="{ row }">{{ formatDateTime(row.generatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <button class="ghost tiny" @click.stop="selectReportCenterRow(row as ReportCenterRow)">查看</button>
              <button class="ghost tiny" :disabled="actionBusy.generateReport" @click.stop="generateReportFromCenter(row as ReportCenterRow)">
                {{ row.reportId ? '重新生成' : '生成报告' }}
              </button>
              <button v-if="row.reportId" class="ghost tiny" :disabled="actionBusy.downloadReport" @click.stop="downloadReport(row.reportId, row.fileName || `${row.projectCode}_report.doc`)">下载</button>
              <button class="ghost tiny" @click.stop="openProjectFromReport(row as ReportCenterRow)">查看项目</button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-if="reportCenterTotal > reportCenterQuery.size"
          class="report-pagination"
          layout="prev, pager, next, total"
          :total="reportCenterTotal"
          :page-size="reportCenterQuery.size"
          :current-page="reportCenterQuery.page + 1"
          @current-change="(page: number) => { reportCenterQuery.page = page - 1; fetchReportCenter() }"
        />
      </section>

      <aside class="card report-center-detail">
        <template v-if="selectedReportCenterRow">
          <div class="section-title-row">
            <div><h2>报告详情 <HelpMark anchor-id="report.preview" /> <HelpMark anchor-id="report.traceability" /></h2><p>{{ selectedReportCenterRow.projectCode }}</p></div>
            <el-tag :type="selectedReportCenterRow.reportId ? 'success' : 'warning'">{{ selectedReportCenterRow.reportStatusLabel || codeLabel(selectedReportCenterRow.reportStatus) }}</el-tag>
          </div>
          <dl class="flow-status-list">
            <div><dt>受检单位</dt><dd>{{ selectedReportCenterRow.companyName }}</dd></div>
            <div><dt>生成人</dt><dd>{{ selectedReportCenterRow.generatedBy || '尚未生成' }}</dd></div>
            <div><dt>生成时间</dt><dd>{{ formatDateTime(selectedReportCenterRow.generatedAt) }}</dd></div>
            <div><dt>报告文件</dt><dd>{{ selectedReportCenterRow.fileName || '尚未生成' }}</dd></div>
          </dl>
          <section v-if="reportCenterPreview" class="report-data-preview">
            <h3>生成前检查 <HelpMark anchor-id="project.report-precheck" /></h3>
            <p :class="reportCenterPreview.precheck.passed ? 'ok' : 'bad'">
              {{ reportCenterPreview.precheck.passed ? '预检通过，可以生成报告' : `预检未通过，缺少 ${reportCenterPreview.precheck.missingItems.length} 项` }}
            </p>
            <ul v-if="!reportCenterPreview.precheck.passed">
              <li v-for="item in reportCenterPreview.precheck.missingItems" :key="item">{{ mapMissingItem(item) }}</li>
            </ul>
            <p>岗位 {{ reportCenterPreview.positions.length }} 项，检测结果 {{ reportCenterPreview.results.length }} 项。</p>
          </section>
          <section v-if="reportCenterLogs.length" class="report-log-list">
            <h3>最近操作 <HelpMark anchor-id="report.log" /></h3>
            <ol>
              <li v-for="item in reportCenterLogs.slice(0, 5)" :key="item.id">
                <span>{{ formatDateTime(item.operatedAt) }}</span>
                <strong>{{ item.operationTypeLabel || codeLabel(item.operationType) }}</strong>
                <small>{{ item.operatorName || '系统' }}</small>
              </li>
            </ol>
          </section>
        </template>
        <div v-else class="empty-state report-center-empty">
          <strong>选择一项报告任务</strong>
          <p>右侧将显示预检结果、生成文件和最近操作记录。</p>
        </div>
      </aside>
    </section>

    <section v-else-if="activeTab === 'maintenance'" class="maintenance-grid">
      <section class="card" v-if="maintenanceChildMenu === 'company'">
        <h2>受检单位维护</h2>
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
          <label>检测依据<input v-model="factorTemplateForm.detectionBasis" /></label>
          <label>检测方法<input v-model="factorTemplateForm.detectionMethod" /></label>
          <label>仪器/设备及编号<input v-model="factorTemplateForm.detectionInstruments" /></label>
          <label>备注<input v-model="factorTemplateForm.notes" /></label>
        </div>
        <div class="actions">
          <button @click="saveFactorTemplate">{{ factorTemplateForm.id ? '更新因素模板' : '新增因素模板' }}</button>
          <button class="ghost" @click="clearFactorTemplateForm">清空</button>
          <button class="ghost" @click="fetchFactorTemplates">刷新</button>
        </div>
        <table>
          <thead><tr><th>因素</th><th>指标</th><th>默认限值</th><th>方法/仪器</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in factorTemplates" :key="item.id">
              <td>{{ item.factorName }}</td>
              <td>{{ item.metricType }}</td>
              <td>{{ item.defaultLimitValue }} {{ item.defaultLimitUnit }}</td>
              <td>{{ item.detectionMethod || '/' }}<br />{{ item.detectionInstruments || '/' }}</td>
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
          <label>专业<input v-model="staffProfileForm.profession" /></label>
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
              <td>{{ codeLabel(item.templateType) }}</td>
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
        <!-- 用户管理 -->
        <section class="card" v-if="systemChildMenu === 'user'">
          <h2>用户管理 <HelpMark anchor-id="system.user-role" /></h2>
          <div class="grid2 compact">
            <input v-model="userQuery.username" placeholder="用户名搜索" />
            <div>
              <button @click="fetchUsers">查询</button>
              <button class="ghost" style="margin-left:8px" @click="clearUserForm">清空表单</button>
            </div>
          </div>

          <div class="grid2" style="margin-top:12px">
            <label>用户名 <span style="color:red">*</span><input v-model="userForm.username" placeholder="登录账号" /></label>
            <label>密码 {{ userForm.id ? '（留空不修改）' : '（必填）' }}<input v-model="userForm.password" type="password" placeholder="密码" /></label>
            <label>员工ID（可选）<input v-model.number="userForm.employeeId" type="number" placeholder="关联员工ID" /></label>
            <label>用户类型
              <select v-model="userForm.role">
                <option value="USER">普通用户</option>
                <option value="ADMIN">管理员</option>
              </select>
            </label>
            <label>状态
              <select v-model="userForm.status">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">禁用</option>
              </select>
            </label>
          </div>
          <div class="actions">
            <button @click="saveUser">{{ userForm.id ? '更新用户' : '新建用户' }}</button>
            <button class="ghost" @click="clearUserForm">清空</button>
          </div>

          <table>
            <thead>
              <tr><th>ID</th><th>用户名</th><th>类型</th><th>状态</th><th>需改密</th><th>创建时间</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.id">
                <td>{{ u.id }}</td>
                <td>{{ u.username }}</td>
                <td><el-tag :type="u.role === 'ADMIN' ? 'danger' : 'primary'" size="small">{{ u.role === 'ADMIN' ? '管理员' : '普通用户' }}</el-tag></td>
                <td><el-tag :type="u.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ u.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></td>
                <td>{{ u.mustChangePassword ? '是' : '否' }}</td>
                <td>{{ u.createdAt ? u.createdAt.substring(0, 10) : '-' }}</td>
                <td>
                  <button class="ghost tiny" @click="editUser(u)">编辑</button>
                  <button class="ghost tiny" @click="openResetDialog(u)" style="margin-left:4px">重置密码</button>
                  <button class="ghost tiny" @click="openRoleAssignDialog(u)" style="margin-left:4px">分配角色</button>
                  <button class="danger tiny" @click="deleteUser(u.id)" style="margin-left:4px">删除</button>
                </td>
              </tr>
              <tr v-if="users.length === 0"><td colspan="7" style="text-align:center;color:#999">暂无数据，点击查询加载</td></tr>
            </tbody>
          </table>
          <p class="small">共 {{ totalUsers }} 条</p>

          <!-- 重置密码弹窗 -->
          <div v-if="showResetDialog" class="modal-mask" @click.self="showResetDialog = false">
            <div class="modal-box">
              <h3>重置密码：{{ resetPasswordForm.username }}</h3>
              <label>新密码<input v-model="resetPasswordForm.newPassword" type="password" placeholder="请输入新密码" /></label>
              <div class="actions" style="margin-top:12px">
                <button @click="doResetPassword">确认重置</button>
                <button class="ghost" @click="showResetDialog = false">取消</button>
              </div>
            </div>
          </div>

          <!-- 分配角色弹窗 -->
          <div v-if="showRoleAssignDialog" class="modal-mask" @click.self="showRoleAssignDialog = false">
            <div class="modal-box" style="min-width:360px;max-width:480px">
              <h3>分配角色：{{ assigningUsername }}</h3>
              <div v-if="roles.length === 0" class="small">暂无可用角色</div>
              <div v-else style="max-height:260px;overflow:auto;border:1px solid #d6deea;border-radius:8px;padding:8px">
                <label v-for="r in roles" :key="r.id" style="flex-direction:row;align-items:center;gap:8px;padding:4px 0;font-size:14px;color:#223047;cursor:pointer">
                  <input type="checkbox" :value="r.id" v-model="selectedRoleIds" style="width:auto;border:none;padding:0" />
                  <span>{{ r.roleName }}</span>
                  <span class="small" style="margin:0">({{ r.roleCode }})</span>
                </label>
              </div>
              <div class="actions" style="margin-top:12px">
                <button @click="saveUserRoles">保存</button>
                <button class="ghost" @click="showRoleAssignDialog = false">取消</button>
              </div>
            </div>
          </div>
        </section>

        <!-- 角色管理 -->
        <section class="card" v-else-if="systemChildMenu === 'role'">
          <h2>角色管理 <HelpMark anchor-id="system.user-role" /></h2>
          <div class="grid2 compact">
            <input v-model="roleQuery.roleCode" placeholder="角色编码" />
            <input v-model="roleQuery.roleName" placeholder="角色名称" />
            <div>
              <button @click="fetchRoles">查询</button>
              <button class="ghost" style="margin-left:8px" @click="clearRoleForm">清空表单</button>
            </div>
          </div>

          <div class="grid2" style="margin-top:12px">
            <label>角色编码 <span style="color:red">*</span><input v-model="roleForm.roleCode" placeholder="如 INSPECTOR" :disabled="!!roleForm.id" /></label>
            <label>角色名称 <span style="color:red">*</span><input v-model="roleForm.roleName" placeholder="如 检测员" /></label>
            <label class="full">备注<input v-model="roleForm.remark" placeholder="可选说明" /></label>
            <label>状态
              <select v-model="roleForm.status">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">禁用</option>
              </select>
            </label>
          </div>
          <div class="actions">
            <button @click="saveRole">{{ roleForm.id ? '更新角色' : '新建角色' }}</button>
            <button class="ghost" @click="clearRoleForm">清空</button>
          </div>

          <table>
            <thead>
              <tr><th>ID</th><th>角色编码</th><th>角色名称</th><th>状态</th><th>备注</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in roles" :key="r.id">
                <td>{{ r.id }}</td>
                <td><code>{{ r.roleCode }}</code></td>
                <td>{{ r.roleName }}</td>
                <td><el-tag :type="r.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ r.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></td>
                <td>{{ r.remark || '-' }}</td>
                <td>
                  <button class="ghost tiny" @click="editRole(r)">编辑</button>
                  <button class="ghost tiny" @click="openRolePermDialog(r)" style="margin-left:4px">分配权限</button>
                  <button class="danger tiny" @click="deleteRole(r.id)" style="margin-left:4px">删除</button>
                </td>
              </tr>
              <tr v-if="roles.length === 0"><td colspan="6" style="text-align:center;color:#999">暂无数据，点击查询加载</td></tr>
            </tbody>
          </table>
          <p class="small">共 {{ totalRoles }} 条</p>

          <!-- 分配权限弹窗 -->
          <div v-if="showRolePermDialog" class="modal-mask" @click.self="showRolePermDialog = false">
            <div class="modal-box" style="min-width:400px;max-width:560px">
              <h3>分配权限：{{ assigningRoleName }}</h3>
              <div v-if="permissions.length === 0" class="small">暂无可用权限</div>
              <div v-else style="max-height:320px;overflow:auto;border:1px solid #d6deea;border-radius:8px;padding:8px">
                <label v-for="p in permissions" :key="p.id" style="flex-direction:row;align-items:center;gap:8px;padding:4px 0;font-size:13px;color:#223047;cursor:pointer">
                  <input type="checkbox" :value="p.id" v-model="selectedPermIds" style="width:auto;border:none;padding:0" />
                  <el-tag size="small" :type="p.permType === 'MENU' ? 'primary' : p.permType === 'BUTTON' ? 'warning' : 'info'" style="margin-right:4px">{{ codeLabel(p.permType) }}</el-tag>
                  <span>{{ p.permName }}</span>
                  <span style="color:#6a7c98;font-size:12px">({{ p.permCode }})</span>
                </label>
              </div>
              <div class="actions" style="margin-top:12px">
                <button @click="saveRolePerms">保存</button>
                <button class="ghost" @click="showRolePermDialog = false">取消</button>
              </div>
            </div>
          </div>
        </section>

        <!-- 菜单管理 -->
        <section class="card" v-else-if="systemChildMenu === 'menu'">
          <h2>菜单管理</h2>
          <div class="actions">
            <button @click="fetchMenuTree">刷新菜单树</button>
            <button class="ghost" @click="clearMenuForm">清空表单</button>
          </div>

          <div class="grid2" style="margin-top:12px">
            <label>权限码 <span style="color:red">*</span><input v-model="menuForm.permCode" placeholder="如 system:user:list" :disabled="!!menuForm.id" /></label>
            <label>菜单名称 <span style="color:red">*</span><input v-model="menuForm.permName" placeholder="如 用户管理" /></label>
            <label>父菜单
              <select v-model="menuForm.parentId">
                <option :value="null">无（顶级菜单）</option>
                <option v-for="m in flattenMenuTree(menuTree)" :key="m.id" :value="m.id">{{ m.permName }}（{{ m.permCode }}）</option>
              </select>
            </label>
            <label>排序号<input v-model.number="menuForm.sortNo" type="number" placeholder="数字越小越靠前" /></label>
            <label>状态
              <select v-model="menuForm.status">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">禁用</option>
              </select>
            </label>
          </div>
          <div class="actions">
            <button @click="saveMenu">{{ menuForm.id ? '更新菜单' : '新建菜单' }}</button>
            <button v-if="menuForm.id" class="ghost" @click="clearMenuForm">取消编辑</button>
          </div>

          <div v-if="menuTree.length === 0" class="empty-state">
            <p class="small">暂无菜单，点击"刷新菜单树"加载或直接新建。</p>
          </div>
          <div v-else>
            <table>
              <thead>
                <tr><th>ID</th><th>权限码</th><th>菜单名称</th><th>排序</th><th>状态</th><th>操作</th></tr>
              </thead>
              <tbody>
                <template v-for="m in menuTree" :key="m.id">
                  <tr>
                    <td>{{ m.id }}</td>
                    <td><code>{{ m.permCode }}</code></td>
                    <td><strong>{{ m.permName }}</strong></td>
                    <td>{{ m.sortNo ?? '-' }}</td>
                    <td><el-tag :type="m.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ m.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></td>
                    <td>
                      <button class="ghost tiny" @click="editMenu(m)">编辑</button>
                      <button class="danger tiny" @click="deleteMenu(m.id)" style="margin-left:4px">删除</button>
                    </td>
                  </tr>
                  <tr v-for="child in m.children" :key="child.id">
                    <td>{{ child.id }}</td>
                    <td style="padding-left:24px"><code>{{ child.permCode }}</code></td>
                    <td style="padding-left:24px">↳ {{ child.permName }}</td>
                    <td>{{ child.sortNo ?? '-' }}</td>
                    <td><el-tag :type="child.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ child.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></td>
                    <td>
                      <button class="ghost tiny" @click="editMenu(child)">编辑</button>
                      <button class="danger tiny" @click="deleteMenu(child.id)" style="margin-left:4px">删除</button>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </section>

        <!-- 权限管理 -->
        <section class="card" v-else-if="systemChildMenu === 'permission'">
          <h2>权限管理 <HelpMark anchor-id="system.permission" /></h2>
          <div class="grid2 compact">
            <input v-model="permQuery.permCode" placeholder="权限码" />
            <input v-model="permQuery.permName" placeholder="权限名称" />
            <div>
              <button @click="fetchPermissions">查询</button>
              <button class="ghost" style="margin-left:8px" @click="clearPermForm">清空表单</button>
            </div>
          </div>

          <div class="grid2" style="margin-top:12px">
            <label>权限码 <span style="color:red">*</span><input v-model="permForm.permCode" placeholder="如 ohdp:project:edit" :disabled="!!permForm.id" /></label>
            <label>权限名称 <span style="color:red">*</span><input v-model="permForm.permName" placeholder="如 编辑项目" /></label>
            <label>权限类型
              <select v-model="permForm.permType">
                <option value="MENU">MENU（菜单）</option>
                <option value="BUTTON">BUTTON（按钮）</option>
                <option value="API">API（接口）</option>
              </select>
            </label>
            <label>HTTP 方法<input v-model="permForm.apiMethod" placeholder="GET / POST / PUT / DELETE" /></label>
            <label class="full">API 路径<input v-model="permForm.apiPath" placeholder="如 /api/ohdp/projects/**" /></label>
            <label>排序号<input v-model.number="permForm.sortNo" type="number" /></label>
            <label>状态
              <select v-model="permForm.status">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">禁用</option>
              </select>
            </label>
          </div>
          <div class="actions">
            <button @click="savePermission">{{ permForm.id ? '更新权限' : '新建权限' }}</button>
            <button v-if="permForm.id" class="ghost" @click="clearPermForm">取消编辑</button>
          </div>

          <table>
            <thead>
              <tr><th>ID</th><th>权限码</th><th>名称</th><th>类型</th><th>HTTP方法</th><th>路径</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in permissions" :key="p.id">
                <td>{{ p.id }}</td>
                <td><code>{{ p.permCode }}</code></td>
                <td>{{ p.permName }}</td>
                <td><el-tag size="small" :type="p.permType === 'MENU' ? 'primary' : p.permType === 'BUTTON' ? 'warning' : 'info'">{{ codeLabel(p.permType) }}</el-tag></td>
                <td>{{ p.apiMethod || '-' }}</td>
                <td style="max-width:200px;word-break:break-all">{{ p.apiPath || '-' }}</td>
                <td><el-tag :type="p.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ p.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></td>
                <td>
                  <button class="ghost tiny" @click="editPermission(p)">编辑</button>
                  <button class="danger tiny" @click="deletePermission(p.id)" style="margin-left:4px">删除</button>
                </td>
              </tr>
              <tr v-if="permissions.length === 0"><td colspan="8" style="text-align:center;color:#999">暂无数据，点击查询加载</td></tr>
            </tbody>
          </table>
          <p class="small">共 {{ totalPermissions }} 条</p>
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
            <tbody><tr v-for="item in sysNotices" :key="item.id"><td>{{ item.title }}</td><td>{{ codeLabel(item.noticeType) }}</td><td>{{ item.publisher }}</td><td><button class="danger tiny" @click="deleteNotice(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="systemChildMenu === 'syslog'">
          <h2>操作日志 <HelpMark anchor-id="system.audit-log" /></h2>
          <div class="actions"><button class="ghost" @click="fetchSysLogs">刷新</button></div>
          <table><thead><tr><th>用户</th><th>操作</th><th>模块</th><th>结果</th><th>时间</th></tr></thead>
            <tbody><tr v-for="item in sysLogs" :key="item.id"><td>{{ item.userName }}</td><td>{{ codeLabel(item.operation) }}</td><td>{{ codeLabel(item.module) }}</td><td>{{ codeLabel(item.resultType) }}</td><td>{{ formatDateTime(item.createdAt) }}</td></tr></tbody>
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
          <div class="section-title-row"><div><h2>法律法规 <HelpMark anchor-id="basic.reference" /></h2><p>登记法规元数据并保留原文版本，历史项目引用不随新版本改变。</p></div></div>
          <div class="grid2">
            <label>标题<input v-model="lawForm.title" /></label>
            <label>文号<input v-model="lawForm.docNo" /></label>
            <label>级别
              <select v-model="lawForm.level"><option value="NATIONAL">国家法律法规</option><option value="DEPARTMENT">部门规章</option><option value="PROVINCIAL">省级法规</option><option value="LOCAL">地方性法规</option></select>
            </label>
            <label>发布日期<input v-model="lawForm.publishDate" type="date" /></label>
            <label>生效日期<input v-model="lawForm.effectiveDate" type="date" /></label>
            <label>发布机构<input v-model="lawForm.publisher" /></label>
            <label>有效状态<select v-model="lawForm.isValid"><option :value="true">现行有效</option><option :value="false">失效或废止</option></select></label>
            <label>公开状态<select v-model="lawForm.isOpen"><option :value="true">可公开使用</option><option :value="false">内部资料</option></select></label>
            <label class="full">备注<input v-model="lawForm.notes" /></label>
          </div>
          <section class="reference-upload-panel">
            <label>原文文件（PDF、DOC、DOCX） <HelpMark anchor-id="basic.document-upload" /><input :key="referenceDocumentInputKey" type="file" accept=".pdf,.doc,.docx" @change="onReferenceDocumentSelected" /></label>
            <label>版本名称 <HelpMark anchor-id="basic.document-version" /><input v-model="referenceVersionLabel" placeholder="如：2020 年发布版" /></label>
            <label>官方来源网址<input v-model="referenceSourceUrl" placeholder="可选，填写官方发布页面" /></label>
          </section>
          <div class="actions"><button :disabled="actionBusy.uploadReferenceDocument" @click="saveLaw">{{ actionBusy.uploadReferenceDocument ? '上传中...' : (lawForm.id ? '更新' : '新增') }}</button><button class="ghost" @click="clearLawForm">清空</button><button class="ghost" @click="fetchLaws">刷新</button></div>
          <table><thead><tr><th>标题</th><th>文号</th><th>级别</th><th>有效状态</th><th>当前原文</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in laws" :key="item.id"><td>{{ item.title }}</td><td>{{ item.docNo }}</td><td>{{ item.levelLabel || codeLabel(item.level) }}</td><td>{{ item.isValid === false ? '失效或废止' : '现行有效' }}</td><td><button v-if="item.currentDocument" class="link-button" @click="openReferenceDocument(item.currentDocument)">{{ item.currentDocument.versionLabel || item.currentDocument.originalName }}</button><span v-else>未上传</span></td><td><button class="ghost tiny" @click="editLaw(item)">编辑/版本</button><button class="danger tiny" @click="deleteLaw(item.id)">删除</button></td></tr></tbody>
          </table>
          <section v-if="lawForm.id" class="reference-history">
            <h3>原文版本记录</h3>
            <p v-if="!referenceDocuments.length" class="small">尚未上传原文。</p>
            <ol v-else><li v-for="item in referenceDocuments" :key="item.id"><div><strong>{{ item.versionLabel || `第 ${item.versionNo} 版` }}</strong><span>{{ item.originalName }} · {{ formatFileSize(item.fileSize) }} · {{ formatDateTime(item.uploadedAt) }}</span></div><el-tag v-if="item.current" type="success" size="small">当前版本</el-tag><button class="ghost tiny" @click="openReferenceDocument(item)">预览</button><button class="ghost tiny" @click="openReferenceDocument(item, true)">下载</button></li></ol>
          </section>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'standard'">
          <div class="section-title-row"><div><h2>标准规范 <HelpMark anchor-id="basic.reference" /></h2><p>维护检测、计算和判定使用的标准版本及可核验原文。</p></div></div>
          <div class="grid2">
            <label>标准名称<input v-model="standardForm.standardName" /></label>
            <label>标准编号<input v-model="standardForm.standardCode" /></label>
            <label>级别<select v-model="standardForm.level"><option value="NATIONAL">国标</option><option value="INDUSTRY">行标</option></select></label>
            <label>发布日期<input v-model="standardForm.publishDate" type="date" /></label>
            <label class="full">适用范围<input v-model="standardForm.useScope" placeholder="如：职业危害检测、DR 质量控制" /></label>
            <label>有效状态<select v-model="standardForm.isValid"><option :value="true">现行有效</option><option :value="false">失效或废止</option></select></label>
            <label>公开状态<select v-model="standardForm.isOpen"><option :value="true">可公开使用</option><option :value="false">内部资料</option></select></label>
            <label class="full">备注<input v-model="standardForm.notes" /></label>
          </div>
          <section class="reference-upload-panel">
            <label>标准原文（PDF、DOC、DOCX） <HelpMark anchor-id="basic.document-upload" /><input :key="referenceDocumentInputKey" type="file" accept=".pdf,.doc,.docx" @change="onReferenceDocumentSelected" /></label>
            <label>版本名称 <HelpMark anchor-id="basic.document-version" /><input v-model="referenceVersionLabel" placeholder="如：2020 年发布版" /></label>
            <label>官方来源网址<input v-model="referenceSourceUrl" placeholder="可选，填写官方发布页面" /></label>
          </section>
          <div class="actions"><button :disabled="actionBusy.uploadReferenceDocument" @click="saveStandard">{{ actionBusy.uploadReferenceDocument ? '上传中...' : (standardForm.id ? '更新' : '新增') }}</button><button class="ghost" @click="clearStandardForm">清空</button><button class="ghost" @click="fetchStandards">刷新</button></div>
          <table><thead><tr><th>标准名称</th><th>编号</th><th>级别</th><th>适用范围</th><th>当前原文</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in standards" :key="item.id"><td>{{ item.standardName }}</td><td>{{ item.standardCode }}</td><td>{{ item.levelLabel || codeLabel(item.level) }}</td><td>{{ item.useScope || '-' }}</td><td><button v-if="item.currentDocument" class="link-button" @click="openReferenceDocument(item.currentDocument)">{{ item.currentDocument.versionLabel || item.currentDocument.originalName }}</button><span v-else>未上传</span></td><td><button class="ghost tiny" @click="editStandard(item)">编辑/版本</button><button class="danger tiny" @click="deleteStandard(item.id)">删除</button></td></tr></tbody>
          </table>
          <section v-if="standardForm.id" class="reference-history">
            <h3>原文版本记录</h3>
            <p v-if="!referenceDocuments.length" class="small">尚未上传原文。</p>
            <ol v-else><li v-for="item in referenceDocuments" :key="item.id"><div><strong>{{ item.versionLabel || `第 ${item.versionNo} 版` }}</strong><span>{{ item.originalName }} · {{ formatFileSize(item.fileSize) }} · {{ formatDateTime(item.uploadedAt) }}</span></div><el-tag v-if="item.current" type="success" size="small">当前版本</el-tag><button class="ghost tiny" @click="openReferenceDocument(item)">预览</button><button class="ghost tiny" @click="openReferenceDocument(item, true)">下载</button></li></ol>
          </section>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'region'">
          <h2>行政区划 <HelpMark anchor-id="basic.region" /></h2>
          <div class="grid2">
            <label>区域名称<input v-model="regionForm.regionName" /></label>
            <label>区域代码<input v-model="regionForm.regionCode" /></label>
            <label>级别<select v-model="regionForm.level"><option :value="1">省级</option><option :value="2">市级</option><option :value="3">区县</option></select></label>
            <label>排序<input v-model.number="regionForm.sortNo" type="number" /></label>
          </div>
          <div class="actions"><button @click="saveRegion">{{ regionForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchRegions">刷新</button></div>
          <table><thead><tr><th>名称</th><th>代码</th><th>级别</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in regions" :key="item.id"><td>{{ item.regionName }}</td><td>{{ item.regionCode }}</td><td>{{ item.levelLabel || regionLevelLabel(item.level) }}</td><td><button class="ghost tiny" @click="editRegion(item)">编辑</button><button class="danger tiny" @click="deleteRegion(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="basicDataChildMenu === 'device'">
          <h2>检测仪器设备 <HelpMark anchor-id="basic.device" /></h2>
          <div class="grid2">
            <label>设备名称<input v-model="deviceForm.deviceName" /></label>
            <label>类型<select v-model="deviceForm.deviceType"><option value="LAB_INSTRUMENT">实验仪器</option><option value="SAMPLER">采样仪器</option></select></label>
            <label>型号<input v-model="deviceForm.deviceModel" /></label>
            <label>设备编号<input v-model="deviceForm.deviceNo" /></label>
            <label>状态<select v-model="deviceForm.status"><option value="NORMAL">正常</option><option value="MAINTENANCE">维修中</option><option value="SCRAPPED">报废</option></select></label>
            <label>证书编号<input v-model="deviceForm.certificateNo" /></label><label>有效期起<input v-model="deviceForm.calibrationDate" type="date" /></label><label>有效期止<input v-model="deviceForm.calibrationExpiry" type="date" /></label>
            <label class="full">备注<input v-model="deviceForm.notes" /></label>
          </div>
          <div class="actions"><button @click="saveDevice">{{ deviceForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchDevices">刷新</button></div>
          <table><thead><tr><th>名称</th><th>型号</th><th>类型</th><th>状态</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in devices" :key="item.id"><td>{{ item.deviceName }}</td><td>{{ item.deviceModel }}</td><td>{{ item.deviceTypeLabel || codeLabel(item.deviceType) }}</td><td>{{ item.statusLabel || codeLabel(item.status) }}</td><td><button class="ghost tiny" @click="editDevice(item)">编辑</button><button class="danger tiny" @click="deleteDevice(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
      </section>

      <!-- 危害因素管理 -->
      <section v-else-if="activeTab === 'hazardFactor'" class="maintenance-grid">
        <section class="card" v-if="hazardChildMenu === 'factor'">
          <h2>危害因素库 <HelpMark anchor-id="hazard.category" /></h2>
          <div class="grid2">
            <label>因素名称<input v-model="hazardFactorForm.factorName" /></label>
            <label>分类 <HelpMark anchor-id="hazard.category" /><select v-model="hazardFactorForm.factorCategory"><option value="PHYSICAL">物理因素</option><option value="CHEMICAL">化学因素</option><option value="DUST">粉尘</option><option value="BIOLOGICAL">生物因素</option><option value="RADIATION">放射因素</option><option value="OTHER">其他</option></select></label>
            <label>因素编码<input v-model="hazardFactorForm.factorCode" /></label>
            <label>CAS号 <HelpMark anchor-id="hazard.cas" /><input v-model="hazardFactorForm.casNo" /></label>
          </div>
          <div class="actions"><button @click="saveHazardFactor">{{ hazardFactorForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchHazardFactors">刷新</button></div>
          <table><thead><tr><th>因素名称</th><th>分类</th><th>CAS号</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in hazardFactors" :key="item.id" :class="{ 'row-selected': selectedHazardFactorId === item.id }">
              <td>{{ item.factorName }}</td><td>{{ item.factorCategoryLabel || codeLabel(item.factorCategory) }}</td><td>{{ item.casNo }}</td>
              <td>
                <button class="ghost tiny" @click="editHazardFactor(item)">编辑</button>
                <button class="ghost tiny" @click="viewFactorLimits(item)" style="margin-left:4px">限值管理</button>
                <button class="danger tiny" @click="deleteHazardFactor(item.id)" style="margin-left:4px">删除</button>
              </td>
            </tr></tbody>
          </table>

          <div v-if="selectedHazardFactorId" style="margin-top:16px;border-top:2px solid var(--primary);padding-top:12px">
            <h3 style="margin-bottom:8px">「{{ selectedHazardFactorName }}」职业接触限值 <HelpMark anchor-id="hazard.limit" /></h3>
            <div class="grid2">
              <label>限值类型
                <select v-model="limitForm.limitType">
                  <option value="PC_TWA">PC-TWA（时间加权平均）</option>
                  <option value="PC_STEL">PC-STEL（短时间接触）</option>
                  <option value="MAC">MAC（最高容许浓度）</option>
                  <option value="OEL">OEL</option>
                  <option value="OTHER">其他</option>
                </select>
              </label>
              <label>限值<input v-model="limitForm.limitValue" type="number" step="any" placeholder="如 0.15" /></label>
              <label>单位<input v-model="limitForm.limitUnit" placeholder="如 mg/m3、dB(A)" /></label>
              <label class="full">标准文号<input v-model="limitForm.standardCode" placeholder="如 GBZ 2.1-2019" /></label>
            </div>
            <div class="actions">
              <button @click="saveFactorLimit">{{ limitForm.id ? '更新限值' : '新增限值' }}</button>
              <button class="ghost" @click="resetLimitForm">清空</button>
            </div>
            <table>
              <thead><tr><th>限值类型</th><th>限值</th><th>单位</th><th>标准文号</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="lim in factorLimits" :key="lim.id">
                  <td>{{ lim.limitTypeLabel || codeLabel(lim.limitType) }}</td>
                  <td>{{ lim.limitValue }}</td>
                  <td>{{ lim.limitUnit }}</td>
                  <td>{{ lim.standardCode }}</td>
                  <td>
                    <button class="ghost tiny" @click="editFactorLimit(lim)">编辑</button>
                    <button class="danger tiny" @click="deleteFactorLimit(lim.id)" style="margin-left:4px">删除</button>
                  </td>
                </tr>
                <tr v-if="factorLimits.length === 0"><td colspan="5" style="text-align:center;color:#999">暂无限值，请新增</td></tr>
              </tbody>
            </table>
          </div>
        </section>
        <section class="card" v-else-if="hazardChildMenu === 'sds'">
          <h2>SDS安全技术说明书 <HelpMark anchor-id="hazard.sds" /></h2>
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
          <h2>检测方法配置 <HelpMark anchor-id="config.method" /></h2>
          <div class="grid2">
            <label>方法名称<input v-model="detectionMethodForm.methodName" /></label>
            <label>方法代码<input v-model="detectionMethodForm.methodCode" /></label>
            <label>简称<input v-model="detectionMethodForm.methodAbbr" /></label>
            <label>计算公式 <HelpMark anchor-id="config.formula" /><input v-model="detectionMethodForm.formula" /></label>
            <label class="full">检测依据（标准文号） <HelpMark anchor-id="config.standard-code" /><input v-model="detectionMethodForm.standardCode" placeholder="如 GBZ/T 300.17-2017；多个依据用分号分隔" /></label>
          </div>
          <div class="actions"><button @click="saveDetectionMethod">{{ detectionMethodForm.id ? '更新' : '新增' }}</button><button class="ghost" @click="fetchDetectionMethods">刷新</button></div>
          <table><thead><tr><th>名称</th><th>代码</th><th>简称</th><th>检测依据</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in detectionMethods" :key="item.id"><td>{{ item.methodName }}</td><td>{{ item.methodCode }}</td><td>{{ item.methodAbbr }}</td><td>{{ item.standardCode }}</td><td><button class="ghost tiny" @click="editDetectionMethod(item)">编辑</button><button class="danger tiny" @click="deleteDetectionMethod(item.id)">删除</button></td></tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="configChildMenu === 'detectType'">
          <h2>检测分类配置 <HelpMark anchor-id="config.detect-type" /></h2>
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
          <h2>机构基础配置 <HelpMark anchor-id="config.numbering" /></h2>
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

      <section v-else-if="activeTab === 'radiology'" class="radiology-module">
        <RadiologyWorkbench
          :token="token"
          :modality="radiologyChildMenu"
          @select-modality="(m) => { radiologyChildMenu=m;selectedMenuKey= m==='CT' ? 'radiology.ct':'radiology.dr' }"
          :can-review="canReviewRadiology"
          :can-approve="canApproveRadiology"
          :can-generate-report="canGenerateRadiologyReport"
          :can-manage-rules="canManageRadiologyRules"
        />
      </section>

      <section v-else-if="activeTab === 'feedback'" class="feedback-module">
        <FeedbackCenter
          :token="token"
          :can-read-all="canReadAllFeedback"
          :can-handle="canHandleFeedback"
          :can-close="canCloseFeedback"
          :current-username="currentUsername"
          :source-module="feedbackSourceModule"
        />
      </section>

      <!-- 实验管理 -->
      <section v-else-if="activeTab === 'labManage'" class="maintenance-grid">
        <section class="card" v-if="labChildMenu === 'task'">
          <h2>内部检测任务单 <HelpMark anchor-id="lab.task" /></h2>
          <div class="grid2">
            <label>关联项目ID<input v-model.number="labTaskForm.projectId" type="number" /></label>
            <label>签发人<input v-model="labTaskForm.issuerName" /></label>
            <label class="full">备注<input v-model="labTaskForm.notes" /></label>
          </div>
          <div class="actions"><button @click="createLabTask">创建任务单</button><button class="ghost" @click="fetchLabTasks">刷新</button></div>
          <table><thead><tr><th>任务单号</th><th>项目ID</th><th>状态</th><th>签发人</th><th>操作</th></tr></thead>
            <tbody><tr v-for="item in labTasks" :key="item.id">
              <td>{{ item.taskCode }}</td><td>{{ item.projectId }}</td><td>{{ item.taskStatusLabel || codeLabel(item.taskStatus) }}</td><td>{{ item.issuerName }}</td>
              <td>
                <button v-if="item.taskStatus === 'PENDING'" class="ghost tiny" @click="receiveTask(item.id, '当前用户')">领取</button>
                <button v-if="item.taskStatus === 'IN_PROGRESS'" class="ghost tiny" @click="submitTask(item.id)">提交审核</button>
              </td>
            </tr></tbody>
          </table>
        </section>
        <section class="card" v-else-if="labChildMenu === 'result'">
          <h2>结果计算与复核 <HelpMark anchor-id="lab.result" /></h2>
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
    <ContextHelpDrawer
      :anchor-id="contextHelpAnchorId"
      @close="contextHelpAnchorId = null"
      @open-topic="openDetailedHelp"
    />
  </div>
</template>
