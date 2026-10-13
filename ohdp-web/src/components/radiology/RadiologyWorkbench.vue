<script setup lang="ts">
import axios from 'axios'
import { renderAsync } from 'docx-preview'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import HelpMark from '../help/HelpMark.vue'
import RadiologyConditionEditor from './RadiologyConditionEditor.vue'

type Modality = 'DR' | 'CT'
type CaseStatus = 'DRAFT' | 'DATA_ENTERED' | 'CALCULATED' | 'PENDING_REVIEW' | 'REVIEWED' | 'APPROVED' | 'REPORTED'
type FieldRole = 'RAW' | 'INTERMEDIATE' | 'FINAL'

interface FieldDefinition {
  code: string
  label: string
  role: FieldRole
  unit: string
  required: boolean
  sortNo: number
}

interface ItemDefinition {
  modality: Modality
  code: string
  name: string
  standardCode: string
  sortNo: number
  fields: FieldDefinition[]
}

interface Measurement {
  id: number
  itemCode: string
  fieldCode: string
  fieldLabel: string
  fieldRole: FieldRole
  valueNum?: number
  valueText?: string
  unit?: string
  sortNo: number
  displayValue?: string
  valueSource: string
}

interface ProtectionPoint {
  id?: number
  pointCode: string
  pointName: string
  locationDescription?: string
  readings: number[]
  averageValue?: number
  resultValue?: number
  averageDisplay?: string
  resultDisplay?: string
  controlLimit?: number
  annualWorkloadMas?: number
  occupancyFactor?: number
  useFactor?: number
  annualEffectiveDose?: number
  annualDoseLimit?: number
  judgement?: string
  sortNo: number
}

interface Attachment {
  id: number
  category: string
  originalName: string
  mediaType: string
  fileSize: number
  sha256: string
  uploadedAt: string
}

interface ReportFile {
  id: number
  reportNo: string
  fileName: string
  sha256: string
  generatedBy?: string
  generatedAt: string
}

interface InspectionCase {
  city?: string
  county?: string
  profile?: ReportProfile
  id: number
  caseCode: string
  modality: Modality
  revisionNo: number
  sourceCaseId?: number
  sampleAcceptanceNo?: string
  inspectedUnitName: string
  inspectedUnitAddress?: string
  contactName?: string
  contactPhone?: string
  equipmentName: string
  equipmentModel?: string
  equipmentNo?: string
  manufacturer?: string
  phantomName?: string
  phantomManufacturer?: string
  phantomModel?: string
  phantomSerialNo?: string
  phantomManualReference?: string
  detectionCategory?: string
  detectionLocation?: string
  inspectionDate?: string
  reportDate?: string
  standardBasis?: string
  instrumentDescription?: string
  writerName?: string
  reviewerName?: string
  approverName?: string
  reviewComment?: string
  approvalComment?: string
  notes?: string
  calculationVersion: string
  status: CaseStatus
  itemConditions: Record<string,string>
  dataVersion: number
  calculatedDataVersion?: number
  calculationFresh: boolean
  hasAec?: boolean
  hasRawImages?: boolean
  featureReason?: string
  agencyName?: string
  region?: string
  inspectionEndDate?: string
  protectionMode?: string
  rpdUnit?: string
  itemResults: CalculationItem[]
  submissionIssues: string[]
  workflowHistory: Array<{action:string;operator:string;comment:string;dataVersion:number;at:string}>
  measurements: Measurement[]
  protectionPoints: ProtectionPoint[]
  attachments: Attachment[]
  reports: ReportFile[]
  updatedAt?: string
}

interface CaseSummary {
  generatedAt?: string
  sampleAcceptanceNo?: string
  equipmentNo?: string
  reportDate?: string
  city?: string
  county?: string
  id: number
  caseCode: string
  modality: Modality
  revisionNo: number
  inspectedUnitName: string
  equipmentName: string
  equipmentModel?: string
  inspectionDate?: string
  status: CaseStatus
  updatedAt?: string
}

interface Rule {
  id: number
  modality: Modality
  itemCode: string
  itemName: string
  ruleVersion: string
  lowerLimit?: number
  upperLimit?: number
  expectedText?: string
  unit?: string
  standardCode: string
  standardName: string
  detectionCategory?: string
  lowerInclusive?: boolean
  upperInclusive?: boolean
  enabled: boolean
}

interface CalculationItem {
  itemCode: string
  itemName: string
  complete: boolean
  message: string
  resultValue?: number
  resultText?: string
  resultUnit?: string
  judgement: string
  ruleDescription: string
}

interface CalculationResponse {
  calculationVersion: string
  completedItems: number
  incompleteItems: number
  items: CalculationItem[]
}

interface ImportPreview {
  fileName: string
  sheetName: string
  dryRun: boolean
  recognizedFields: number
  appliedValues: number
  ignoredHeaders: string[]
  errors: Array<{ column: number; header: string; value: string; message: string }>
}

interface InstrumentRecord { deviceId?: number; name: string; model: string; deviceNo: string; certificateNo: string; validFrom?: string; validTo?: string }
interface ReportProfile {
  sampleName?: string; submittingUnit?: string; commissionedUnit?: string; equipmentParameters?: string; manufacturedDate?: string;
  detectorName?: string; backgroundLocation?: string; backgroundOperation?: string; calibrationMode?: string; instrumentResponseTime?: string;
  protectionConditions?: string; overallConclusion?: string; selectedGroups?: string[]; instruments?: InstrumentRecord[];
}
const blankProfile = (modality: Modality): ReportProfile => ({ sampleName: '', submittingUnit: '', commissionedUnit: '', equipmentParameters: '',
  detectorName: '', backgroundLocation: '', backgroundOperation: '未曝光', calibrationMode: 'INSTRUMENT', protectionConditions: '', overallConclusion: '',
  selectedGroups: modality === 'DR' ? ['GENERAL','SPECIAL','PROTECTION'] : ['QC','PROTECTION'], instruments: [] })
function detectionGroupLabel(group: string) {
  const labels: Record<string,string> = {GENERAL:'通用质控',SPECIAL:'DR专用质控',QC:'CT质量控制',PROTECTION:'场所防护'}
  return labels[group] || group
}

interface CaseForm {
  caseCode: string
  modality: Modality
  sampleAcceptanceNo: string
  inspectedUnitName: string
  inspectedUnitAddress: string
  contactName: string
  contactPhone: string
  equipmentName: string
  equipmentModel: string
  equipmentNo: string
  manufacturer: string
  phantomName: string
  phantomManufacturer: string
  phantomModel: string
  phantomSerialNo: string
  phantomManualReference: string
  detectionCategory: string
  detectionLocation: string
  inspectionDate: string
  reportDate: string
  standardBasis: string
  instrumentDescription: string
  writerName: string
  reviewerName: string
  approverName: string
  hasAec: boolean
  hasRawImages: boolean
  featureReason: string
  agencyName: string
  region: string
  inspectionEndDate: string
  protectionMode: string
  rpdUnit: string
  notes: string
  city: string
  county: string
  profile: ReportProfile
}

const emit=defineEmits<{ 'select-modality':[Modality] }>()
const pendingQueryId=ref<number>()
const pendingQueryEdit=ref(false)
const props = withDefaults(defineProps<{
  token: string
  modality: Modality
  canReview?: boolean
  canApprove?: boolean
  canGenerateReport?: boolean
  canManageRules?: boolean
}>(), {
  canReview: false,
  canApprove: false,
  canGenerateReport: false,
  canManageRules: false,
})

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/' })
api.interceptors.request.use((config) => {
  if (props.token) config.headers.Authorization = `Bearer ${props.token}`
  return config
})

const busy = ref(false)
const cases = ref<CaseSummary[]>([])
const totalCases = ref(0)
const selectedCase = ref<InspectionCase | null>(null)
const catalog = ref<ItemDefinition[]>([])
const rules = ref<Rule[]>([])
const calculation = ref<CalculationResponse | null>(null)
const workTab = ref('base')
const query = reactive({ keyword: '', status: '' as '' | CaseStatus, page: 0, size: 20, year: undefined as number | undefined,
  from: '', to: '', city: '', county: '', receipt: '', company: '', modalities: [props.modality] as Modality[] })
const queryVisible = ref(false)
const previewVisible = ref(false)
const previewPdfUrl=ref('')
watch(previewVisible,value=>{if(!value && previewPdfUrl.value){URL.revokeObjectURL(previewPdfUrl.value);previewPdfUrl.value=''}})
const previewContainer = ref<HTMLElement>()
const profileDraft = reactive<ReportProfile>(blankProfile(props.modality))
const regionsLibrary=ref<Array<{id:number;parentId?:number;regionName:string;level:number}>>([])
const cityOptions=computed(()=>regionsLibrary.value.filter(r=>r.level===2))
const standardsLibrary = ref<Array<{id: number; standardCode: string; standardName: string}>>([])
const instrumentsLibrary = ref<Array<{id: number; deviceName: string; deviceModel?: string; deviceNo?: string; certificateNo?: string; calibrationDate?: string; calibrationExpiry?: string}>>([])
const staffLibrary = ref<Array<{id: number; staffName?: string; name?: string}>>([])
const selectedStandardIds = ref<number[]>([])
const selectedInstrumentId = ref<number>()
async function loadLibraries() {
  const results = await Promise.allSettled([api.get('/api/ohdp/basic/standards',{params:{size:100}}),api.get('/api/ohdp/devices',{params:{size:100,status:'NORMAL'}}),api.get('/api/ohdp/maintenance/staff-profiles',{params:{size:100}}),api.get('/api/ohdp/basic/regions')])
  if(results[0].status === 'fulfilled') standardsLibrary.value = results[0].value.data.content || []
  if(results[1].status === 'fulfilled') instrumentsLibrary.value = results[1].value.data.content || []
  if(results[2].status === 'fulfilled') staffLibrary.value = results[2].value.data.content || []
  if(results[3].status === 'fulfilled') regionsLibrary.value=results[3].value.data || []
}
function importStandards() { caseForm.standardBasis = standardsLibrary.value.filter(s=>selectedStandardIds.value.includes(s.id)).map((s,i)=>`${i+1}.${s.standardCode}《${s.standardName}》`).join('；') }
function addInstrument() {
  const d=instrumentsLibrary.value.find(d=>d.id===selectedInstrumentId.value);if(!d)return
  if(!caseForm.profile.instruments) caseForm.profile.instruments=[]
  if(caseForm.profile.instruments.some(i=>i.deviceId===d.id)) return
  caseForm.profile.instruments.push({deviceId:d.id,name:d.deviceName,model:d.deviceModel || '',deviceNo:d.deviceNo || '',certificateNo:d.certificateNo || '',validFrom:d.calibrationDate,validTo:d.calibrationExpiry})
}
function resetQuery() { Object.assign(query,{keyword:'',status:'',year:undefined,from:'',to:'',city:'',county:'',receipt:'',company:'',modalities:[props.modality],page:0});void loadCases() }
async function previewReport() {
  if(!selectedCase.value)return
  await run(async()=>{
    const caps=await api.get('/api/radiology/preview-capabilities')
    const pdf=!!caps.data.pdf
    const {data}=await api.get(`/api/radiology/cases/${selectedCase.value!.id}/report-preview`,{responseType:'blob',params:{format:pdf?'pdf':'docx'}})
    if(previewPdfUrl.value)URL.revokeObjectURL(previewPdfUrl.value)
    previewPdfUrl.value=pdf?URL.createObjectURL(new Blob([data],{type:'application/pdf'})):''
    previewVisible.value=true;await nextTick()
    if(!pdf && previewContainer.value) { previewContainer.value.innerHTML='';await renderAsync(data,previewContainer.value,undefined,{breakPages:true,renderHeaders:true,renderFooters:true,inWrapper:true,ignoreLastRenderedPageBreak:false}) }
  })
}
async function deleteReport(id: number) {
  const {value}=await ElMessageBox.prompt('填写删除原因。系统保留原文件及删除记录。','删除报告',{inputValidator:v=>!!v.trim() || '请填写原因'})
  await run(async()=>{await api.delete(`/api/radiology/reports/${id}`,{params:{reason:value}});await selectCase(selectedCase.value!.id)},'报告已从列表移除')
}
async function replaceAttachment(id: number,event: Event) {
  const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return
  await run(async()=>{const form=new FormData();form.append('file',file);await api.put(`/api/radiology/attachments/${id}`,form);await selectCase(selectedCase.value!.id)},'附件已更换');input.value=''
}
async function saveReportProfile() {
  if(!selectedCase.value)return
  await run(async()=>{
    const c=selectedCase.value!;const payload={...blankCaseForm(c.modality),...c,profile:JSON.parse(JSON.stringify(profileDraft)),expectedDataVersion:c.dataVersion}
    const {data}=await api.put<InspectionCase>(`/api/radiology/cases/${c.id}`,payload);selectedCase.value=data;syncCaseState()
  },'总体评价和防护条件已保存，请重新计算后提交')
}
async function generateNonconformities() {
  if(!selectedCase.value)return
  const {data}=await api.get(`/api/radiology/cases/${selectedCase.value.id}/suggested-conclusion`);profileDraft.overallConclusion=data.text
}

const conditionValues = reactive<Record<string,string>>({})
const measurementValues = reactive<Record<string, string | number>>({})
const pointDrafts = ref<Array<ProtectionPoint & { readingsText: string }>>([])
const caseDialogVisible = ref(false)
const editingCaseId = ref<number | null>(null)
const caseDialogStep = ref(0)
const caseFormDirty = ref(false)
const caseFormHydrating = ref(false)
const caseDialogCommitted = ref(false)
const caseDraftSavedAt = ref('')
const caseValidationMessage = ref('')
let caseDraftTimer: ReturnType<typeof setTimeout> | undefined
const importFile = ref<File | null>(null)
const importType = ref<'PERFORMANCE' | 'PROTECTION'>('PERFORMANCE')
const importPreview = ref<ImportPreview | null>(null)
const attachmentCategory = ref<'POINT_MAP' | 'CERTIFICATE' | 'PHANTOM_MANUAL' | 'OTHER'>('POINT_MAP')
const queryPage=computed({get:()=>query.page+1,set:(v:number)=>{query.page=v-1}})
async function selectQueryCase(item: CaseSummary, edit=false) {
  if(item.modality!==props.modality){queryVisible.value=false;pendingQueryId.value=item.id;pendingQueryEdit.value=edit;emit('select-modality',item.modality);return}
  queryVisible.value=false;await selectCase(item.id)
  if(edit && editable.value) openEditCase()
}
const ruleDialogVisible = ref(false)
const editingRuleId = ref<number | null>(null)
const calculationItems = computed(() => calculation.value?.items || selectedCase.value?.itemResults || [])

const blankCaseForm = (modality: Modality): CaseForm => ({
  caseCode: '', modality, sampleAcceptanceNo: '', inspectedUnitName: '', inspectedUnitAddress: '',
  contactName: '', contactPhone: '', equipmentName: '', equipmentModel: '', equipmentNo: '', manufacturer: '',
  phantomName: '', phantomManufacturer: '', phantomModel: '', phantomSerialNo: '', phantomManualReference: '',
  detectionCategory: '验收检测', detectionLocation: '',
  inspectionDate: '', reportDate: '',
  standardBasis: modality === 'DR'
    ? 'WS 76-2020《医用常规X射线诊断设备质量控制检测规范》；GBZ 130-2020《放射诊断放射防护要求》'
    : 'WS 519-2019《X射线计算机体层摄影装置质量控制检测规范》；GBZ 130-2020《放射诊断放射防护要求》',
  instrumentDescription: '', writerName: '', reviewerName: '', approverName: '', notes: '',
  hasAec: true, hasRawImages: true, featureReason: '', agencyName: '', region: '', inspectionEndDate: '', protectionMode: 'UNSUBTRACTED', rpdUnit: 'nSv/h', city:'',county:'',profile:blankProfile(modality),
})

const caseForm = reactive<CaseForm>(blankCaseForm(props.modality))
const ruleForm = reactive({
  modality: props.modality as Modality, itemCode: '', ruleVersion: '2026.4-WS', lowerLimit: undefined as number | undefined,
  upperLimit: undefined as number | undefined, expectedText: '', unit: '', standardCode: '', standardName: '', enabled: true, detectionCategory: '验收检测', lowerInclusive: true, upperInclusive: true,
})

const editable = computed(() => selectedCase.value !== null && ['DRAFT', 'DATA_ENTERED', 'CALCULATED'].includes(selectedCase.value.status))
const qcFlowStage = computed(() => {
  const status = selectedCase.value?.status
  if (!status || status === 'DRAFT') return 1
  if (status === 'DATA_ENTERED') return 2
  if (status === 'CALCULATED') return 3
  if (status === 'PENDING_REVIEW' || status === 'REVIEWED') return 4
  return 5
})
const completenessText = computed(() => {
  if (!selectedCase.value) return '未选择'
  const results=calculationItems.value
  if (!results.length) return '待计算'
  return results.filter(r=>r.complete).length + ' / ' + results.length
})
const completenessHint = computed(() => selectedCase.value?.submissionIssues?.length
  ? '仍有 ' + selectedCase.value.submissionIssues.length + ' 项提交条件待补齐'
  : '已完成项目 / 应检清单全部项目')
const nextQcAction = computed(() => {
  const status = selectedCase.value?.status
  if (!status) return `创建或选择一个 ${props.modality} 案例开始录入。`
  if (status === 'DRAFT') return '补全案例信息和原始检测数据，然后保存。'
  if (status === 'DATA_ENTERED') return '执行自动计算并逐项核对标准判定依据。'
  if (status === 'CALCULATED') return '确认完整性和异常项后提交审核。'
  if (status === 'PENDING_REVIEW') return '由审核人员复核原始值、计算结果和防护点位。'
  if (status === 'REVIEWED') return '由批准人员确认结论并完成批准。'
  return '生成或下载受控报告，必要时创建修订版。'
})
const caseStepTitles = ['受检单位与案例信息', '设备与模体信息', '检测与人员信息', '确认并创建']
const caseDraftKey = computed(() => `bhfyzx:radiology-case-draft:${props.modality}`)
const caseDraftStatus = computed(() => {
  if (editingCaseId.value) return caseFormDirty.value ? '有尚未保存的修改' : '当前修改已保存'
  if (caseFormDirty.value) return '正在保存草稿…'
  return caseDraftSavedAt.value ? `草稿已自动保存于 ${caseDraftSavedAt.value}` : '填写内容会自动保存在本机'
})
const editabilityReason = computed(() => {
  if (!selectedCase.value) return '需要先从左侧选择案例'
  if (!editable.value) return `当前状态为“${statusText(selectedCase.value.status)}”，如需修改需要先创建修订版`
  return ''
})

watch(() => props.modality, async (value) => {
  selectedCase.value = null
  calculation.value = null
  query.modalities = [value]
  Object.assign(caseForm, blankCaseForm(value))
  await Promise.all([loadCatalog(), loadCases(), loadRules(), loadLibraries()])
  if(pendingQueryId.value){const id=pendingQueryId.value;pendingQueryId.value=undefined;await selectCase(id);if(pendingQueryEdit.value && editable.value) openEditCase();pendingQueryEdit.value=false}
}, { immediate: true })

watch(caseForm, () => {
  if (!caseDialogVisible.value || caseFormHydrating.value) return
  caseFormDirty.value = true
  caseValidationMessage.value = ''
  if (editingCaseId.value) return
  if (caseDraftTimer) clearTimeout(caseDraftTimer)
  caseDraftTimer = setTimeout(() => persistCaseDraft(), 700)
}, { deep: true })

onBeforeUnmount(() => {
  if (caseDraftTimer) clearTimeout(caseDraftTimer)
})

function errorText(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) return error.response?.data?.message || error.message || fallback
  return fallback
}

async function run(action: () => Promise<void>, success?: string) {
  busy.value = true
  try {
    await action()
    if (success) ElMessage.success(success)
  } catch (error) {
    ElMessage.error(errorText(error, '操作失败'))
  } finally {
    busy.value = false
  }
}

async function loadCatalog() {
  const response = await api.get<ItemDefinition[]>(`/api/radiology/catalog/${props.modality}`)
  catalog.value = response.data
}

async function loadCases() {
  if (query.from && query.to && query.to < query.from) { ElMessage.warning('查询结束日期不能早于开始日期'); return }
  const response = await api.get<{ content: CaseSummary[]; totalElements: number }>('/api/radiology/cases', {
    params: { status: query.status || undefined, keyword: query.keyword || undefined,
      modalities: query.modalities.length ? query.modalities.join(',') : undefined, year:query.year,from:query.from || undefined,to:query.to || undefined,
      city:query.city || undefined,county:query.county || undefined,receipt:query.receipt || undefined,company:query.company || undefined,page:query.page,size:query.size },
  })
  cases.value = response.data.content
  totalCases.value = response.data.totalElements
}

async function loadRules() {
  const response = await api.get<Rule[]>('/api/radiology/rules', { params: { modality: props.modality } })
  rules.value = response.data
}

async function selectCase(id: number) {
  await run(async () => {
    const response = await api.get<InspectionCase>(`/api/radiology/cases/${id}`)
    selectedCase.value = response.data
    calculation.value = null
    syncCaseState()
  })
}

function syncCaseState() {
  for (const key of Object.keys(measurementValues)) delete measurementValues[key]
  const current = selectedCase.value
  for (const name of Object.keys(conditionValues)) delete conditionValues[name]
  Object.assign(conditionValues,current?.itemConditions || {})
  if (!current) return
  Object.assign(profileDraft,blankProfile(current.modality),JSON.parse(JSON.stringify(current.profile || {})))
  for (const value of current.measurements.filter(item => item.fieldRole === 'RAW')) {
    measurementValues[`${value.itemCode}::${value.fieldCode}`] = value.fieldRole === 'RAW' ? (value.valueText ?? value.valueNum ?? '') : (value.valueNum ?? value.valueText ?? '')
  }
  pointDrafts.value = current.protectionPoints.map(point => ({ ...point, readingsText: point.readings.join(', ') }))
}

function formatDraftTime(value = new Date()) {
  return value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function persistCaseDraft(showMessage = false) {
  if (editingCaseId.value) return
  try {
    localStorage.setItem(caseDraftKey.value, JSON.stringify({ modality: props.modality, savedAt: new Date().toISOString(), form: { ...caseForm } }))
    caseDraftSavedAt.value = formatDraftTime()
    caseFormDirty.value = false
    if (showMessage) ElMessage.success('案例草稿已保存在本机')
  } catch {
    if (showMessage) ElMessage.warning('浏览器未能保存草稿，请及时完成案例创建')
  }
}

function loadCaseDraft() {
  try {
    const raw = localStorage.getItem(caseDraftKey.value)
    if (!raw) return false
    const draft = JSON.parse(raw) as { modality?: Modality; savedAt?: string; form?: Partial<CaseForm> }
    if (draft.modality !== props.modality || !draft.form) return false
    Object.assign(caseForm, draft.form, { modality: props.modality })
    caseDraftSavedAt.value = draft.savedAt ? formatDraftTime(new Date(draft.savedAt)) : ''
    return true
  } catch {
    return false
  }
}

function clearCaseDraft() {
  localStorage.removeItem(caseDraftKey.value)
  caseDraftSavedAt.value = ''
}

function resetCaseDialogState() {
  caseDialogStep.value = 0
  caseValidationMessage.value = ''
  caseFormDirty.value = false
  caseDialogCommitted.value = false
}

function openCreateCase() {
  caseFormHydrating.value = true
  resetCaseDialogState()
  editingCaseId.value = null
  Object.assign(caseForm, blankCaseForm(props.modality))
  const time = new Date()
  const stamp = `${time.getFullYear()}${String(time.getMonth() + 1).padStart(2, '0')}${String(time.getDate()).padStart(2, '0')}`
  caseForm.caseCode = `${props.modality}-${stamp}-`
  const restored = loadCaseDraft()
  caseDialogVisible.value = true
  queueMicrotask(() => { caseFormHydrating.value = false })
  if (restored) ElMessage.info('已恢复上次未提交的案例草稿')
}

function openEditCase() {
  const current = selectedCase.value
  for (const name of Object.keys(conditionValues)) delete conditionValues[name]
  Object.assign(conditionValues,current?.itemConditions || {})
  if (!current) return
  caseFormHydrating.value = true
  resetCaseDialogState()
  editingCaseId.value = current.id
  const form = blankCaseForm(current.modality)
  for (const key of Object.keys(form) as Array<keyof CaseForm>) {
    const value = current[key as keyof InspectionCase]
    if (typeof value === 'string' || typeof value === 'boolean') (form as unknown as Record<string, unknown>)[key] = value
  }
  form.profile = { ...blankProfile(current.modality), ...JSON.parse(JSON.stringify(current.profile || {})) }
  if(!form.profile.selectedGroups)form.profile.selectedGroups=blankProfile(current.modality).selectedGroups
  if(form.protectionMode==='RESPONSE_CORRECTED'){form.protectionMode='SUBTRACTED';form.profile.calibrationMode='BOTH'}
  form.modality = current.modality
  form.caseCode = current.caseCode
  form.inspectedUnitName = current.inspectedUnitName
  form.equipmentName = current.equipmentName
  Object.assign(caseForm, form)
  caseDialogVisible.value = true
  queueMicrotask(() => { caseFormHydrating.value = false })
}

function validateCaseStep(step = caseDialogStep.value) {
  let message = ''
  if (step === 0) {
    if (!caseForm.caseCode.trim()) message = '需要填写案例编号'
    else if (!caseForm.inspectedUnitName.trim()) message = '需要填写受检单位'
    else if (!caseForm.sampleAcceptanceNo.trim()) message = '需要填写受理编号'
    else if (!caseForm.detectionCategory) message = '需要选择检测类别'
  } else if (step === 1 && !caseForm.equipmentName.trim()) {
    message = '需要填写设备名称'
  }
  if(step===2) {
    if(!caseForm.inspectionDate || !caseForm.inspectionEndDate) message='需要填写检测起止日期'
    else if(caseForm.inspectionEndDate < caseForm.inspectionDate) message='结束日期不能早于开始日期'
  }
  caseValidationMessage.value = message
  if (message) ElMessage.warning(message)
  return !message
}

function nextCaseStep() {
  if (!validateCaseStep()) return
  caseDialogStep.value = Math.min(caseDialogStep.value + 1, caseStepTitles.length - 1)
}

function previousCaseStep() {
  caseValidationMessage.value = ''
  caseDialogStep.value = Math.max(caseDialogStep.value - 1, 0)
}

function saveCaseDraftManually() {
  persistCaseDraft(true)
}

async function beforeCloseCaseDialog(done: () => void) {
  if (caseDialogCommitted.value) {
    done()
    return
  }
  if (!caseFormDirty.value && editingCaseId.value) {
    done()
    return
  }
  const message = editingCaseId.value
    ? '当前案例还有未保存的修改，确认关闭吗？'
    : '案例尚未创建，当前内容会作为本机草稿保留。确认关闭吗？'
  try {
    await ElMessageBox.confirm(message, '关闭确认', { confirmButtonText: '确认关闭', cancelButtonText: '继续填写', type: 'warning' })
    if (!editingCaseId.value) persistCaseDraft()
    done()
  } catch {
    // 继续填写
  }
}

function requestCloseCaseDialog() {
  beforeCloseCaseDialog(() => { caseDialogVisible.value = false })
}

async function saveCase() {
  if (!validateCaseStep(0)) {
    caseDialogStep.value = 0
    return
  }
  if (!validateCaseStep(1)) {
    caseDialogStep.value = 1
    return
  }
  const wasEditing = Boolean(editingCaseId.value)
  await run(async () => {
    if(!validateCaseStep(2)) {caseDialogStep.value=2;return}
    const payload = { ...caseForm, profile:{...caseForm.profile, manufacturedDate:caseForm.profile.manufacturedDate || null}, inspectionDate: caseForm.inspectionDate || null, reportDate: caseForm.reportDate || null,
      inspectionEndDate: caseForm.inspectionEndDate || null, expectedDataVersion: selectedCase.value?.dataVersion }
    const response = editingCaseId.value
      ? await api.put<InspectionCase>(`/api/radiology/cases/${editingCaseId.value}`, payload)
      : await api.post<InspectionCase>('/api/radiology/cases', payload)
    selectedCase.value = response.data
    syncCaseState()
    caseDialogCommitted.value = true
    caseFormDirty.value = false
    if (!wasEditing) clearCaseDraft()
    caseDialogVisible.value = false
    await loadCases()
  }, editingCaseId.value ? '案例信息已更新' : '检测案例已创建')
}

async function removeCase(item: CaseSummary) {
  await ElMessageBox.confirm(`确认删除案例 ${item.caseCode}？`, '删除确认', { type: 'warning' })
  await run(async () => {
    await api.delete(`/api/radiology/cases/${item.id}`)
    if (selectedCase.value?.id === item.id) selectedCase.value = null
    await loadCases()
  }, '案例已删除')
}

function visibleFields(item: ItemDefinition) {
  return item.fields.filter(f=>{
    if(item.code==='LCD' && (f.code.startsWith('NUM') || f.code.startsWith('mm')))return selectedCase.value?.measurements.some(m=>m.itemCode==='LCD' && m.fieldCode===f.code && m.fieldRole==='RAW' && m.valueNum!=null)
    if(item.code==='HCR' && f.code==='厂家规定值/基线值')return selectedCase.value?.measurements.some(m=>m.itemCode==='HCR' && m.fieldCode===f.code && m.fieldRole==='RAW' && m.valueNum!=null)
    if(item.code==='RST' && ['WWmin','CThm'].includes(f.code))return false
    if(item.code==='GTA' && conditionValues['__method.GTA']==='PHANTOM' && f.code==='测量值α2')return false
    if(item.code==='GTA' && conditionValues['__method.GTA']==='INCLINOMETER' && f.code.includes('边沿间距'))return false
    return true
  })
}
function conditionRows(code: string): string[] {return ({TVD:['第1组','第2组','第3组'],LRD:['左a1','右a2','前b1','后b2'],REA:['水平方向','垂直方向'],TPA:['前进（负载70kg）','后退（负载70kg）'],CTDI:['头部模体','体部模体']} as Record<string,string[]>)[code] || []}
function textOptions(code: string): string[] {
  if(code==='UBD-QUALIFIER')return ['>','<']
  if(code==='ART-SOURCE')return ['探测器','监视器']
  if(code==='LCR-BASELINE-UNIT')return ['个','%']
  if(code==='填“是”或“否”' || code.startsWith('是否有残影'))return ['是','否']
  return []
}
function isTextField(_itemCode: string, fieldCode: string) {
  return fieldCode === '填“是”或“否”' || fieldCode.startsWith('是否有残影') || ['STP-MODEL','UBD-QUALIFIER','ART-SOURCE','LCR-BASELINE-UNIT'].includes(fieldCode)
}

function key(itemCode: string, fieldCode: string) {
  return `${itemCode}::${fieldCode}`
}

function computedFields(itemCode: string) {
  return (selectedCase.value?.measurements || []).filter(value => value.itemCode === itemCode && value.fieldRole !== 'RAW')
}

function readingUnit(itemCode: string) {
  try { const unit=JSON.parse(conditionValues[`__params.${itemCode}`] || '{}').doseUnit || (['ROA','ROQ'].includes(itemCode) ? 'mGy' : itemCode==='ARS' ? 'μGy' : 'mAs');return unit==='mA,s' ? 'mAs' : unit } catch { return '' }
}
function isAicExposurePair(itemCode:string) {
  try {return itemCode==='AIC' && JSON.parse(conditionValues['__params.AIC'] || '{}').doseUnit==='mA,s'} catch {return false}
}
function aicExposurePair(field:string): {current:string;time:string} {
  try {return JSON.parse(conditionValues['__aic.pairs'] || '{}')[field] || {current:'',time:''}} catch {return {current:'',time:''}}
}
function updateAicExposurePair(field:string,part:'current'|'time',value:string) {
  let pairs:Record<string,{current:string;time:string}>={}
  try {pairs=JSON.parse(conditionValues['__aic.pairs'] || '{}')} catch { /* Start with an empty structured pair. */ }
  const pair={...aicExposurePair(field),[part]:value};pairs[field]=pair;conditionValues['__aic.pairs']=JSON.stringify(pairs)
  measurementValues[key('AIC',field)]=pair.current && pair.time ? String(Number(pair.current)*Number(pair.time)) : ''
}
function rawFieldUnit(itemCode: string, field: FieldDefinition) {
  if(itemCode === 'RPD' && field.code.startsWith('RPD0-')) return selectedCase.value?.rpdUnit || 'nSv/h'
  if(['ROA','ROQ','ARP','ARS','AIC'].includes(itemCode) && new RegExp(`^${itemCode}(?:\\d|-)`).test(field.code) && !field.code.includes('-K')) return readingUnit(itemCode)
  return field.unit
}
function itemResultDisplay(item: CalculationItem) {
  if(item.resultText) return item.resultText
  const result=selectedCase.value?.measurements.find(m=>m.itemCode===item.itemCode && m.fieldCode==='RESULT' && m.fieldRole==='FINAL')
  return result ? resultDisplay(result) : `${item.resultValue ?? '-'} ${item.resultUnit || ''}`.trim()
}

async function saveMeasurements() {
  if (!selectedCase.value) return
  const values: Array<{ itemCode: string; fieldCode: string; valueNum?: number | string | null; valueText?: string | null }> = []
  for (const item of catalog.value) {
    for (const field of item.fields) {
      const raw = measurementValues[key(item.code, field.code)]
      if (raw === '' || raw === undefined || raw === null) {
        if (selectedCase.value.measurements.some(v => v.itemCode === item.code && v.fieldCode === field.code && v.fieldRole === 'RAW'))
          values.push({ itemCode: item.code, fieldCode: field.code, valueNum: null, valueText: null })
        continue
      }
      if (isTextField(item.code, field.code)) values.push({ itemCode: item.code, fieldCode: field.code, valueText: String(raw) })
      else {
        const number = Number(raw)
        if (!Number.isFinite(number)) {
          ElMessage.warning(`${item.code} / ${field.label} 不是有效数值`)
          return
        }
        values.push({ itemCode: item.code, fieldCode: field.code, valueNum: String(raw).trim() })
      }
    }
  }
  if (!values.length && !isAicExposurePair('AIC')) {
    ElMessage.warning('尚未填写测量数据')
    return
  }
  await run(async () => {
    const response = await api.put<InspectionCase>(`/api/radiology/cases/${selectedCase.value?.id}/measurements`, { values, conditions: conditionValues, expectedDataVersion: selectedCase.value?.dataVersion })
    selectedCase.value = response.data
    syncCaseState()
    await loadCases()
  }, `已保存 ${values.length} 个测量值`)
}

async function calculateCase() {
  if (!selectedCase.value) return
  const conditionsPending = JSON.stringify(conditionValues) !== JSON.stringify(selectedCase.value.itemConditions || {})
  const pending = conditionsPending || catalog.value.some(item => item.fields.some(field => {
    const current = measurementValues[key(item.code, field.code)] ?? ''
    const stored = selectedCase.value!.measurements.find(v => v.itemCode === item.code && v.fieldCode === field.code && v.fieldRole === 'RAW')
    return String(current) !== String(stored?.valueText ?? stored?.valueNum ?? '')
  }))
  if (pending) {
    await saveMeasurements()
    const stillPending = catalog.value.some(item => item.fields.some(field => {
      const stored = selectedCase.value!.measurements.find(v => v.itemCode === item.code && v.fieldCode === field.code && v.fieldRole === 'RAW')
      return String(measurementValues[key(item.code,field.code)] ?? '') !== String(stored?.valueText ?? stored?.valueNum ?? '')
    }))
    if (stillPending) return
  }
  await run(async () => {
    const response = await api.post<CalculationResponse>(`/api/radiology/cases/${selectedCase.value?.id}/calculate`)
    await selectCase(selectedCase.value!.id)
    calculation.value = response.data
    await loadCases()
    workTab.value = 'data'
  }, '计算完成')
}

function resultDisplay(value: Measurement) {
  const unit=value.unit || (['ROQ','ARP','ARS','AIC'].includes(value.itemCode) && value.fieldCode.includes('MEAN') ? readingUnit(value.itemCode) : '')
  if (value.displayValue) return value.displayValue + (unit ? " " + unit : "")
  if (value.valueText) return value.valueText
  if (value.valueNum === undefined || value.valueNum === null) return '-'
  return `${value.valueNum}${value.unit ? ` ${value.unit}` : ''}`
}

function onImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  importFile.value = input.files?.[0] || null
  importPreview.value = null
}

async function importExcel(dryRun: boolean) {
  if (!selectedCase.value || !importFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  await run(async () => {
    const form = new FormData()
    form.append('file', importFile.value!)
    const response = await api.post<ImportPreview>(`/api/radiology/cases/${selectedCase.value?.id}/import`, form, {
      params: { type: importType.value, dryRun }, headers: { 'Content-Type': 'multipart/form-data' },
    })
    importPreview.value = response.data
    if (!dryRun && response.data.errors.length === 0) await selectCase(selectedCase.value!.id)
  }, dryRun ? '导入预检已完成' : 'Excel 数据已导入')
}

async function downloadTemplate(type: 'PERFORMANCE' | 'PROTECTION') {
  await downloadBlob(`/api/radiology/templates/${props.modality}/${type}`, `${props.modality}-${type}.xlsx`)
}

function addProtectionPoint() {
  const index = pointDrafts.value.length + 1
  pointDrafts.value.push({
    pointCode: `RPD${index}`, pointName: `检测点位${index}`, readings: [], readingsText: '',
    occupancyFactor: 1, useFactor: 1, sortNo: index,
  })
}

async function saveProtectionPoints() {
  if (!selectedCase.value) return
  const points: Array<{
    pointCode: string; pointName: string; locationDescription?: string; readings: number[];
    annualWorkloadMas?: number; occupancyFactor?: number; useFactor?: number; sortNo: number
  }> = []
  for (const draft of pointDrafts.value) {
    const readings = draft.readingsText.split(/[，,\s]+/).filter(Boolean).map(Number)
    if (!readings.length || readings.some(Number.isNaN)) {
      ElMessage.warning(`${draft.pointName} 需要填写有效读数`)
      return
    }
    points.push({
      pointCode: draft.pointCode, pointName: draft.pointName, locationDescription: draft.locationDescription,
      readings, annualWorkloadMas: draft.annualWorkloadMas, occupancyFactor: draft.occupancyFactor,
      useFactor: draft.useFactor, sortNo: draft.sortNo,
    })
  }
  await run(async () => {
    const response = await api.put<ProtectionPoint[]>(`/api/radiology/cases/${selectedCase.value?.id}/protection-points`, { points, expectedDataVersion: selectedCase.value?.dataVersion })
    pointDrafts.value = response.data.map(point => ({ ...point, readingsText: point.readings.join(', ') }))
    await selectCase(selectedCase.value!.id)
  }, '防护点位已保存并计算')
}

async function removePoint(point: ProtectionPoint & { readingsText: string }, index: number) {
  if (!selectedCase.value || !point.id) {
    pointDrafts.value.splice(index, 1)
    return
  }
  await run(async () => {
    await api.delete(`/api/radiology/cases/${selectedCase.value?.id}/protection-points/${point.id}`)
    pointDrafts.value.splice(index, 1)
  }, '点位已删除')
}

async function uploadAttachment(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedCase.value) return
  await run(async () => {
    const form = new FormData()
    form.append('file', file)
    await api.post(`/api/radiology/cases/${selectedCase.value?.id}/attachments`, form, {
      params: { category: attachmentCategory.value }, headers: { 'Content-Type': 'multipart/form-data' },
    })
    await selectCase(selectedCase.value!.id)
    input.value = ''
  }, '附件已上传')
}

async function removeAttachment(item: Attachment) {
  await ElMessageBox.confirm(`确认删除附件 ${item.originalName}？`, '删除确认', { type: 'warning' })
  await run(async () => {
    await api.delete(`/api/radiology/attachments/${item.id}`)
    await selectCase(selectedCase.value!.id)
  }, '附件已删除')
}

async function workflow(action: 'submit' | 'review' | 'approve' | 'return') {
  if (!selectedCase.value) return
  const names = { submit: '提交审核', review: '审核通过', approve: '批准', return: '退回修改' }
  const input = await ElMessageBox.prompt(`请输入${names[action]}意见（可留空）`, names[action], { inputType: 'textarea' }).catch(() => null)
  if (!input) return
  await run(async () => {
    const response = await api.post<InspectionCase>(`/api/radiology/cases/${selectedCase.value?.id}/workflow/${action}`, { comment: input.value, expectedDataVersion: selectedCase.value?.dataVersion })
    selectedCase.value = response.data
    syncCaseState()
    await loadCases()
  }, `${names[action]}完成`)
}

async function createRevision() {
  if (!selectedCase.value) return
  await ElMessageBox.confirm('将复制全部原始检测数据并创建可编辑修订版，继续吗？', '创建修订版')
  await run(async () => {
    const response = await api.post<InspectionCase>(`/api/radiology/cases/${selectedCase.value?.id}/workflow/revision`)
    selectedCase.value = response.data
    syncCaseState()
    await loadCases()
  }, '修订版已创建')
}

async function generateReport() {
  if (!selectedCase.value) return
  await run(async () => {
    await api.post(`/api/radiology/cases/${selectedCase.value?.id}/reports`)
    await selectCase(selectedCase.value!.id)
    workTab.value = 'report'
  }, 'DOCX 报告已生成并固化快照')
}

async function downloadBlob(url: string, fallback: string) {
  await run(async () => {
    const response = await api.get<Blob>(url, { responseType: 'blob' })
    const disposition = response.headers['content-disposition'] as string | undefined
    const match = disposition?.match(/filename\*=UTF-8''([^;]+)/i)
    const fileName = match ? decodeURIComponent(match[1]) : fallback
    const href = URL.createObjectURL(response.data)
    const anchor = document.createElement('a')
    anchor.href = href
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(href)
  })
}

function openCreateRule() {
  editingRuleId.value = null
  Object.assign(ruleForm, {
    modality: props.modality, itemCode: catalog.value[0]?.code || '', ruleVersion: '2026.10.13-custom', lowerLimit: undefined,
    upperLimit: undefined, expectedText: '', unit: '', standardCode: props.modality === 'DR' ? 'WS 76-2020' : 'WS 519-2019',
    standardName: '设备质量控制检测规范', enabled: true, detectionCategory: '验收检测', lowerInclusive: true, upperInclusive: true,
  })
  ruleDialogVisible.value = true
}

function openEditRule(item: Rule) {
  editingRuleId.value = item.ruleVersion.startsWith('WS-') ? null : item.id
  Object.assign(ruleForm, item)
  if (item.ruleVersion.startsWith('WS-')) { ruleForm.ruleVersion = '2026.10.13-custom'; ruleForm.detectionCategory = '验收检测' }
  ruleDialogVisible.value = true
}

async function saveRule() {
  if (!ruleForm.itemCode || !ruleForm.ruleVersion || !ruleForm.standardCode || !ruleForm.standardName) {
    ElMessage.warning('项目、版本和标准信息为必填项')
    return
  }
  await run(async () => {
    if (editingRuleId.value) await api.put(`/api/radiology/rules/${editingRuleId.value}`, ruleForm)
    else await api.post('/api/radiology/rules', ruleForm)
    ruleDialogVisible.value = false
    await loadRules()
  }, '判定规则已保存')
}

async function removeRule(item: Rule) {
  await ElMessageBox.confirm(`确认删除 ${item.itemCode} / ${item.ruleVersion} 规则？`, '删除确认', { type: 'warning' })
  await run(async () => {
    await api.delete(`/api/radiology/rules/${item.id}`)
    await loadRules()
  }, '规则已删除')
}

function statusText(status: CaseStatus) {
  return ({
    DRAFT: '草稿', DATA_ENTERED: '已录入', CALCULATED: '已计算', PENDING_REVIEW: '待审核',
    REVIEWED: '已审核', APPROVED: '已批准', REPORTED: '已出报告',
  } as Record<CaseStatus, string>)[status]
}

function statusType(status: CaseStatus) {
  if (status === 'APPROVED' || status === 'REPORTED') return 'success'
  if (status === 'PENDING_REVIEW' || status === 'REVIEWED') return 'warning'
  if (status === 'CALCULATED') return 'primary'
  return 'info'
}

function judgementText(value?: string) {
  return ({
    QUALIFIED: '合格', UNQUALIFIED: '不合格', NOT_CONFIGURED: '待补充依据', INCOMPLETE: '数据不完整',
    NOT_APPLICABLE: '本类别不适用', BASELINE_ESTABLISHED: '建立基线', NEEDS_ASSESSMENT: '需年剂量评估',
  } as Record<string, string>)[value || ''] || value || '-'
}

function measurementHelpAnchor(field: FieldDefinition) {
  const code = field.code.toUpperCase()
  if (code.includes('CTDI') && code.includes('MANUFACTURER')) return 'radiology.ctdi-manufacturer'
  if (code.includes('BASELINE')) return 'radiology.stability-baseline'
  return 'radiology.raw-value'
}

function showMeasurementHelp(field: FieldDefinition) {
  const code = field.code.toUpperCase()
  return field.required || code.includes('CTDI') || code.includes('BASELINE')
}
</script>

<template>
  <div class="radiology-workbench" v-loading="busy">
    <div class="comparison-banner">
      <strong>10月13日新版 · 应检清单与审批改造</strong>
      <span>原始数据修改后重新计算；报告采用批准快照</span>
      <a href="/test1013-changes.html" target="_blank" rel="noopener">查看改动对照</a>
    </div>
    <header class="qc-hero">
      <div>
        <span class="modality-mark">{{ modality }}</span>
        <div>
          <h2>{{ modality }} 设备质量控制检测 <HelpMark anchor-id="radiology.case" /></h2>
          <p>{{ modality === 'DR' ? 'WS 76-2020' : 'WS 519-2019' }} · GBZ 130-2020 · 计算规则 2026.10.13</p>
        </div>
      </div>
      <div class="hero-actions">
        <el-button @click="loadCases">刷新</el-button>
        <el-button type="primary" @click="openCreateCase">新建 {{ modality }} 案例</el-button>
      </div>
    </header>

    <nav class="qc-flow" aria-label="放射检测流转阶段">
      <div v-for="(item, index) in ['案例建档','检测录入','自动计算','审核批准','报告归档']" :key="item" :class="{ active: qcFlowStage === index + 1, done: qcFlowStage > index + 1 }">
        <span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ item }}</strong>
      </div>
      <HelpMark class="qc-flow-help" anchor-id="workflow.stages" />
    </nav>

    <div class="qc-layout">
      <aside class="case-rail">
        <div class="rail-filter">
          <el-input v-model="query.keyword" clearable placeholder="编号、单位、设备编号" @keyup.enter="loadCases" />
          <el-select v-model="query.status" clearable placeholder="全部状态" @change="loadCases">
            <el-option v-for="status in ['DRAFT','DATA_ENTERED','CALCULATED','PENDING_REVIEW','REVIEWED','APPROVED','REPORTED']" :key="status" :label="statusText(status as CaseStatus)" :value="status" />
          </el-select>
          <el-button @click="loadCases">查询</el-button><el-button @click="queryVisible=true">综合查询</el-button>
        </div>
        <div class="case-count">共 {{ totalCases }} 个案例</div>
        <div v-if="!cases.length" class="rail-empty">暂无 {{ modality }} 案例</div>
        <article
          v-for="item in cases"
          :key="item.id"
          class="case-card"
          :class="{ active: selectedCase?.id === item.id }"
          role="button"
          tabindex="0"
          :aria-label="`打开案例 ${item.caseCode}，状态 ${statusText(item.status)}`"
          :aria-pressed="selectedCase?.id === item.id"
          @click="selectQueryCase(item)"
          @keydown.enter.prevent="selectQueryCase(item)"
          @keydown.space.prevent="selectQueryCase(item)"
        >
          <div class="case-line">
            <strong>{{ item.caseCode }}</strong>
            <el-tag size="small" :type="statusType(item.status)">{{ statusText(item.status) }}</el-tag>
          </div>
          <p>{{ item.inspectedUnitName }}</p>
          <span>{{ item.equipmentName }} {{ item.equipmentModel || '' }}</span>
          <div class="case-foot">
            <small>R{{ item.revisionNo }} · {{ item.inspectionDate || '未填检测日期' }}</small>
            <el-button v-if="!['APPROVED','REPORTED'].includes(item.status)" link type="danger" @click.stop="removeCase(item)">删除</el-button>
          </div>
        </article>
      </aside>

      <main class="case-main">
        <div v-if="!selectedCase" class="select-hint">
          <div class="hint-mark">{{ modality }}</div>
          <h3>选择左侧案例开始录入</h3>
          <p>支持逐项录入、Excel 批量导入、自动计算、防护点位、审核批准和报告生成。</p>
          <el-button type="primary" @click="openCreateCase">创建第一个案例</el-button>
        </div>

        <template v-else>
          <section class="case-heading">
            <div>
              <div class="heading-line">
                <h3>{{ selectedCase.caseCode }}</h3>
                <el-tag :type="statusType(selectedCase.status)">{{ statusText(selectedCase.status) }}</el-tag>
                <span class="version-pill">R{{ selectedCase.revisionNo }}</span>
              </div>
              <p>{{ selectedCase.inspectedUnitName }} · {{ selectedCase.equipmentName }} {{ selectedCase.equipmentModel || '' }}</p>
            </div>
            <div class="workflow-actions">
              <el-button v-if="editable" @click="openEditCase">编辑信息</el-button>
              <el-button v-if="editable" type="primary" plain @click="saveMeasurements">保存数据</el-button>
              <el-button v-if="editable" type="primary" @click="calculateCase">自动计算</el-button><HelpMark v-if="editable" anchor-id="radiology.calculation" />
              <el-button v-if="selectedCase.status === 'CALCULATED'" :disabled="!!selectedCase.submissionIssues?.length" type="warning" @click="workflow('submit')">提交审核</el-button><HelpMark v-if="selectedCase.status === 'CALCULATED'" anchor-id="radiology.review" />
              <el-button v-if="selectedCase.status === 'PENDING_REVIEW' && props.canReview" type="warning" @click="workflow('review')">审核通过</el-button><HelpMark v-if="selectedCase.status === 'PENDING_REVIEW' && props.canReview" anchor-id="radiology.review" />
              <el-button v-if="selectedCase.status === 'REVIEWED' && props.canApprove" type="success" @click="workflow('approve')">批准</el-button><HelpMark v-if="selectedCase.status === 'REVIEWED' && props.canApprove" anchor-id="radiology.approve" />
              <el-button v-if="['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" type="success" @click="generateReport">生成报告</el-button><HelpMark v-if="['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" anchor-id="radiology.report" />
              <el-button v-if="(selectedCase.status === 'PENDING_REVIEW' && props.canReview) || (selectedCase.status === 'REVIEWED' && props.canApprove)" type="danger" plain @click="workflow('return')">退回修改</el-button>
              <el-button v-if="['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" @click="createRevision">创建修订版</el-button>
            </div>
          </section>

          <section v-if="editable" class="submission-check">
            <strong>{{ selectedCase.calculationFresh ? '计算与当前数据一致' : '需要重新计算' }}</strong>
            <span>数据版本 {{ selectedCase.dataVersion }} · 计算版本 {{ selectedCase.calculatedDataVersion ?? '未计算' }}</span>
            <details v-if="selectedCase.submissionIssues?.length">
              <summary>提交前需要完成 {{ selectedCase.submissionIssues.length }} 项</summary>
              <ul><li v-for="issue in selectedCase.submissionIssues" :key="issue">{{ issue }}</li></ul>
            </details>
          </section>
          <el-tabs v-model="workTab" class="qc-tabs">
            <el-tab-pane label="基本信息" name="base">
              <div class="info-grid">
                <div><span>受理编号</span><strong>{{ selectedCase.sampleAcceptanceNo || '-' }}</strong></div>
                <div><span>设备编号</span><strong>{{ selectedCase.equipmentNo || '-' }}</strong></div>
                <div><span>生产厂家 <HelpMark anchor-id="radiology.equipment" /></span><strong>{{ selectedCase.manufacturer || '-' }}</strong></div>
                <div v-if="modality === 'CT'"><span>检测模体 <HelpMark anchor-id="radiology.phantom" /></span><strong>{{ selectedCase.phantomName || '-' }}</strong></div>
                <div v-if="modality === 'CT'"><span>模体厂家/型号 <HelpMark anchor-id="radiology.phantom" /></span><strong>{{ [selectedCase.phantomManufacturer, selectedCase.phantomModel].filter(Boolean).join(' / ') || '-' }}</strong></div>
                <div><span>检测地点</span><strong>{{ selectedCase.detectionLocation || '-' }}</strong></div>
                <div><span>检测日期</span><strong>{{ selectedCase.inspectionDate || '-' }}</strong></div>
                <div><span>计算版本 <HelpMark anchor-id="radiology.calculation" /></span><strong>{{ selectedCase.calculationVersion }}</strong></div>
              </div>
              <div class="text-block"><span>检测依据 <HelpMark anchor-id="basic.reference" /></span><p>{{ selectedCase.standardBasis || '-' }}</p></div>
              <div class="text-block"><span>检测仪器 <HelpMark anchor-id="radiology.instrument" /></span><p>{{ selectedCase.instrumentDescription || '-' }}</p></div>
            </el-tab-pane>

            <el-tab-pane label="检测数据与计算" name="data">
              <div class="import-strip">
                <div>
                  <strong>Excel 批量导入 <HelpMark anchor-id="radiology.excel-import" /></strong>
                  <p>先预检字段和数值，通过后再正式写入。</p>
                </div>
                <el-select v-model="importType" style="width: 140px">
                  <el-option label="性能数据" value="PERFORMANCE" />
                  <el-option label="防护数据" value="PROTECTION" />
                </el-select>
                <input type="file" accept=".xlsx,.xls" :disabled="!editable" @change="onImportFile" />
                <el-button :disabled="!editable || !importFile" @click="importExcel(true)">预检</el-button>
                <el-button type="primary" :disabled="!editable || !importFile" @click="importExcel(false)">正式导入</el-button>
                <el-button link @click="downloadTemplate('PERFORMANCE')">性能模板</el-button>
                <el-button link @click="downloadTemplate('PROTECTION')">防护模板</el-button>
              </div>
              <el-alert
                v-if="importPreview"
                :type="importPreview.errors.length ? 'error' : 'success'"
                :closable="false"
                show-icon
                :title="`识别 ${importPreview.recognizedFields} 个字段，${importPreview.dryRun ? '预检未写入' : `已写入 ${importPreview.appliedValues} 个值`}`"
              >
                <template #default>
                  <p v-for="error in importPreview.errors" :key="`${error.column}-${error.header}`">第 {{ error.column }} 列 {{ error.header }}：{{ error.message }}（{{ error.value }}）</p>
                  <p v-if="importPreview.ignoredHeaders.length">忽略字段：{{ importPreview.ignoredHeaders.join('、') }}</p>
                </template>
              </el-alert>

              <el-collapse class="item-collapse">
                <el-collapse-item v-for="item in catalog" :key="item.code" :name="item.code">
                  <template #title>
                    <div class="item-title">
                      <span class="item-code">{{ item.code }}</span>
                      <strong>{{ item.name }}</strong>
                      <small>{{ item.standardCode }}</small>
                      <el-tag v-if="computedFields(item.code).some(value => value.fieldRole === 'FINAL')" size="small" type="success">已计算</el-tag>
                    </div>
                  </template>
                  <p v-if="calculationItems.find(r => r.itemCode === item.code)?.judgement === 'NOT_APPLICABLE'" class="item-exclusion">
                    {{ calculationItems.find(r => r.itemCode === item.code)?.ruleDescription }}
                  </p>
                  <RadiologyConditionEditor v-if="item.code!=='RPD'" v-model="conditionValues[item.code]" v-model:parameters="conditionValues['__params.'+item.code]" :item-code="item.code" :disabled="!editable" />
                  <div v-if="conditionRows(item.code).length" class="condition-subrows"><label v-for="(name,index) in conditionRows(item.code)" :key="name">{{name}}检测条件<el-input v-model="conditionValues[item.code+'.'+(index+1)]" :disabled="!editable" placeholder="留空使用上方共有条件；可补充本组曝光参数" /></label></div>
                  <el-select v-if="item.code==='GTA'" v-model="conditionValues['__method.GTA']" :disabled="!editable" placeholder="选择倾角测量方法"><el-option label="模体法" value="PHANTOM" /><el-option label="倾角仪法" value="INCLINOMETER" /></el-select>
                  <div class="field-grid">
                    <label v-for="field in visibleFields(item)" :key="field.code" class="field-card">
                      <span>{{ field.label }} <em v-if="field.required">*</em> <HelpMark v-if="showMeasurementHelp(field)" :anchor-id="measurementHelpAnchor(field)" /></span>
                      <div v-if="isAicExposurePair(item.code)" class="aic-exposure-pair">
                        <el-input :model-value="aicExposurePair(field.code).current" type="number" :disabled="!editable" placeholder="电流" @update:model-value="updateAicExposurePair(field.code,'current',$event)"><template #append>mA</template></el-input>
                        <el-input :model-value="aicExposurePair(field.code).time" type="number" :disabled="!editable" placeholder="曝光时间" @update:model-value="updateAicExposurePair(field.code,'time',$event)"><template #append>s</template></el-input>
                      </div>
                      <el-select v-else-if="textOptions(field.code).length" v-model="measurementValues[key(item.code,field.code)]" :disabled="!editable" clearable>
                        <el-option v-for="o in textOptions(field.code)" :key="o" :value="o" :label="o" />
                      </el-select>
                      <el-select
                        v-else-if="field.code === 'STP-MODEL'"
                        v-model="measurementValues[key(item.code, field.code)]"
                        :disabled="!editable"
                        placeholder="选择设备信号响应模型"
                        style="width:100%"
                      >
                        <el-option label="线性 PV=aK+c" value="LINEAR" />
                        <el-option label="对数 PV=a·ln(K)+c" value="LOG" />
                        <el-option label="幂函数 PV=a·K^b+c" value="POWER" />
                        <el-option label="自动选择最佳拟合（暂用）" value="BEST_FIT" />
                      </el-select>
                      <el-input
                        v-else
                        v-model="measurementValues[key(item.code, field.code)]"
                        :type="isTextField(item.code, field.code) ? 'text' : 'number'"
                        :disabled="!editable || field.code === 'a'"
                        :placeholder="field.code"
                      >
                        <template v-if="rawFieldUnit(item.code, field)" #append>{{ rawFieldUnit(item.code, field) }}</template>
                      </el-input>
                    </label>
                  </div>
                  <div v-if="computedFields(item.code).length" class="computed-panel">
                    <div v-for="value in computedFields(item.code)" :key="value.id" :class="['computed-value', value.fieldRole.toLowerCase()]">
                      <span>{{ value.fieldLabel }}</span>
                      <strong>{{ resultDisplay(value) }}</strong>
                    </div>
                  </div>
                </el-collapse-item>
              </el-collapse>

              <section v-if="calculationItems.length" class="calculation-summary">
                <h4>本次计算结果 <HelpMark anchor-id="radiology.calculation" /></h4>
                <el-table :data="calculationItems" border>
                  <el-table-column prop="itemCode" label="代码" width="84" />
                  <el-table-column prop="itemName" label="检测项目" min-width="170" />
                  <el-table-column label="结果" min-width="120">
                    <template #default="scope">{{ itemResultDisplay(scope.row) }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="120">
                    <template #default="scope"><el-tag :type="scope.row.complete ? 'success' : 'danger'">{{ scope.row.complete ? '完成' : '不完整' }}</el-tag></template>
                  </el-table-column>
                  <el-table-column label="判定" width="130">
                    <template #default="scope">{{ judgementText(scope.row.judgement) }}</template>
                  </el-table-column>
                  <el-table-column prop="ruleDescription" label="标准判定依据" min-width="320" />
                  <el-table-column prop="message" label="提示" min-width="220" />
                </el-table>
              </section>
            </el-tab-pane>

            <el-tab-pane label="防护点位" name="points">
              <div class="section-toolbar">
                <div><h4>场所卫生防护点位 <HelpMark anchor-id="radiology.protection-point" /></h4><p>DR 超过 25 μSv/h 后，系统按年工作负荷、居留因子和使用因子继续计算年有效剂量，并按 0.25 mSv/a 判定；CT 按 2.5 μSv/h 判定。</p></div>
                <el-button :disabled="!editable" @click="addProtectionPoint">增加点位</el-button>
                <el-button type="primary" :disabled="!editable || !pointDrafts.length" @click="saveProtectionPoints">保存并计算</el-button>
              </div>
              <div class="protection-context">
                <el-input v-model="profileDraft.protectionConditions" :disabled="!editable" placeholder="防护检测条件：kV、mA、s/mAs、模体、扫描方式等" />
                <el-input v-model="profileDraft.backgroundLocation" :disabled="!editable" placeholder="本底测量地点" />
                <el-input v-model="profileDraft.backgroundOperation" :disabled="!editable" placeholder="本底运行情况（未曝光）" />
                <el-input v-model="profileDraft.instrumentResponseTime" :disabled="!editable" placeholder="仪器响应时间（含单位）" />
                <el-button :disabled="!editable" @click="saveReportProfile">保存条件</el-button>
              </div>
              <el-table :data="pointDrafts" border>
                <el-table-column label="点位代码" width="120"><template #default="scope"><el-input v-model="scope.row.pointCode" :disabled="!editable" /></template></el-table-column>
                <el-table-column label="点位名称" width="150"><template #default="scope"><el-input v-model="scope.row.pointName" :disabled="!editable" /></template></el-table-column>
                <el-table-column label="位置描述" min-width="180"><template #default="scope"><el-input v-model="scope.row.locationDescription" :disabled="!editable" /></template></el-table-column>
                <el-table-column label="读数（逗号分隔）" min-width="250"><template #default="scope"><el-input v-model="scope.row.readingsText" :disabled="!editable" placeholder="88, 99, 98, 87, 86" /></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="150"><template #header>年工作负荷 <HelpMark anchor-id="radiology.annual-workload" /></template><template #default="scope"><el-input v-model.number="scope.row.annualWorkloadMas" type="number" :disabled="!editable" placeholder="mAs/a" /></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="120"><template #header>居留因子 T <HelpMark anchor-id="radiology.occupancy-factor" /></template><template #default="scope"><el-input v-model.number="scope.row.occupancyFactor" type="number" :disabled="!editable" /></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="120"><template #header>使用因子 U <HelpMark anchor-id="radiology.use-factor" /></template><template #default="scope"><el-input v-model.number="scope.row.useFactor" type="number" :disabled="!editable" /></template></el-table-column>
                <el-table-column prop="averageDisplay" label="平均值" width="100" />
                <el-table-column label="结果" width="130"><template #default="scope">{{ scope.row.resultDisplay || '-' }} μSv/h</template></el-table-column>
                <el-table-column v-if="modality === 'DR'" label="年有效剂量" width="150"><template #default="scope">{{ scope.row.annualEffectiveDose ?? '-' }} mSv/a</template></el-table-column>
                <el-table-column label="结论" width="110"><template #default="scope">{{ judgementText(scope.row.judgement) }}</template></el-table-column>
                <el-table-column label="操作" width="80"><template #default="scope"><el-button link type="danger" :disabled="!editable" @click="removePoint(scope.row, scope.$index)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="附件与点位图" name="attachments">
              <div class="upload-zone">
                <el-select v-model="attachmentCategory" style="width: 160px" :disabled="!editable">
                  <el-option label="检测点位图" value="POINT_MAP" />
                  <el-option label="仪器证书" value="CERTIFICATE" />
                  <el-option label="模体说明书" value="PHANTOM_MANUAL" />
                  <el-option label="其他附件" value="OTHER" />
                </el-select>
                <input type="file" :disabled="!editable" accept="image/png,image/jpeg,image/webp,application/pdf,.docx,.xlsx" @change="uploadAttachment" />
                <span>点位图支持 PNG/JPEG/WebP；其他附件支持 PDF、DOCX、XLSX，单文件不超过 25MB。 <HelpMark anchor-id="radiology.phantom" /></span>
              </div>
              <el-table :data="selectedCase.attachments" border>
                <el-table-column prop="category" label="类别" width="130" />
                <el-table-column prop="originalName" label="文件名" min-width="240" />
                <el-table-column label="大小" width="100"><template #default="scope">{{ Math.ceil(scope.row.fileSize / 1024) }} KB</template></el-table-column>
                <el-table-column prop="sha256" label="SHA-256" min-width="220" show-overflow-tooltip />
                <el-table-column prop="uploadedAt" label="上传时间" width="180" />
                <el-table-column label="操作" width="150"><template #default="scope"><el-button link @click="downloadBlob(`/api/radiology/attachments/${scope.row.id}/download`, scope.row.originalName)">下载</el-button><label v-if="editable" class="replace-attachment">更换<input type="file" accept="image/png,image/jpeg,image/webp,.pdf,.docx,.xlsx" @change="replaceAttachment(scope.row.id,$event)" /></label><el-button link type="danger" :disabled="!editable" @click="removeAttachment(scope.row)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="审核与报告" name="report">
              <div class="section-toolbar"><el-button type="primary" @click="previewReport">逐页预览报告</el-button><el-button :disabled="!editable" @click="generateNonconformities">生成不符合项</el-button></div>
              <el-input v-model="profileDraft.overallConclusion" :disabled="!editable" type="textarea" :rows="4" placeholder="根据检测范围撰写总体评价；留空时按质控与防护分别生成评价" />
              <el-button :disabled="!editable" @click="saveReportProfile">保存总体评价</el-button>
              <div class="approval-grid">
                <div><span>编写人</span><strong>{{ selectedCase.writerName || '-' }}</strong></div>
                <div><span>审核人 <HelpMark anchor-id="radiology.review" /></span><strong>{{ selectedCase.reviewerName || '-' }}</strong><p>{{ selectedCase.reviewComment || '' }}</p></div>
                <div><span>批准人 <HelpMark anchor-id="radiology.approve" /></span><strong>{{ selectedCase.approverName || '-' }}</strong><p>{{ selectedCase.approvalComment || '' }}</p></div>
              </div>
              <div class="report-rule-heading">报告生成规则 <HelpMark anchor-id="radiology.report" /></div>
              <el-alert type="info" :closable="false" description="仅已批准案例可生成报告；每次生成均固化案例、测量值、点位、附件哈希和审批信息快照，历史报告不会覆盖。" />
              <el-table :data="selectedCase.workflowHistory || []" border style="margin:16px 0">
                <el-table-column prop="at" label="时间" min-width="180" />
                <el-table-column label="操作" width="100"><template #default="scope">{{ ({SUBMIT:'提交',REVIEW:'复核通过',APPROVE:'批准',RETURN:'退回'} as Record<string,string>)[scope.row.action] || scope.row.action }}</template></el-table-column>
                <el-table-column prop="operator" label="操作人" width="120" />
                <el-table-column prop="comment" label="意见" min-width="200" />
                <el-table-column prop="dataVersion" label="数据版本" width="100" />
              </el-table>
              <el-table :data="selectedCase.reports" border class="report-table">
                <el-table-column prop="reportNo" label="报告编号" min-width="180" />
                <el-table-column prop="fileName" label="文件名" min-width="260" />
                <el-table-column prop="generatedBy" label="生成人" width="110" />
                <el-table-column prop="generatedAt" label="生成时间" width="180" />
                <el-table-column prop="sha256" label="SHA-256" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="100"><template #default="scope"><el-button link @click="downloadBlob(`/api/radiology/reports/${scope.row.id}/download`, scope.row.fileName)">下载</el-button><el-button v-if="canGenerateReport" link type="danger" @click="deleteReport(scope.row.id)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane v-if="props.canManageRules" label="判定规则" name="rules">
              <div class="section-toolbar"><div><h4>版本化判定规则</h4><p>需求表未给出的限值不作猜测；在此按标准版本配置后自动判定。</p></div><el-button type="primary" @click="openCreateRule">新增规则</el-button></div>
              <el-table :data="rules" border>
                <el-table-column prop="itemCode" label="项目" width="90" />
                <el-table-column prop="itemName" label="名称" min-width="160" />
                <el-table-column prop="ruleVersion" label="规则版本" width="150" />
                <el-table-column label="限值/文本" min-width="170"><template #default="scope">{{ scope.row.lowerLimit !== undefined ? `≥${scope.row.lowerLimit}` : '' }} {{ scope.row.upperLimit !== undefined ? `≤${scope.row.upperLimit}` : '' }} {{ scope.row.expectedText ? `应为“${scope.row.expectedText}”` : '' }} {{ scope.row.unit }}</template></el-table-column>
                <el-table-column prop="standardCode" label="标准" width="150" />
                <el-table-column label="启用" width="80"><template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template></el-table-column>
                <el-table-column label="操作" width="140"><template #default="scope"><el-button link @click="openEditRule(scope.row)">编辑</el-button><el-button link type="danger" @click="removeRule(scope.row)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </main>

      <aside v-if="selectedCase" class="qc-status-rail">
        <section>
          <h3>检测完整性 <HelpMark anchor-id="radiology.calculation" /></h3>
          <div class="completion-value">{{ completenessText }}</div>
          <p>{{ completenessHint }}</p>
        </section>
        <section>
          <h3>责任与审批 <HelpMark anchor-id="radiology.review" /></h3>
          <dl>
            <div><dt>编写人</dt><dd>{{ selectedCase.writerName || '待指定' }}</dd></div>
            <div><dt>审核人</dt><dd>{{ selectedCase.reviewerName || '待指定' }}</dd></div>
            <div><dt>批准人</dt><dd>{{ selectedCase.approverName || '待指定' }}</dd></div>
            <div><dt>当前状态</dt><dd>{{ statusText(selectedCase.status) }}</dd></div>
          </dl>
        </section>
        <section class="qc-next-action">
          <h3>下一步操作 <HelpMark anchor-id="workflow.stages" /></h3>
          <p>{{ nextQcAction }}</p>
          <p v-if="editabilityReason" class="action-constraint"><strong>当前限制：</strong>{{ editabilityReason }} <HelpMark anchor-id="form.disabled-reason" /></p>
          <el-button v-if="editable" type="primary" @click="saveMeasurements">保存当前数据</el-button>
          <el-button v-if="selectedCase.status === 'CALCULATED'" :disabled="!!selectedCase.submissionIssues?.length" type="warning" @click="workflow('submit')">提交审核</el-button>
          <el-button v-if="['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" type="success" @click="generateReport">生成检测报告</el-button>
        </section>
      </aside>
    </div>

    <el-dialog
      v-model="caseDialogVisible"
      class="case-step-dialog"
      :title="editingCaseId ? '编辑检测案例' : `新建 ${modality} 检测案例`"
      width="980px"
      :before-close="beforeCloseCaseDialog"
      destroy-on-close
    >
      <div class="case-step-header">
        <el-steps :active="caseDialogStep" finish-status="success" align-center>
          <el-step v-for="title in caseStepTitles" :key="title" :title="title" />
        </el-steps>
        <div class="case-draft-state" :class="{ pending: caseFormDirty }">
          <span aria-hidden="true"></span>{{ caseDraftStatus }}
          <HelpMark anchor-id="form.autosave" />
        </div>
      </div>

      <el-alert v-if="caseValidationMessage" class="case-validation-alert" type="warning" :closable="false" :title="caseValidationMessage" show-icon />

      <el-form label-position="top" class="case-step-form">
        <section v-if="caseDialogStep === 0" class="case-form-section">
          <div class="case-section-title"><div><span>第 1 步</span><h3>受检单位与案例信息</h3></div><HelpMark anchor-id="radiology.case-steps" /></div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="案例编号 *"><el-input v-model="caseForm.caseCode" placeholder="例如 CT-20260828-01" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="受理编号 *"><el-input v-model="caseForm.sampleAcceptanceNo" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="检测类别 *"><el-select v-model="caseForm.detectionCategory" style="width:100%"><el-option label="验收检测" value="验收检测" /><el-option label="状态检测" value="状态检测" /><el-option label="稳定性检测" value="稳定性检测" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :md="12"><el-form-item label="受检单位 *"><el-input v-model="caseForm.inspectedUnitName" placeholder="填写单位完整名称" /></el-form-item></el-col>
            <el-col :xs="24" :md="12"><el-form-item label="单位地址"><el-input v-model="caseForm.inspectedUnitAddress" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="样品名称"><el-input v-model="caseForm.profile.sampleName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="委托单位"><el-input v-model="caseForm.profile.commissionedUnit" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="送检单位"><el-input v-model="caseForm.profile.submittingUnit" /></el-form-item></el-col>
            <el-col :xs="24" :sm="6"><el-form-item label="所在地市"><el-select v-model="caseForm.city" filterable allow-create><el-option v-for="r in cityOptions" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="6"><el-form-item label="所在县区"><el-select v-model="caseForm.county" filterable allow-create><el-option v-for="r in regionsLibrary.filter(r=>r.level>=3)" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="本次检测范围"><el-checkbox-group v-model="caseForm.profile.selectedGroups"><template v-if="modality==='DR'"><el-checkbox value="GENERAL">通用质控</el-checkbox><el-checkbox value="SPECIAL">DR专用质控</el-checkbox></template><el-checkbox v-else value="QC">CT质量控制</el-checkbox><el-checkbox value="PROTECTION">场所防护</el-checkbox></el-checkbox-group></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="联系人"><el-input v-model="caseForm.contactName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="联系电话"><el-input v-model="caseForm.contactPhone" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="检测地点"><el-input v-model="caseForm.detectionLocation" /></el-form-item></el-col>
          </el-row>
        </section>

        <section v-else-if="caseDialogStep === 1" class="case-form-section">
          <div class="case-section-title"><div><span>第 2 步</span><h3>设备与模体信息</h3></div><HelpMark anchor-id="radiology.equipment" /></div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12"><el-form-item><template #label>设备名称 * <HelpMark anchor-id="radiology.equipment" /></template><el-input v-model="caseForm.equipmentName" placeholder="填写设备铭牌名称" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="生产厂家"><el-input v-model="caseForm.manufacturer" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="设备型号"><el-input v-model="caseForm.equipmentModel" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="设备编号"><el-input v-model="caseForm.equipmentNo" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="设备参数"><el-input v-model="caseForm.profile.equipmentParameters" type="textarea" /></el-form-item></el-col>
            <el-col v-if="modality==='CT'" :span="12"><el-form-item label="出厂日期"><el-date-picker v-model="caseForm.profile.manufacturedDate" value-format="YYYY-MM-DD" /></el-form-item></el-col>
            <template v-if="modality === 'CT'">
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体名称 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomName" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体厂家 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomManufacturer" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体型号 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomModel" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="模体编号"><el-input v-model="caseForm.phantomSerialNo" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item><template #label>模体说明书/依据 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomManualReference" placeholder="填写厂家、型号、说明书名称或版本" /></el-form-item></el-col>
            </template>
          </el-row>
        </section>

        <section v-else-if="caseDialogStep === 2" class="case-form-section">
          <div class="case-section-title"><div><span>第 3 步</span><h3>检测与人员信息</h3></div><HelpMark anchor-id="radiology.instrument" /></div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12"><el-form-item label="检测开始日期 *"><el-date-picker v-model="caseForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="检测结束日期 *"><el-date-picker v-model="caseForm.inspectionEndDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="报告日期"><el-date-picker v-model="caseForm.reportDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="检测机构名称"><el-input v-model="caseForm.agencyName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="地区"><el-input v-model="caseForm.region" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="防护原始读数单位"><el-select v-model="caseForm.rpdUnit" style="width:100%"><el-option v-for="unit in ['nSv/h','μSv/h','mSv/h']" :key="unit" :value="unit" :label="unit" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="校准因子使用"><el-select v-model="caseForm.profile.calibrationMode" style="width:100%"><el-option label="结果无需修正" value="NONE" /><el-option label="需要仪器刻度修正，无需时间响应修正" value="INSTRUMENT" /><el-option label="需要仪器刻度、时间响应同步修正" value="BOTH" /><el-option label="需要时间响应修正，无需仪器刻度修正" value="TIME" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="场所防护数据处理"><el-select v-model="caseForm.protectionMode" style="width:100%"><el-option label="不扣本底" value="UNSUBTRACTED" /><el-option label="扣本底" value="SUBTRACTED" /></el-select></el-form-item></el-col>
            <el-col :span="24" v-if="modality === 'DR'"><el-checkbox v-model="caseForm.hasAec">设备具备AEC</el-checkbox><el-checkbox v-model="caseForm.hasRawImages">可获取未处理图像</el-checkbox><el-form-item v-if="!caseForm.hasAec || !caseForm.hasRawImages" label="设备功能与不检测理由"><el-input v-model="caseForm.featureReason" type="textarea" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="从标准库导入"><el-select v-model="selectedStandardIds" multiple filterable style="width:80%"><el-option v-for="s in standardsLibrary" :key="s.id" :value="s.id" :label="s.standardCode+' '+s.standardName" /></el-select><el-button @click="importStandards">导入依据</el-button></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="从检测设备库选择"><el-select v-model="selectedInstrumentId" filterable style="width:80%"><el-option v-for="d in instrumentsLibrary" :key="d.id" :value="d.id" :label="d.deviceName+' '+(d.deviceNo || '')" /></el-select><el-button @click="addInstrument">添加仪器</el-button></el-form-item></el-col>
            <el-col :span="24"><el-table :data="caseForm.profile.instruments" border><el-table-column label="仪器" min-width="150"><template #default="s">{{s.row.name}} {{s.row.model}} / {{s.row.deviceNo}}</template></el-table-column><el-table-column label="证书编号" min-width="130"><template #default="s"><el-input v-model="s.row.certificateNo" /></template></el-table-column><el-table-column label="有效期起" min-width="150"><template #default="s"><el-date-picker v-model="s.row.validFrom" value-format="YYYY-MM-DD" style="width:140px" /></template></el-table-column><el-table-column label="有效期止" min-width="150"><template #default="s"><el-date-picker v-model="s.row.validTo" value-format="YYYY-MM-DD" style="width:140px" /></template></el-table-column><el-table-column width="70"><template #default="s"><el-button link @click="caseForm.profile.instruments?.splice(s.$index,1)">移除</el-button></template></el-table-column></el-table></el-col>
            <el-col :span="12"><el-form-item label="检测人"><el-select v-model="caseForm.profile.detectorName" filterable allow-create default-first-option><el-option v-for="s in staffLibrary" :key="s.id" :value="s.staffName || s.name || String(s.id)" :label="s.staffName || s.name || String(s.id)" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item><template #label>检测依据 <HelpMark anchor-id="basic.reference" /></template><el-input v-model="caseForm.standardBasis" type="textarea" :rows="2" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item><template #label>检测仪器及证书 <HelpMark anchor-id="radiology.instrument" /></template><el-input v-model="caseForm.instrumentDescription" type="textarea" :rows="2" placeholder="填写仪器名称、型号、编号和证书有效期" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item label="编写人"><el-input v-model="caseForm.writerName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item><template #label>预设审核人 <HelpMark anchor-id="radiology.review" /></template><el-input v-model="caseForm.reviewerName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item><template #label>预设批准人 <HelpMark anchor-id="radiology.approve" /></template><el-input v-model="caseForm.approverName" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="caseForm.notes" type="textarea" :rows="2" /></el-form-item></el-col>
          </el-row>
        </section>

        <section v-else class="case-form-section case-review-section">
          <div class="case-section-title"><div><span>第 4 步</span><h3>{{ editingCaseId ? '确认修改内容' : '确认并创建案例' }}</h3></div><HelpMark anchor-id="radiology.case-steps" /></div>
          <el-alert type="info" :closable="false" title="请核对关键字段。创建后仍可在草稿、已录入或已计算状态下修改案例信息。" show-icon />
          <dl class="case-review-grid">
            <div><dt>案例编号</dt><dd>{{ caseForm.caseCode || '未填写' }}</dd></div>
            <div><dt>受理编号</dt><dd>{{ caseForm.sampleAcceptanceNo || '未填写' }}</dd></div>
            <div><dt>检测类别</dt><dd>{{ caseForm.detectionCategory || '未填写' }}</dd></div>
            <div><dt>受检单位</dt><dd>{{ caseForm.inspectedUnitName || '未填写' }}</dd></div>
            <div><dt>检测地点</dt><dd>{{ caseForm.detectionLocation || '未填写' }}</dd></div>
            <div><dt>设备</dt><dd>{{ [caseForm.manufacturer, caseForm.equipmentName, caseForm.equipmentModel].filter(Boolean).join(' / ') || '未填写' }}</dd></div>
            <div v-if="modality === 'CT'"><dt>检测模体</dt><dd>{{ [caseForm.phantomManufacturer, caseForm.phantomName, caseForm.phantomModel].filter(Boolean).join(' / ') || '未填写' }}</dd></div>
            <div><dt>检测起止日期</dt><dd>{{ caseForm.inspectionDate || '未填写' }} 至 {{ caseForm.inspectionEndDate || '未填写' }}</dd></div>
            <div><dt>所在地</dt><dd>{{ [caseForm.city,caseForm.county].filter(Boolean).join(' / ') || '未填写' }}</dd></div>
            <div><dt>本次检测范围</dt><dd>{{ (caseForm.profile.selectedGroups || []).map(detectionGroupLabel).join('、') }}</dd></div>
            <div><dt>编写 / 审核 / 批准</dt><dd>{{ [caseForm.writerName, caseForm.reviewerName, caseForm.approverName].map(value => value || '待指定').join(' / ') }}</dd></div>
            <div class="wide"><dt>检测依据</dt><dd>{{ caseForm.standardBasis || '未填写' }}</dd></div>
          </dl>
        </section>
      </el-form>

      <template #footer>
        <div class="case-dialog-footer">
          <div><el-button @click="requestCloseCaseDialog">取消</el-button><el-button v-if="!editingCaseId" plain @click="saveCaseDraftManually">保存草稿</el-button></div>
          <div>
            <el-button v-if="caseDialogStep > 0" @click="previousCaseStep">上一步</el-button>
            <el-button v-if="caseDialogStep < caseStepTitles.length - 1" type="primary" @click="nextCaseStep">下一步</el-button>
            <el-button v-else type="primary" @click="saveCase">{{ editingCaseId ? '保存修改' : '创建案例' }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="检测报告逐页预览" width="94%" top="3vh" destroy-on-close><iframe v-if="previewPdfUrl" :src="previewPdfUrl" title="检测报告分页预览" style="width:100%;height:80vh;border:0" /><div v-else ref="previewContainer" class="report-preview-pages"></div></el-dialog>
    <el-dialog v-model="queryVisible" class="case-query-dialog" title="综合查询" width="92%">
      <el-form label-position="top"><el-row :gutter="12">
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="检测年份"><el-input-number v-model="query.year" :min="1900" :max="2200" style="width:100%" /></el-form-item></el-col>
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="检测时间起"><el-date-picker v-model="query.from" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="检测时间止"><el-date-picker v-model="query.to" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="报告状态"><el-select v-model="query.status" clearable style="width:100%"><el-option v-for="s in ['DRAFT','DATA_ENTERED','CALCULATED','PENDING_REVIEW','REVIEWED','APPROVED','REPORTED']" :key="s" :value="s" :label="statusText(s as CaseStatus)" /></el-select></el-form-item></el-col>
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="所在地市"><el-select v-model="query.city" filterable allow-create clearable style="width:100%"><el-option v-for="r in cityOptions" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col><el-col :xs="24" :sm="12" :lg="6"><el-form-item label="所在县区"><el-select v-model="query.county" filterable allow-create clearable style="width:100%"><el-option v-for="r in regionsLibrary.filter(r=>r.level>=3)" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col>
        <el-col :xs="24" :sm="12" :lg="6"><el-form-item label="受理编号"><el-input v-model="query.receipt" clearable /></el-form-item></el-col><el-col :xs="24" :sm="12" :lg="6"><el-form-item label="受检单位"><el-input v-model="query.company" clearable /></el-form-item></el-col>
        <el-col :xs="24" :sm="12"><el-form-item label="设备类别"><el-select v-model="query.modalities" multiple clearable placeholder="不限（DR/CT）" style="width:100%"><el-option label="DR" value="DR" /><el-option label="CT" value="CT" /></el-select></el-form-item></el-col>
      </el-row></el-form><el-button type="primary" @click="query.page=0;loadCases()">查询</el-button><el-button @click="resetQuery">重置</el-button>
      <el-table :data="cases" border style="margin-top:16px"><el-table-column prop="sampleAcceptanceNo" label="受理编号" min-width="150" /><el-table-column prop="inspectedUnitName" label="受检单位" min-width="160" /><el-table-column prop="modality" label="设备类别" width="90" /><el-table-column prop="equipmentName" label="设备名称" min-width="150" /><el-table-column prop="equipmentNo" label="设备编号" min-width="120" /><el-table-column prop="inspectionDate" label="检测时间" width="120" /><el-table-column label="出具报告时间" min-width="170"><template #default="s">{{s.row.generatedAt?.replace("T"," ").slice(0,19) || "—"}}</template></el-table-column><el-table-column label="操作" width="150"><template #default="s"><el-button link @click="selectQueryCase(s.row)">查看</el-button><el-button v-if="['DRAFT','DATA_ENTERED','CALCULATED'].includes(s.row.status)" link @click="selectQueryCase(s.row,true)">修改</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="queryPage" :page-size="query.size" :total="totalCases" layout="prev,pager,next,total" @current-change="loadCases" />
    </el-dialog>
    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? '编辑判定规则' : '新增判定规则'" width="680px">
      <el-form label-position="top">
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="检测项目"><el-select v-model="ruleForm.itemCode" style="width:100%"><el-option v-for="item in catalog" :key="item.code" :label="`${item.code} / ${item.name}`" :value="item.code" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item><template #label>规则版本 <HelpMark anchor-id="radiology.calculation" /></template><el-input v-model="ruleForm.ruleVersion" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="适用检测类别"><el-select v-model="ruleForm.detectionCategory"><el-option v-for="category in ['验收检测','状态检测','稳定性检测']" :key="category" :label="category" :value="category" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-checkbox v-model="ruleForm.lowerInclusive">下限包含边界（≥）</el-checkbox></el-col>
          <el-col :span="12"><el-checkbox v-model="ruleForm.upperInclusive">上限包含边界（≤）</el-checkbox></el-col>
          <el-col :span="8"><el-form-item label="下限"><el-input-number v-model="ruleForm.lowerLimit" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="上限"><el-input-number v-model="ruleForm.upperLimit" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="单位"><el-input v-model="ruleForm.unit" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="期望文本（是/否类项目）"><el-input v-model="ruleForm.expectedText" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item><template #label>标准编号 <HelpMark anchor-id="basic.reference" /></template><el-input v-model="ruleForm.standardCode" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="标准名称"><el-input v-model="ruleForm.standardName" /></el-form-item></el-col>
          <el-col :span="24"><el-checkbox v-model="ruleForm.enabled">启用规则</el-checkbox></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="ruleDialogVisible = false">取消</el-button><el-button type="primary" @click="saveRule">保存规则</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.report-preview-pages { max-height:80vh;overflow:auto;background:#eceff3; }
.report-preview-pages :deep(.docx-wrapper) { padding:20px; }
.condition-subrows {display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin:10px 0;}
.protection-context {display:grid;grid-template-columns:2fr 1fr 1fr auto;gap:8px;margin:12px 0;}
.replace-attachment {position:relative;color:#409eff;cursor:pointer;margin:0 8px;}
.replace-attachment input {position:absolute;inset:0;opacity:0;width:100%;cursor:pointer;}

.radiology-workbench { color: #172033; min-width: 0; }
.qc-hero { display:flex; align-items:center; justify-content:space-between; gap:24px; padding:24px 28px; color:#fff; border-radius:16px; background:linear-gradient(115deg,#143a57 0%,#0b6b73 62%,#10928c 100%); box-shadow:0 16px 42px rgba(19,59,79,.2); }
.qc-hero h2 { margin:3px 0 7px; font-size:25px; letter-spacing:.04em; }
.qc-hero p { margin:0; color:rgba(255,255,255,.78); }
.eyebrow { font-size:11px; letter-spacing:.18em; font-weight:700; }
.comparison-banner { display:flex; flex-wrap:wrap; align-items:center; gap:16px; padding:14px 20px; background:#e9f3ef; border-bottom:1px solid #a7c6b8; color:#1c5140; }
.comparison-banner a { margin-left:auto; color:#176347; }
.submission-check { padding:14px 18px; margin:14px 0; background:#f8faf9; border:1px solid #d1ded7; border-radius:8px; }
.submission-check span { display:block; margin:6px 0; color:#597066; }
.submission-check summary { cursor:pointer; }
.item-exclusion { color:#55685d; padding:8px; background:#f1f4f2; }
.hero-actions,.workflow-actions,.section-toolbar,.import-strip,.upload-zone { display:flex; align-items:center; gap:10px; flex-wrap:wrap; }
.qc-layout { display:grid; grid-template-columns:300px minmax(0,1fr); gap:16px; margin-top:16px; }
.case-rail,.case-main { background:#fff; border:1px solid #e3e9ef; border-radius:14px; box-shadow:0 8px 28px rgba(29,55,72,.06); }
.case-rail { padding:14px; max-height:calc(100vh - 205px); overflow:auto; }
.rail-filter { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
.rail-filter .el-button { grid-column:1 / -1; }
.case-count { margin:12px 2px 8px; font-size:12px; color:#7c8796; }
.case-card { padding:13px; margin-bottom:9px; border:1px solid #e8edf2; border-radius:11px; cursor:pointer; transition:.16s ease; background:#fbfcfd; }
.case-card:hover { transform:translateY(-1px); border-color:#8ac0bf; }
.case-card.active { border-color:#007d72; background:#f0faf9; box-shadow:inset 3px 0 #007d72; }
.case-line,.case-foot,.heading-line { display:flex; align-items:center; justify-content:space-between; gap:8px; }
.case-card p { margin:9px 0 3px; font-size:13px; font-weight:600; }
.case-card span,.case-card small { color:#6b7685; font-size:12px; }
.case-foot { margin-top:9px; }
.rail-empty { text-align:center; color:#97a0ad; padding:40px 0; }
.case-main { min-width:0; padding:20px; }
.select-hint { text-align:center; padding:100px 20px; color:#697585; }
.select-hint h3 { color:#233044; margin:16px 0 8px; }
.hint-mark { width:76px; height:76px; margin:auto; display:grid; place-items:center; border-radius:24px; color:#0a7774; font-size:24px; font-weight:800; background:#e6f5f4; }
.case-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:20px; padding-bottom:16px; border-bottom:1px solid #e7ecf0; }
.case-heading h3 { margin:0; font-size:21px; }
.case-heading p { margin:8px 0 0; color:#6c7785; }
.heading-line { justify-content:flex-start; }
.version-pill { padding:3px 8px; border-radius:10px; background:#edf1f5; color:#546171; font-size:12px; }
.workflow-actions { justify-content:flex-end; }
.qc-tabs { margin-top:10px; }
.info-grid,.approval-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
.info-grid div,.approval-grid div { padding:16px; border-radius:10px; background:#f6f8fa; border:1px solid #ebeff3; }
.info-grid > div > span,.approval-grid > div > span,.text-block > span { display:block; color:#7b8794; font-size:12px; margin-bottom:7px; }
.text-block { margin-top:14px; padding:16px; border:1px solid #d6e1e3; background:#f7fafb; }
.text-block p { margin:0; line-height:1.7; }
.import-strip,.upload-zone { padding:14px; margin-bottom:12px; border:1px dashed #b8ccd2; border-radius:10px; background:#f6fafb; }
.import-strip > div { margin-right:auto; }
.import-strip p,.section-toolbar p { margin:4px 0 0; font-size:12px; color:#71808d; }
.item-title { width:100%; display:flex; align-items:center; gap:10px; }
.item-title small { color:#8792a0; margin-left:auto; }
.item-code { display:inline-grid; place-items:center; min-width:50px; padding:3px 6px; color:#0b7774; background:#e6f5f4; border-radius:6px; font-weight:800; }
.field-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:11px; padding:6px 4px 14px; }
.field-card { min-width:0; }
.field-card > span { display:block; min-height:32px; color:#596778; font-size:12px; line-height:1.35; }
.field-card em { color:#dd5c48; font-style:normal; }
.computed-panel { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:8px; padding:12px; border-radius:9px; background:#fff8eb; }
.computed-value { padding:9px 10px; border:1px solid #ead9bb; background:#fff; }
.computed-value span { display:block; color:#80705b; font-size:11px; }
.computed-value strong { display:block; margin-top:4px; }
.computed-value.final { border-left-color:#e4c220; background:#fffdf1; }
.calculation-summary { margin-top:20px; }
.section-toolbar { justify-content:flex-end; margin-bottom:12px; }
.section-toolbar > div { margin-right:auto; }
.section-toolbar h4 { margin:0; }
.approval-grid { margin-bottom:16px; }
.approval-grid p { margin:6px 0 0; color:#657281; font-size:12px; }
.report-rule-heading { display:flex; margin:0 0 8px; color:#29434e; font-size:13px; font-weight:700; align-items:center; gap:3px; }
.report-table { margin-top:16px; }
.dialog-form { max-height:65vh; overflow:auto; padding-right:8px; }
.case-step-header { padding:2px 12px 14px; border-bottom:1px solid #dbe4e6; }
.case-draft-state { display:flex; min-height:24px; margin:12px 0 0; color:#536a73; font-size:12px; align-items:center; justify-content:center; gap:6px; }
.case-draft-state > span { width:7px; height:7px; border-radius:50%; background:#14877d; }
.case-draft-state.pending > span { background:#b7791f; }
.case-validation-alert { margin:14px 0 0; }
.case-step-form { min-height:390px; padding:18px 16px 4px; }
.case-form-section { max-width:900px; margin:0 auto; }
.case-section-title { display:flex; margin-bottom:16px; padding-bottom:12px; border-bottom:1px solid #e1e8ea; align-items:flex-end; justify-content:space-between; }
.case-section-title span { display:block; margin-bottom:3px; color:#60767f; font-size:11px; }
.case-section-title h3 { margin:0; color:#193642; font-size:17px; }
.case-dialog-footer { display:flex; width:100%; align-items:center; justify-content:space-between; gap:16px; }
.case-dialog-footer > div { display:flex; align-items:center; gap:8px; }
.case-review-section .el-alert { margin-bottom:16px; }
.case-review-grid { display:grid; margin:0; grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
.case-review-grid > div { min-width:0; padding:12px 14px; border:1px solid #dce5e7; border-radius:6px; background:#f7f9f9; }
.case-review-grid > div.wide { grid-column:1 / -1; }
.case-review-grid dt { margin-bottom:5px; color:#60747d; font-size:12px; }
.case-review-grid dd { margin:0; overflow-wrap:anywhere; color:#1e3944; font-weight:650; line-height:1.55; }
.action-constraint { margin-top:10px !important; padding:9px 10px; border:1px solid #decba4; border-radius:5px; color:#73571d !important; background:#fff9ed; }
:global(.case-step-dialog .el-dialog__body) { padding:10px 20px 0; }
:global(.case-step-dialog .el-dialog__footer) { position:sticky; z-index:2; bottom:0; padding:14px 20px; border-top:1px solid #d7e1e3; background:#fff; }
:global(.case-step-dialog .el-dialog__headerbtn) { color:#294b57; background:#fff; }
:global(.case-step-dialog .el-dialog__headerbtn .el-dialog__close) { color:currentColor; stroke-width:2.4; }
@media (max-width:1200px) { .qc-layout{grid-template-columns:250px minmax(0,1fr)} .field-grid,.computed-panel{grid-template-columns:repeat(3,minmax(0,1fr))} .case-heading{display:block}.workflow-actions{margin-top:14px;justify-content:flex-start} }
@media (max-width:850px) { .qc-hero,.case-heading{align-items:flex-start;flex-direction:column}.qc-layout{grid-template-columns:1fr}.case-rail{max-height:360px}.field-grid,.computed-panel,.info-grid,.approval-grid{grid-template-columns:1fr 1fr} }

/* 样品流转台视觉系统 */
.radiology-workbench { color:#172a33; }
.qc-hero { min-height:72px; padding:13px 17px; border:1px solid #ccd7da; border-radius:10px; color:#172a33; background:#fff; box-shadow:0 5px 18px rgba(18,46,58,.06); }
.qc-hero > div:first-child { display:flex; align-items:center; gap:13px; }
.qc-hero h2 { margin:0 0 5px; font-size:19px; letter-spacing:0; }
.qc-hero p { margin:0; color:#60747d; font-size:12px; }
.modality-mark { display:grid; flex:0 0 48px; width:48px; height:42px; border-radius:7px; color:#fff; background:#0d4055; place-items:center; font-size:16px; font-weight:750; }
.qc-flow { display:grid; position:relative; margin-top:12px; padding:9px; border:1px solid #ccd7da; border-radius:10px; background:#fff; box-shadow:0 5px 18px rgba(18,46,58,.06); grid-template-columns:repeat(5,minmax(116px,1fr)); }
.qc-flow > div { display:flex; min-height:48px; padding:8px 13px; border-right:1px solid #d8e0e2; color:#62757e; align-items:center; gap:9px; }
.qc-flow > div:nth-child(5) { border-right:0; }
.qc-flow .qc-flow-help { position:absolute; z-index:2; top:4px; right:4px; }
.qc-flow > div > span { color:#82939a; font-size:11px; }
.qc-flow strong { font-size:13px; }
.qc-flow .done { color:#006b63; background:#f0f8f6; }
.qc-flow .active { color:#fff; background:#007d72; box-shadow:0 6px 16px rgba(0,113,103,.18); }
.qc-flow .active > span { color:#c4ebe7; }
.qc-layout { grid-template-columns:268px minmax(0,1fr) 246px; gap:12px; margin-top:12px; align-items:start; }
.case-rail,.case-main,.qc-status-rail { border:1px solid #ccd7da; border-radius:10px; background:#fff; box-shadow:0 5px 18px rgba(18,46,58,.06); }
.case-rail { position:sticky; top:94px; max-height:calc(100vh - 112px); padding:12px; }
.case-main { min-width:0; padding:16px; }
.case-card { margin-bottom:7px; padding:11px; border-color:#d7e0e2; border-radius:7px; background:#f8fafa; transition:border-color 180ms ease-out,background-color 180ms ease-out,box-shadow 180ms ease-out; }
.case-card:hover { transform:none; border-color:#78a9a4; background:#f2f8f7; }
.case-card:focus-visible { outline:3px solid rgba(0,125,114,.26); outline-offset:2px; border-color:#007d72; }
.case-card.active { border-color:#007d72; background:#eaf6f4; box-shadow:inset 0 0 0 1px #007d72; }
.case-heading { padding-bottom:13px; border-color:#dce4e6; }
.case-heading h3 { color:#172a33; font-size:19px; }
.version-pill { border-radius:4px; color:#4e646d; background:#e9eef0; }
.qc-tabs { margin-top:8px; }
.info-grid,.approval-grid { gap:8px; }
.info-grid div,.approval-grid div { padding:12px; border-color:#dce4e6; border-radius:7px; background:#f5f8f8; }
.text-block { margin-top:10px; padding:13px; border:1px solid #d6e1e3; border-left-width:1px; border-radius:7px; background:#f7f9f9; }
.import-strip,.upload-zone { padding:12px; border-color:#9fb7bd; border-radius:7px; background:#f4f8f8; }
.item-code { min-width:48px; border-radius:4px; color:#006b63; background:#e2f3f0; }
.field-grid { grid-template-columns:repeat(3,minmax(0,1fr)); gap:10px 12px; }
.computed-panel { border:1px solid #e6cfaa; border-radius:7px; background:#fff8eb; }
.computed-value,.computed-value.final { border:1px solid #ead9bb; border-left-width:1px; border-radius:5px; background:#fff; }
.computed-value.final { background:#fffcef; }
.qc-status-rail { position:sticky; top:94px; overflow:hidden; }
.qc-status-rail section { padding:15px; border-bottom:1px solid #dce4e6; }
.qc-status-rail section:last-child { border-bottom:0; }
.qc-status-rail h3 { margin:0 0 11px; color:#263e48; font-size:14px; }
.completion-value { color:#007d72; font-size:27px; font-weight:750; letter-spacing:-.02em; }
.qc-status-rail section > p { margin:5px 0 0; color:#687b83; font-size:12px; line-height:1.6; }
.qc-status-rail dl { display:grid; margin:0; }
.qc-status-rail dl > div { display:grid; padding:8px 0; border-bottom:1px solid #e5ebed; grid-template-columns:68px minmax(0,1fr); gap:8px; }
.qc-status-rail dl > div:last-child { border-bottom:0; }
.qc-status-rail dt { color:#6d7f87; font-size:12px; }
.qc-status-rail dd { margin:0; overflow-wrap:anywhere; color:#263e48; font-size:12px; font-weight:650; }
.qc-next-action { background:#f1f8f7; }
.qc-next-action .el-button { width:100%; margin:12px 0 0; }
.qc-next-action .el-button + .el-button { margin-left:0; }
.select-hint { padding:90px 20px; }
.hint-mark { width:66px; height:58px; border-radius:8px; color:#fff; background:#0d4055; }

@media (max-width:1500px) {
  .qc-layout { grid-template-columns:240px minmax(0,1fr) 222px; }
  .field-grid,.computed-panel { grid-template-columns:repeat(3,minmax(0,1fr)); }
}
@media (max-width:1250px) {
  .qc-layout { grid-template-columns:238px minmax(0,1fr); }
  .qc-status-rail { position:static; grid-column:1 / -1; display:grid; grid-template-columns:repeat(3,1fr); }
  .qc-status-rail section { border-right:1px solid #dce4e6; border-bottom:0; }
  .qc-status-rail section:last-child { border-right:0; }
}
@media (max-width:850px) {
  .qc-hero { align-items:flex-start; flex-direction:column; }
  .qc-flow { overflow-x:auto; grid-template-columns:repeat(5,128px); }
  .qc-layout { grid-template-columns:1fr; }
  .case-rail { position:static; max-height:360px; }
  .qc-status-rail { grid-template-columns:1fr; }
  .qc-status-rail section { border-right:0; border-bottom:1px solid #dce4e6; }
  .field-grid,.computed-panel,.info-grid,.approval-grid { grid-template-columns:1fr 1fr; }
  .case-review-grid { grid-template-columns:1fr; }
  .case-review-grid > div.wide { grid-column:auto; }
  .case-dialog-footer { align-items:stretch; flex-direction:column; }
  .case-dialog-footer > div { justify-content:flex-end; }
}
</style>
