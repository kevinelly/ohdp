<script setup lang="ts">
import axios from 'axios'
import { renderAsync } from 'docx-preview'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import InspectionQuery from './InspectionQuery.vue'
import InspectionFlow from './InspectionFlow.vue'
import MeasurementTable from './MeasurementTable.vue'
import RadiologyCaseSummary from './RadiologyCaseSummary.vue'
import RadiologyScopeEditor from './RadiologyScopeEditor.vue'
import { scopeSelection, applicableQuality, adjacentQuality, qualityStatus, type ItemScope, type ScopeProfile } from './radiologyScopeSupport'
import EquipmentParameters from './EquipmentParameters.vue'
import { sameDraft, pptItemName, pptFieldName, attachmentCategoryName, ruleLimitText } from './radiologyDraftSupport'
import HelpMark from '../help/HelpMark.vue'
import RadiologyConditionEditor from './RadiologyConditionEditor.vue'
import RadiologyMeasurementRows from './RadiologyMeasurementRows.vue'

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
  itemScope?: ItemScope[]
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
interface ReportProfile extends ScopeProfile {
  sampleName?: string; submittingUnit?: string; commissionedUnit?: string; equipmentParameters?: string; manufacturedDate?: string;
  detectorName?: string; backgroundLocation?: string; backgroundOperation?: string; calibrationMode?: string; instrumentResponseTime?: string;
  protectionConditions?: string; overallConclusion?: string; selectedGroups?: string[]; instruments?: InstrumentRecord[];
}
const blankProfile = (modality: Modality): ReportProfile => ({ sampleName: '', submittingUnit: '', commissionedUnit: '', equipmentParameters: '',
  detectorName: '', backgroundLocation: '', backgroundOperation: '未曝光', calibrationMode: 'INSTRUMENT', protectionConditions: '', overallConclusion: '',
  selectedGroups: modality === 'DR' ? ['GENERAL','SPECIAL','PROTECTION'] : ['QC','PROTECTION'], instruments: [] })
function normalizedProfile(c:InspectionCase){const profile={...blankProfile(c.modality),...c.profile};if(!Array.isArray(profile.selectedGroups))profile.selectedGroups=blankProfile(c.modality).selectedGroups;return profile}
function detectionGroupLabel(group: string) {
  const labels: Record<string,string> = {GENERAL:'X射线摄影设备通用检测项目',SPECIAL:'数字X射线摄影（DR）设备专用检测项目',QC:'CT质量控制检测项目',PROTECTION:'放射工作场所卫生防护检测'}
  return labels[group] || group
}
function calibrationLabel(mode?: string) {
  const labels: Record<string,string> = {NONE:'无需修正',INSTRUMENT:'仅仪器刻度修正',TIME:'仅时间响应修正',BOTH:'仪器刻度与时间响应修正'}
  return labels[mode || ''] || '未登记'
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

const emit=defineEmits<{ 'select-modality':[Modality]; 'open-dose':[number] }>()
const pendingQueryId=ref<number>()
const pendingQueryEdit=ref(false)
const props = withDefaults(defineProps<{
  token: string
  openCaseId?: number
  openEdit?: boolean
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
type WorkbenchTab = 'base' | 'data' | 'points' | 'attachments' | 'report' | 'rules'
const workTab = ref<WorkbenchTab>('base')
const viewStage=ref(1), evaluationTab=ref('results'), showCompleteness=ref(false), activeQuality=ref('')
const inspectionRange=computed<[string,string]>({get:()=>[caseForm.inspectionDate||'',caseForm.inspectionEndDate||''] as [string,string],set:(v:[string,string])=>{caseForm.inspectionDate=v?.[0]||'';caseForm.inspectionEndDate=v?.[1]||''}})
function goStage(stage:number){viewStage.value=stage;if(stage===1)workTab.value='base';if(stage===2 && !['base','data','points','attachments'].includes(workTab.value))workTab.value='data'}
const query = reactive({ keyword: '', status: '' as '' | CaseStatus, page: 0, size: 20, year: undefined as number | undefined,
  from: '', to: '', city: '', county: '', receipt: '', company: '', modalities: [props.modality] as Modality[] })
const batchVisible=ref(false)
const batchFile=ref<File>()
const batchResult=ref<{dryRun:boolean;cases:number;appliedCases:number;rows:Array<{row:number;caseCode:string;status:string;message:string}>}>()
function selectBatchFile(event:Event) {batchFile.value=(event.target as HTMLInputElement).files?.[0];batchResult.value=undefined}
async function importBatch(dryRun:boolean) {
  if(!batchFile.value)return
  await run(async()=>{const form=new FormData();form.append('file',batchFile.value!);batchResult.value=(await api.post('/api/radiology/batch-import',form,{params:{dryRun}})).data;if(!dryRun)await loadCases()})
}
const queryVisible = ref(false)
const previewVisible = ref(false), previewSection=ref('cover'), previewComplete=ref(false), previewComment=ref('')
const previewPages=ref<string[]>([])
const previewDocx=ref<Blob>()
const previewContainer = ref<HTMLElement>()
async function renderPreviewDocx() {
  if(previewDocx.value && previewContainer.value) {
    previewContainer.value.innerHTML=''
    await renderAsync(previewDocx.value,previewContainer.value,undefined,{breakPages:true,renderHeaders:true,renderFooters:true,inWrapper:true,ignoreLastRenderedPageBreak:false})
  }
}
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
async function previewReport() {
  if(!selectedCase.value)return
  await run(async()=>{
    previewPages.value=[];previewDocx.value=undefined;previewSection.value='cover';previewComplete.value=false;previewComment.value=''
    const caps=await api.get('/api/radiology/preview-capabilities')
    if(caps.data.pdf) previewPages.value=(await api.get<string[]>(`/api/radiology/cases/${selectedCase.value!.id}/report-preview-pages`)).data
    else previewDocx.value=(await api.get(`/api/radiology/cases/${selectedCase.value!.id}/report-preview`,{responseType:'blob'})).data
    previewVisible.value=true
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
  if(!selectedCase.value)return false
  let saved=false
  await run(async()=>{
    const c=selectedCase.value!;const payload={...blankCaseForm(c.modality),...c,...featureDraft,notes:noteDraft.value,profile:JSON.parse(JSON.stringify(profileDraft)),expectedDataVersion:c.dataVersion}
    const {data}=await api.put<InspectionCase>(`/api/radiology/cases/${c.id}`,payload);selectedCase.value=data;syncSavedPointResults(c.protectionPoints);Object.assign(profileDraft,blankProfile(c.modality),JSON.parse(JSON.stringify(data.profile || {})));scopePreview.value=null;saved=true;await loadCases()
  },'总体评价和防护条件已保存，请重新计算后提交')
  return saved
}
async function generateNonconformities() {
  if(!selectedCase.value)return
  const {data}=await api.get(`/api/radiology/cases/${selectedCase.value.id}/suggested-conclusion`);profileDraft.overallConclusion=data.text
}

const featureDraft=reactive({hasAec:true,hasRawImages:true,featureReason:''})
const scopePreview=ref<ItemScope[]|null>(null),scopePreviewBusy=ref(false),scopePreviewError=ref('')
const entryStep=ref<'scope'|'input'>('scope')
let scopePreviewSequence=0
const scopeRows=computed(()=>scopePreview.value || selectedCase.value?.itemScope || [])
const scopeNames=computed(()=>Object.fromEntries(catalog.value.map(item=>[item.code,pptItemName(item.code,item.name)])))
const scopeDirty=computed(()=>{
 const current=selectedCase.value;if(!current)return false
 return !sameDraft(scopeSelection(profileDraft),scopeSelection(normalizedProfile(current))) || !sameDraft(featureDraft,{hasAec:current.hasAec!==false,hasRawImages:current.hasRawImages!==false,featureReason:current.featureReason || ''})
})
const scopeReady=computed(()=>!!selectedCase.value && !scopePreviewBusy.value && !scopePreviewError.value && !scopeDirty.value && profileDraft.scopeConfirmed!==false && !scopeRows.value.some(item=>item.selected&&item.pending))
const qualityScope=computed(()=>scopeRows.value.filter(item=>item.selected&&item.itemCode!=='RPD'))
const applicableItems=computed(()=>applicableQuality(scopeRows.value))
const currentQuality=computed(()=>applicableItems.value.some(item=>item.itemCode===activeQuality.value)?activeQuality.value:applicableItems.value[0]?.itemCode || '')
const qualityGroups=computed(()=>[...new Set(qualityScope.value.map(item=>item.group))])
const protectionSelected=computed(()=>scopeRows.value.some(item=>item.itemCode==='RPD'&&item.selected))
async function previewScope(){
 const current=selectedCase.value;if(!current)return
 const sequence=++scopePreviewSequence;scopePreviewBusy.value=true;scopePreviewError.value=''
 try{const {data}=await api.post<ItemScope[]>(`/api/radiology/cases/${current.id}/scope-preview`,{profile:JSON.parse(JSON.stringify(profileDraft)),detectionCategory:current.detectionCategory,...featureDraft});if(sequence===scopePreviewSequence&&selectedCase.value?.id===current.id)scopePreview.value=data}
 catch(e:any){if(sequence===scopePreviewSequence)scopePreviewError.value=e.response?.data?.message || '适用项目预览失败，请重试'}
 finally{if(sequence===scopePreviewSequence)scopePreviewBusy.value=false}
}
watch(()=>[scopeSelection(profileDraft),{...featureDraft}],()=>{if(selectedCase.value&&scopeDirty.value)void previewScope()},{deep:true})
function setScopeProfile(profile:ScopeProfile){Object.assign(profileDraft,profile);if(['ARP','ARS','AIC'].some(code=>profile.itemApplicability?.[code]===true))featureDraft.hasAec=true}
async function confirmScope(){
 if(!profileDraft.selectedGroups?.length){ElMessage.warning('请至少选择一类检测项目');return}
 if(scopeReady.value){entryStep.value='input';if(!applicableItems.value.length&&protectionSelected.value)workTab.value='points';return}
 const old=profileDraft.scopeConfirmed;profileDraft.scopeConfirmed=true
 const saved=await saveReportProfile()
 if(!saved || selectedCase.value?.profile?.scopeConfirmed!==true){profileDraft.scopeConfirmed=old;return}
 ++scopePreviewSequence;scopePreview.value=null;scopePreviewBusy.value=false;scopePreviewError.value=''
 entryStep.value='input'
 if(!applicableItems.value.length&&protectionSelected.value)workTab.value='points'
}
function scopeStatus(item:ItemScope){const saved=selectedCase.value?.measurements.some(v=>v.itemCode===item.itemCode&&v.fieldRole==='RAW'&&String(v.valueText??v.valueNum??'')!=='') || false;return qualityStatus(item,saved,!!selectedCase.value?.calculationFresh,calculationItems.value.some(v=>v.itemCode===item.itemCode&&v.complete))}
function previousQuality(){const previous=adjacentQuality(scopeRows.value,currentQuality.value,-1);if(previous)activeQuality.value=previous;else workTab.value='base'}
async function nextEntry(){
 if(viewStage.value===1){goStage(2);return}
 if(workTab.value==='base'){workTab.value='data';return}
 if(workTab.value==='data'){
  if(entryStep.value==='scope'){
   if(!scopeReady.value)await confirmScope()
   if(!scopeReady.value)return
   entryStep.value='input';if(!applicableItems.value.length)workTab.value=protectionSelected.value?'points':'attachments';return
  }
  if(!scopeReady.value){ElMessage.warning('请先保存并确认检测范围及适用条件');return}
  const next=adjacentQuality(scopeRows.value,currentQuality.value,1);if(next)activeQuality.value=next;else workTab.value=protectionSelected.value?'points':'attachments';return
 }
 if(workTab.value==='points'){workTab.value='attachments';return}
 goStage(3)
}
const noteDraft=ref('')
const conditionValues = reactive<Record<string,string>>({})
const measurementValues = reactive<Record<string, string | number>>({})
const measurementsPending=computed(()=>{
 const c=selectedCase.value;if(!c)return false
 const old=Object.fromEntries(c.measurements.filter(v=>v.fieldRole==='RAW'&&String(v.valueText??v.valueNum??'')!=='').map(v=>[`${v.itemCode}::${v.fieldCode}`,String(v.valueText??v.valueNum??'')]))
 const now=Object.fromEntries(Object.entries(measurementValues).filter(([,v])=>String(v)!=='').map(([k,v])=>[k,String(v)]))
 return !sameDraft(old,now)||!sameDraft(conditionValues,c.itemConditions||{})
})
function displayCalculated(v:Measurement){return measurementsPending.value||!selectedCase.value?.calculationFresh?'待重算':resultDisplay(v)}
const pointDrafts = ref<Array<ProtectionPoint & { readingsText: string }>>([])
const selectedPointRows=ref<ProtectionPoint[]>([])
const pointReadCount=computed(()=>Math.max(5,...pointDrafts.value.map(p=>p.readingsText.split(/[，,\s]+/).filter(Boolean).length)))
function pointRead(point:{readingsText:string},index:number){return point.readingsText.split(/[，,\s]+/)[index]||''}
function setPointRead(point:{readingsText:string},index:number,value:string){const values=point.readingsText.split(/[，,\s]+/);while(values.length<=index)values.push('');values[index]=value;point.readingsText=values.join(',')}
function removeSelectedPoints(){pointDrafts.value=pointDrafts.value.filter(p=>!selectedPointRows.value.includes(p));selectedPointRows.value=[]}

const caseDialogVisible = ref(false)
const editingCaseId = ref<number | null>(null)
const caseDialogStep = ref(0)
const caseFormScroll = ref<HTMLElement>()
watch(caseDialogStep,async()=>{await nextTick();caseFormScroll.value?.scrollTo({top:0})})
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
const pendingAttachment = ref<File>()
async function confirmLeave():Promise<boolean> {
  const c=selectedCase.value
  if(!c || !editable.value)return true
  const storedValues=Object.fromEntries(c.measurements.filter(v=>v.fieldRole==='RAW').map(v=>[`${v.itemCode}::${v.fieldCode}`,String(v.valueText ?? v.valueNum ?? '')]))
  const currentValues=Object.fromEntries(Object.entries(measurementValues).filter(([,v])=>String(v)!=='' ).map(([k,v])=>[k,String(v)]))
  for(const key of Object.keys(storedValues))if(storedValues[key]==='')delete storedValues[key]
  const pending=noteDraft.value!==(c.notes||'') || !sameDraft(featureDraft,{hasAec:c.hasAec!==false,hasRawImages:c.hasRawImages!==false,featureReason:c.featureReason||''}) || !sameDraft(currentValues,storedValues) || !sameDraft(conditionValues,c.itemConditions || {}) || !sameDraft(profileDraft,normalizedProfile(c)) || !sameDraft(pointDrafts.value,c.protectionPoints.map(p=>({...p,readingsText:p.readings.join(', ')}))) || (caseDialogVisible.value && caseFormDirty.value)
  if(!pending)return true
  try {await ElMessageBox.confirm('当前项目有未保存的修改。继续切换将放弃这些修改；如需保留，请取消并先保存。','未保存的内容',{confirmButtonText:'放弃修改并切换',cancelButtonText:'留在当前项目',type:'warning',closeOnClickModal:false});return true}catch{return false}
}
defineExpose({confirmLeave})
async function openQueryResult(row:{id:number;modality:'CT'|'DR'|'DOSE';edit?:boolean}) {
  if(row.modality==='DOSE'){if(!await confirmLeave())return;queryVisible.value=false;emit('open-dose',row.id)}
  else await selectQueryCase(row as CaseSummary,!!row.edit)
}
async function selectQueryCase(item: CaseSummary, edit=false) {
  if(!await confirmLeave())return
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
  hasAec: true, hasRawImages: true, featureReason: '', agencyName: '', region: '', inspectionEndDate: '', protectionMode: 'UNSUBTRACTED', rpdUnit: 'nSv/h', city:'',county:'',profile:{...blankProfile(modality),selectedGroups:[],scopeConfirmed:false,itemApplicability:{ARP:null,ARS:null,AIC:null,DDI:null,STP:null,RUE:null,ART:null},itemExclusionReasons:{}},
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
  if(profileDraft.scopeConfirmed===false || scopeRows.value.some(scope=>scope.selected&&scope.pending))return '待确认范围及适用条件'
  const relevant=scopeRows.value.filter(scope=>scope.selected&&scope.applicable&&!scope.pending)
  const results=calculationItems.value.filter(item=>relevant.some(scope=>scope.itemCode===item.itemCode))
  if (!results.length) return '待计算'
  return results.filter(r=>r.complete).length + ' / ' + relevant.length
})
const completenessHint = computed(() => selectedCase.value?.submissionIssues?.length
  ? '仍有 ' + selectedCase.value.submissionIssues.length + ' 项提交条件待补齐'
  : '已处理项目 / 本次适用项目总数（不开展项不计入）')
const nextQcAction = computed(() => {
  const status = selectedCase.value?.status
  if (!status) return `创建或选择一个 ${props.modality} 案例开始录入。`
  if(profileDraft.scopeConfirmed===false)return '先选择并确认本次检测范围与机型适用条件。'
  if (status === 'DRAFT') return '补全案例信息和原始检测数据，然后保存。'
  if (status === 'DATA_ENTERED') return '执行自动计算并逐项核对标准判定依据。'
  if (status === 'CALCULATED') return '确认完整性和异常项后提交审核。'
  if (status === 'PENDING_REVIEW') return '由审核人员复核原始值、计算结果和防护点位。'
  if (status === 'REVIEWED') return '由批准人员确认结论并完成批准。'
  return '生成或下载受控报告，必要时创建修订版。'
})
const caseStepTitles = ['受检单位信息', '受检设备信息', '检测机构信息', '确认并创建']
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

watch(() => [props.modality,props.openCaseId,props.openEdit] as const, async ([value]) => {
  selectedCase.value = null
  viewStage.value = 1
  workTab.value = 'base'
  evaluationTab.value = 'results'
  activeQuality.value = ''
  pendingAttachment.value = undefined
  calculation.value = null
  query.modalities = [value]
  Object.assign(caseForm, blankCaseForm(value))
  await Promise.all([loadCatalog(), loadCases(), loadRules(), loadLibraries()])
  if(props.openCaseId){await selectCase(props.openCaseId);if(props.openEdit&&editable.value)openEditCase()}
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
  pendingAttachment.value = undefined
  await run(async () => {
    const response = await api.get<InspectionCase>(`/api/radiology/cases/${id}`)
    selectedCase.value = response.data
    calculation.value = null
    syncCaseState()
    viewStage.value=qcFlowStage.value
  })
}

function syncSavedMeasurements() {
  for (const key of Object.keys(measurementValues)) delete measurementValues[key]
  const current = selectedCase.value
  for (const name of Object.keys(conditionValues)) delete conditionValues[name]
  Object.assign(conditionValues,current?.itemConditions || {})
  if (!current) return
  for (const value of current.measurements.filter(item => item.fieldRole === 'RAW')) {
    measurementValues[`${value.itemCode}::${value.fieldCode}`] = value.valueText ?? value.valueNum ?? ''
  }
}
function syncSavedPointResults(previousPoints:ProtectionPoint[]) {
  const asDraft=(points:ProtectionPoint[])=>points.map(point=>({...point,readingsText:point.readings.join(', ')}))
  if(sameDraft(pointDrafts.value,asDraft(previousPoints)))pointDrafts.value=asDraft(selectedCase.value?.protectionPoints || [])
}
function syncCaseState() {
  ++scopePreviewSequence;scopePreview.value=null;scopePreviewBusy.value=false;scopePreviewError.value=''
  entryStep.value='scope'
  syncSavedMeasurements()
  const current = selectedCase.value
  if (!current) return
  noteDraft.value=current.notes||''
  Object.assign(featureDraft,{hasAec:current.hasAec!==false,hasRawImages:current.hasRawImages!==false,featureReason:current.featureReason||''})
  Object.assign(profileDraft,blankProfile(current.modality),JSON.parse(JSON.stringify(current.profile || {})))
  if(!Array.isArray(profileDraft.selectedGroups))profileDraft.selectedGroups=blankProfile(current.modality).selectedGroups
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
    viewStage.value=2;workTab.value="base"
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

const repeatableItems=['TVD','ROA','ETD','HCR','LCR','RST']
function rowIds(code:string):number[] {return JSON.parse(conditionValues['__rows.'+code] || '[]')}
function editableFields(item:ItemDefinition):FieldDefinition[] {
  const ids=rowIds(item.code)
  if(!ids.length) return item.fields
  return ids.flatMap(id=>item.fields.filter(f=>!['TVD','ETD'].includes(item.code) || f.code.startsWith(item.code+'1-') || f.code===item.code+'-K1')
    .map(f=>({...f,code:`R${id}__${f.code}`})))
}
function ctdiParts():string[] {return (conditionValues['__ctdi.parts'] ?? (selectedCase.value?.detectionCategory?.includes('验收')?'HEAD,BODY':'HEAD')).split(',').filter(Boolean)}
function setCtdiParts(parts:Array<string|number|boolean>) {conditionValues['__ctdi.parts']=parts.join(',')}
interface EvaluationRow {itemName:string;condition:string;result:string;judgement:string;rule:string;message?:string}
const evaluationRows=computed<EvaluationRow[]>(()=>{
 const rows=calculationItems.value.flatMap(item=>{
  const raw=selectedCase.value?.measurements.find(m=>m.itemCode===item.itemCode&&m.fieldCode==='ROWS_JSON')?.valueText
  if(raw&&item.judgement!=='NOT_APPLICABLE')return (JSON.parse(raw) as Array<{condition:string;result:string;judgement:string;rule:string;message:string}>).map(r=>({...r,itemName:pptItemName(item.itemCode,item.itemName),...(item.judgement==='NOT_CONFIGURED'?{judgement:item.judgement,rule:item.ruleDescription}:{})}))
  const base={itemName:pptItemName(item.itemCode,item.itemName),condition:selectedCase.value?.itemConditions?.[item.itemCode]||'—',result:itemResultDisplay(item),judgement:item.judgement,rule:item.ruleDescription,message:item.message}
  const sub:Record<string,string[]>={TVD:['DISPLAY_1','DISPLAY_2','DISPLAY_3'],ETD:['DEVIATION_MS_1','DEVIATION_MS_2','DEVIATION_MS_3'],LRD:['RESULT_A1','RESULT_A2','RESULT_B1','RESULT_B2'],REA:['HORIZONTAL','VERTICAL'],TPA:['FORWARD','BACKWARD']}
  const codes=sub[item.itemCode];if(!codes||!item.complete||item.judgement==='NOT_APPLICABLE')return [base]
  const expanded=codes.flatMap((code,index)=>{const field=selectedCase.value?.measurements.find(m=>m.itemCode===item.itemCode&&m.fieldCode===code&&m.fieldRole!=='RAW');return field?[{...base,condition:selectedCase.value?.itemConditions?.[item.itemCode+'.'+(index+1)]||base.condition+' / '+(conditionRows(item.itemCode)[index]||'第'+(index+1)+'组'),result:resultDisplay(field)}]:[]})
  return expanded.length===codes.length?expanded:[base]
 })
 const pointScope=selectedCase.value?.itemScope?.find(scope=>scope.itemCode==='RPD')
 if(!pointScope || pointScope.selected&&pointScope.applicable)for(const point of selectedCase.value?.protectionPoints||[])rows.push({itemName:'防护检测',condition:[point.pointName,point.locationDescription].filter(Boolean).join(' / '),result:(point.resultDisplay??point.resultValue??'—')+' μSv/h',judgement:point.judgement||'INCOMPLETE',rule:point.controlLimit!=null?'≤'+point.controlLimit+' μSv/h':'—',message:''})
 return rows
})
function evaluationSpan({rowIndex,columnIndex}:{rowIndex:number;columnIndex:number}) {
  if(![0,3,4].includes(columnIndex))return [1,1]
  const field=columnIndex===0?'itemName':columnIndex===3?'rule':'judgement';const rows=evaluationRows.value;const row=rows[rowIndex]!
  const same=(other:EvaluationRow|undefined)=>other?.itemName===row.itemName&&other?.[field]===row[field]
  if(rowIndex>0&&same(rows[rowIndex-1]))return [0,0]
  let count=1;while(same(rows[rowIndex+count]))count++;return [count,1]
}
function evaluationText(j:string) {return j==='QUALIFIED'?'符合':j==='UNQUALIFIED'?'不符合':'—'}
function visibleFields(item: ItemDefinition) {
  return item.fields.filter(f=>{
    if(item.code==='LCD' && (f.code.startsWith('NUM') || f.code.startsWith('mm')))return selectedCase.value?.measurements.some(m=>m.itemCode==='LCD' && m.fieldCode===f.code && m.fieldRole==='RAW' && m.valueNum!=null)
    if(item.code==='HCR' && f.code==='厂家规定值/基线值')return selectedCase.value?.measurements.some(m=>m.itemCode==='HCR' && m.fieldCode===f.code && m.fieldRole==='RAW' && m.valueNum!=null)
    if(item.code==='RST' && ['WWmin','CThm'].includes(f.code))return false
    if(item.code==='CTDI') {const body=f.code.startsWith('S') || ['层数n2','层厚T2','刻度系数k2'].includes(f.code) || f.code.startsWith('CTDI-BODY');return ctdiParts().includes(body?'BODY':'HEAD')}
    if(item.code==='GTA' && conditionValues['__method.GTA']==='PHANTOM' && f.code==='测量值α2')return false
    if(item.code==='GTA' && conditionValues['__method.GTA']==='INCLINOMETER' && f.code.includes('边沿间距'))return false
    return true
  })
}
function conditionRows(code: string): string[] {return ({TVD:['第1组','第2组','第3组'],LRD:['左a1','右a2','前b1','后b2'],REA:['水平方向','垂直方向'],TPA:['前进（负载70kg）','后退（负载70kg）'],CTDI:['头部模体','体部模体']} as Record<string,string[]>)[code] || []}
function textOptions(code: string): string[] {
  if(code==='UBD-QUALIFIER')return ['>','<']
  if(code==='ART-SOURCE')return ['探测器','监视器']
  if(code==='LCR-BASELINE-UNIT')return ['个']
  if(code==='填“是”或“否”' || code.startsWith('是否有残影'))return ['是','否']
  return []
}
function isTextField(_itemCode: string, fieldCode: string) {
  fieldCode=fieldCode.replace(/^R[1-9]\d{0,2}__/, '')
  return fieldCode === '填“是”或“否”' || fieldCode.startsWith('是否有残影') || ['STP-MODEL','UBD-QUALIFIER','ART-SOURCE','LCR-BASELINE-UNIT'].includes(fieldCode)
}

function key(itemCode: string, fieldCode: string) {
  return `${itemCode}::${fieldCode}`
}

function computedFields(itemCode: string) {
  return (selectedCase.value?.measurements || []).filter(value => value.itemCode === itemCode && value.fieldRole !== 'RAW' && value.fieldCode!=='ROWS_JSON')
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
  if(item.resultText) return isProtectionRecord(item) && item.resultUnit ? `${item.resultText} ${item.resultUnit}` : item.resultText
  const result=selectedCase.value?.measurements.find(m=>m.itemCode===item.itemCode && m.fieldCode==='RESULT' && m.fieldRole==='FINAL')
  return result ? resultDisplay(result) : `${item.resultValue ?? '-'} ${item.resultUnit || ''}`.trim()
}
function isProtectionRecord(item: CalculationItem) {
  return item.itemCode==='RPD' && item.complete && item.ruleDescription==='场所防护按点位逐项判定'
}

async function saveMeasurements() {
  if (!selectedCase.value) return
  if(!scopeReady.value){ElMessage.warning('请先保存并确认检测范围及适用条件');return}
  const values: Array<{ itemCode: string; fieldCode: string; valueNum?: number | string | null; valueText?: string | null }> = []
  for (const item of catalog.value) {
    if(!scopeRows.value.some(scope=>scope.itemCode===item.code&&scope.applicable&&!scope.pending))continue
    for (const field of editableFields(item)) {
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
          ElMessage.warning(`${pptItemName(item.code,item.name)} / ${pptFieldName(item.code,field.code,field.label)} 不是有效数值`)
          return
        }
        values.push({ itemCode: item.code, fieldCode: field.code, valueNum: String(raw).trim() })
      }
    }
  }
  if (!values.length && !isAicExposurePair('AIC') && JSON.stringify(conditionValues)===JSON.stringify(selectedCase.value.itemConditions || {})) {
    ElMessage.warning('尚未填写测量数据')
    return
  }
  await run(async () => {
    const previousPoints=selectedCase.value!.protectionPoints
    const response = await api.put<InspectionCase>(`/api/radiology/cases/${selectedCase.value?.id}/measurements`, { values, conditions: conditionValues, expectedDataVersion: selectedCase.value?.dataVersion })
    selectedCase.value = response.data
    syncSavedMeasurements()
    syncSavedPointResults(previousPoints)
    await loadCases()
  }, `已保存 ${values.length} 个测量值`)
}

async function calculateCase() {
  if (!selectedCase.value) return
  if(!scopeReady.value){ElMessage.warning('请先保存并确认检测范围及适用条件');return}
  const pointsPending=()=>!sameDraft(pointDrafts.value,selectedCase.value!.protectionPoints.map(p=>({...p,readingsText:p.readings.join(', ')})))
  if(protectionSelected.value && pointsPending()){await saveProtectionPoints();if(pointsPending())return}
  if(noteDraft.value!==(selectedCase.value.notes||'') || !sameDraft(profileDraft,normalizedProfile(selectedCase.value)) || !sameDraft(featureDraft,{hasAec:selectedCase.value.hasAec!==false,hasRawImages:selectedCase.value.hasRawImages!==false,featureReason:selectedCase.value.featureReason||''})) {
    await saveReportProfile()
    if(!sameDraft(profileDraft,normalizedProfile(selectedCase.value)))return
  }
  const conditionsPending = JSON.stringify(conditionValues) !== JSON.stringify(selectedCase.value.itemConditions || {})
  const pending = conditionsPending || catalog.value.some(item => editableFields(item).some(field => {
    const current = measurementValues[key(item.code, field.code)] ?? ''
    const stored = selectedCase.value!.measurements.find(v => v.itemCode === item.code && v.fieldCode === field.code && v.fieldRole === 'RAW')
    return String(current) !== String(stored?.valueText ?? stored?.valueNum ?? '')
  }))
  if (pending) {
    await saveMeasurements()
    const stillPending = catalog.value.some(item => editableFields(item).some(field => {
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
    workTab.value = 'data';viewStage.value=3
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
    selectedCase.value=(await api.get<InspectionCase>(`/api/radiology/cases/${selectedCase.value!.id}`)).data
  }, '防护点位已保存并计算')
}



function chooseAttachment(event:Event){const input=event.target as HTMLInputElement;pendingAttachment.value=input.files?.[0];input.value=''}
async function uploadAttachment() {
  const file = pendingAttachment.value
  if (!file || !selectedCase.value) return
  await run(async () => {
    const form = new FormData()
    form.append('file', file)
    await api.post(`/api/radiology/cases/${selectedCase.value?.id}/attachments`, form, {
      params: { category: attachmentCategory.value }, headers: { 'Content-Type': 'multipart/form-data' },
    })
    await selectCase(selectedCase.value!.id)
    pendingAttachment.value = undefined
  }, '附件已上传')
}

async function removeAttachment(item: Attachment) {
  await ElMessageBox.confirm(`确认删除附件 ${item.originalName}？`, '删除确认', { type: 'warning' })
  await run(async () => {
    await api.delete(`/api/radiology/attachments/${item.id}`)
    await selectCase(selectedCase.value!.id)
  }, '附件已删除')
}

async function workflow(action: 'submit' | 'review' | 'approve' | 'return',providedComment?:string) {
  if (!selectedCase.value) return
  if(action==='submit'&&(measurementsPending.value||!sameDraft(profileDraft,normalizedProfile(selectedCase.value))||noteDraft.value!==(selectedCase.value.notes||'')||!sameDraft(featureDraft,{hasAec:selectedCase.value.hasAec!==false,hasRawImages:selectedCase.value.hasRawImages!==false,featureReason:selectedCase.value.featureReason||''})||!sameDraft(pointDrafts.value,selectedCase.value.protectionPoints.map(p=>({...p,readingsText:p.readings.join(', ')}))))){ElMessage.warning('请先保存修改并重新计算，再发送审核');return}
  const names = { submit: '提交审核', review: '审核通过', approve: '批准', return: '退回修改' }
  const input = providedComment===undefined?await ElMessageBox.prompt(`请输入${names[action]}意见（可留空）`, names[action], { inputType: 'textarea' }).catch(() => null):{value:providedComment}
  if (!input) return
  await run(async () => {
    const response = await api.post<InspectionCase>(`/api/radiology/cases/${selectedCase.value?.id}/workflow/${action}`, { comment: input.value, expectedDataVersion: selectedCase.value?.dataVersion })
    selectedCase.value = response.data
    syncCaseState()
    await loadCases()
    viewStage.value=action==='approve'?5:action==='return'?2:4
    if(providedComment!==undefined){previewVisible.value=false;previewComplete.value=false;previewComment.value=''}
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
    viewStage.value=2;workTab.value='base'
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
<header class="qc-hero">
      <div>

        <div>
          <h2>{{ modality }} 质量控制与场所防护<span v-if="selectedCase">--{{selectedCase.sampleAcceptanceNo}}</span></h2>

        </div>
      </div>
      <div class="hero-actions">
        <el-button @click="loadCases">刷新</el-button><el-button @click="downloadBlob('/api/radiology/batch-import-template','多案例批量模板.xlsx')">模板下载</el-button><el-button v-if="selectedCase" type="primary" @click="showCompleteness=!showCompleteness">检测完整性</el-button>
      </div>
    </header>

<InspectionFlow :model-value="viewStage" :selected="!!selectedCase" @update:model-value="goStage" />

    <div class="qc-layout" :class="{recording:selectedCase && viewStage>1}">
      <aside v-if="!selectedCase || viewStage===1" class="case-rail">
        <div class="rail-filter">
          <el-input v-model="query.keyword" clearable placeholder="受理编号" @keyup.enter="loadCases" />
          <el-select v-model="query.status" clearable placeholder="全部状态" @change="loadCases">
            <el-option v-for="status in ['DRAFT','DATA_ENTERED','CALCULATED','PENDING_REVIEW','REVIEWED','APPROVED','REPORTED']" :key="status" :label="statusText(status as CaseStatus)" :value="status" />
          </el-select>
          <el-button @click="loadCases">查询</el-button>
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
          <p>或</p><el-button type="primary" @click="openCreateCase">创建新的项目</el-button><el-button type="primary" @click="batchVisible=true">Excel批量导入</el-button>
        </div>

        <template v-else>
          <section class="case-heading" v-if="viewStage>=4">
            <div>
              <div class="heading-line">
                <h3>{{ selectedCase.caseCode }}</h3>
                <el-tag :type="statusType(selectedCase.status)">{{ statusText(selectedCase.status) }}</el-tag>
                <span class="version-pill">R{{ selectedCase.revisionNo }}</span>
              </div>
              <p>{{ selectedCase.inspectedUnitName }} · {{ selectedCase.equipmentName }} {{ selectedCase.equipmentModel || '' }}</p>
            </div>
            <div class="workflow-actions">



              <el-button v-if="viewStage===4 && selectedCase.status === 'CALCULATED'" :disabled="!!selectedCase.submissionIssues?.length" type="warning" @click="workflow('submit')">发送</el-button><HelpMark v-if="viewStage===4 && selectedCase.status === 'CALCULATED'" anchor-id="radiology.review" />
              <el-button v-if="viewStage===4 && selectedCase.status === 'PENDING_REVIEW' && props.canReview" type="warning" @click="workflow('review')">复核通过</el-button><HelpMark v-if="viewStage===4 && selectedCase.status === 'PENDING_REVIEW' && props.canReview" anchor-id="radiology.review" />
              <el-button v-if="viewStage===4 && selectedCase.status === 'REVIEWED' && props.canApprove" type="success" @click="workflow('approve')">签发</el-button><HelpMark v-if="viewStage===4 && selectedCase.status === 'REVIEWED' && props.canApprove" anchor-id="radiology.approve" />
              <el-button v-if="viewStage===5 && ['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" type="success" @click="generateReport">生成检测报告</el-button><HelpMark v-if="viewStage===5 && ['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" anchor-id="radiology.report" />
              <el-button v-if="(selectedCase.status === 'PENDING_REVIEW' && props.canReview) || (selectedCase.status === 'REVIEWED' && props.canApprove)" type="danger" plain @click="workflow('return')">退回修改</el-button>
              <el-button v-if="viewStage===5 && ['APPROVED','REPORTED'].includes(selectedCase.status) && props.canGenerateReport" @click="createRevision">创建修订版</el-button>
            </div>
          </section>

          <el-alert v-if="(selectedCase.profile?.selectedGroups || blankProfile(modality).selectedGroups || []).includes('PROTECTION') && !selectedCase.attachments.some(a=>a.category==='POINT_MAP')" title="本次包含放射工作场所卫生防护检测，必须上传点位图；当前缺图，请在附件中补充。" type="warning" :closable="false" show-icon />
          <section v-if="showCompleteness" class="submission-check"><p>检测完整性：{{completenessText}} · {{completenessHint}}</p><p>{{nextQcAction}}</p><p v-if="editabilityReason">{{editabilityReason}}</p>
            <strong>{{ selectedCase.calculationFresh ? '计算与当前数据一致' : '需要重新计算' }}</strong>
            <span>数据版本 {{ selectedCase.dataVersion }} · 计算版本 {{ selectedCase.calculatedDataVersion ?? '未计算' }}</span>
            <details v-if="selectedCase.submissionIssues?.length">
              <summary>提交前需要完成 {{ selectedCase.submissionIssues.length }} 项</summary>
              <ul><li v-for="issue in selectedCase.submissionIssues" :key="issue">{{ issue }}</li></ul>
            </details>
          </section>

          <el-tabs v-if="viewStage<=2" v-model="workTab" class="qc-tabs">
            <el-tab-pane label="基本信息" name="base"><div class="ppt-page-actions ppt-entry-actions"><el-button :disabled="!editable" @click="openEditCase">编辑信息</el-button></div>
              <RadiologyCaseSummary :modality="modality" :case-data="selectedCase" />
              <details class="ppt-help"><summary>补充信息及历史参数</summary>
              <div class="info-grid">


                <div><span>样品名称</span><strong>{{ selectedCase.profile?.sampleName || '-' }}</strong></div>
                <div><span>送检单位</span><strong>{{ selectedCase.profile?.submittingUnit || '-' }}</strong></div>
                <div v-if="modality==='DR'"><span>委托单位</span><strong>{{ selectedCase.profile?.commissionedUnit || '-' }}</strong></div>
                <div><span>所在地市 / 县区</span><strong>{{ [selectedCase.city,selectedCase.county].filter(Boolean).join(' / ') || '-' }}</strong></div>
                <div><span>设备编号</span><strong>{{ selectedCase.equipmentNo || '-' }}</strong></div>
                <div><span>生产厂家 <HelpMark anchor-id="radiology.equipment" /></span><strong>{{ selectedCase.manufacturer || '-' }}</strong></div>
                <div v-if="modality === 'CT'"><span>检测模体 <HelpMark anchor-id="radiology.phantom" /></span><strong>{{ selectedCase.phantomName || '-' }}</strong></div>
                <div v-if="modality === 'CT'"><span>模体厂家/型号 <HelpMark anchor-id="radiology.phantom" /></span><strong>{{ [selectedCase.phantomManufacturer, selectedCase.phantomModel].filter(Boolean).join(' / ') || '-' }}</strong></div>


                <div><span>检测人</span><strong>{{ selectedCase.profile?.detectorName || '-' }}</strong></div>
                <div><span>检测范围</span><strong>{{ (selectedCase.profile?.selectedGroups || blankProfile(modality).selectedGroups || []).map(detectionGroupLabel).join('、') }}</strong></div>
                <div><span>校准方式</span><strong>{{ calibrationLabel(selectedCase.profile?.calibrationMode || (selectedCase.protectionMode==='RESPONSE_CORRECTED' ? 'BOTH' : undefined)) }}</strong></div>
                <div><span>仪器响应时间</span><strong>{{ selectedCase.profile?.instrumentResponseTime || '-' }}</strong></div>
                <div><span>计算版本 <HelpMark anchor-id="radiology.calculation" /></span><strong>{{ selectedCase.calculationVersion }}</strong></div>
              </div>
              <div v-if="selectedCase.profile?.equipmentParameters" class="text-block"><span>设备参数</span><p>{{ selectedCase.profile.equipmentParameters }}</p></div>
              </details>
              <el-table v-if="selectedCase.profile?.instruments?.length" :data="selectedCase.profile.instruments" border class="instrument-snapshot">
                <el-table-column prop="name" label="仪器名称" min-width="150" />
                <el-table-column prop="model" label="型号" min-width="110" />
                <el-table-column prop="deviceNo" label="仪器编号" min-width="110" />
                <el-table-column prop="certificateNo" label="证书编号" min-width="150" />
                <el-table-column label="证书有效期" min-width="220"><template #default="s">{{ s.row.validFrom || '—' }} 至 {{ s.row.validTo || '—' }}</template></el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="质量控制项目" name="data">
              <RadiologyScopeEditor v-if="entryStep==='scope'" :modality="modality" :profile="profileDraft" :scopes="scopeRows" :names="scopeNames" :editable="editable" :busy="busy || scopePreviewBusy" :dirty="scopeDirty" @update:profile="setScopeProfile" @confirm="confirmScope" />
              <div v-else class="scope-current"><span>本次检测范围：{{(profileDraft.selectedGroups || []).map(detectionGroupLabel).join('、')}}</span><el-button @click="entryStep='scope'">修改检测范围与适用条件</el-button></div>
              <el-alert v-if="scopePreviewError" :title="scopePreviewError" type="error" :closable="false"/><el-button v-if="scopePreviewError" @click="previewScope">重试适用项目预览</el-button>

              <details class="ppt-help"><summary>Excel批量导入 / 模板下载</summary><div class="import-strip">
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
</details>              <el-alert
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

              <div v-if="entryStep==='input'" class="quality-layout"><aside class="quality-navigation" aria-label="本次检测项目清单"><section v-for="group in qualityGroups" :key="group"><h4>{{detectionGroupLabel(group)}}</h4><button v-for="scope in qualityScope.filter(item=>item.group===group)" :key="scope.itemCode" type="button" :class="{active:currentQuality===scope.itemCode&&scopeReady,excluded:!scope.applicable}" :disabled="!scopeReady || !scope.applicable || scope.pending" :title="scope.reason" @click="activeQuality=scope.itemCode"><strong>{{scopeNames[scope.itemCode]}}</strong><span>{{scopeStatus(scope)}}</span><small v-if="scope.reason">{{scope.reason}}</small></button></section><p v-if="!qualityScope.length">未选择质量控制项目。</p><el-button v-if="scopeReady && protectionSelected" @click="workTab='points'">进入场所防护</el-button></aside><div class="quality-projects"><el-alert v-if="!scopeReady" type="info" :closable="false" title="先选择检测范围并确认适用条件，保存后开始录入。"/><el-empty v-else-if="!currentQuality" description="本次无须录入质量控制项目；请继续场所防护或附件。"/>

                <section v-for="item in catalog.filter(i=>scopeReady && i.code===currentQuality)" :key="item.code" class="quality-project">
                  <header>
                    <div class="item-title">
                      <span class="item-code">{{ item.code }}</span>
                    <strong>{{ pptItemName(item.code,item.name) }}</strong>
                      <small>{{ item.standardCode }}</small>
                      <el-tag v-if="!measurementsPending && selectedCase.calculationFresh && computedFields(item.code).some(value => value.fieldRole === 'FINAL')" size="small" type="success">已计算</el-tag>
                    </div>
                  <div class="ppt-page-actions ppt-entry-actions"><el-button :disabled="!editable" @click="saveMeasurements">保存</el-button><el-button :disabled="!editable" @click="calculateCase">计算</el-button><el-button @click="previousQuality">上一步</el-button></div></header>
                  <p v-if="calculationItems.find(r => r.itemCode === item.code)?.judgement === 'NOT_APPLICABLE'" class="item-exclusion">
                    {{ calculationItems.find(r => r.itemCode === item.code)?.ruleDescription }}
                  </p>
                  <RadiologyConditionEditor v-if="!['RPD','CTDI'].includes(item.code) && (item.code!=='LCR' || rowIds(item.code).length)" v-model="conditionValues[item.code]" v-model:parameters="conditionValues['__params.'+item.code]" :item-code="item.code" :disabled="!editable"><template v-if="item.code==='RUE'" #response-model><label>拟合关系<el-select v-model="measurementValues[key('STP','STP-MODEL')]" :disabled="!editable" placeholder="选择拟合关系"><el-option label="线性响应" value="LINEAR"/><el-option label="对数相关" value="LOG"/><el-option label="幂相关" value="POWER"/><el-option label="自动选择最佳拟合（暂用）" value="BEST_FIT"/></el-select></label></template></RadiologyConditionEditor>
                  <details v-if="item.code!=='CTDI' && conditionRows(item.code).length && !rowIds(item.code).length" class="ppt-help"><summary>分组检测条件</summary><div class="condition-subrows"><label v-for="(name,index) in conditionRows(item.code)" :key="name">{{name}}检测条件<el-input v-model="conditionValues[item.code+'.'+(index+1)]" :disabled="!editable" placeholder="留空使用上方共有条件；可补充本组曝光参数" /></label></div></details>
                  <el-select v-if="item.code==='GTA'" v-model="conditionValues['__method.GTA']" :disabled="!editable" placeholder="选择倾角测量方法"><el-option label="模体检测法" value="PHANTOM" /><el-option label="斜率指示器法" value="INCLINOMETER" /></el-select>
                  <div v-if="item.code==='CTDI'" class="ctdi-parts"><strong>检测部位</strong><el-checkbox-group :model-value="ctdiParts()" :disabled="!editable" @update:model-value="setCtdiParts"><el-checkbox value="HEAD">头部模体</el-checkbox><el-checkbox value="BODY" :disabled="!selectedCase?.detectionCategory?.includes('验收')">体部模体</el-checkbox></el-checkbox-group><small>验收检测可多选；状态及稳定性检测仅头部模体。</small></div>
                  <MeasurementTable v-if="!rowIds(item.code).length" :code="item.code" :fields="visibleFields(item)" :conditions="conditionValues" :disabled="!editable" :results="computedFields(item.code).map(v=>({fieldCode:v.fieldCode,output:displayCalculated(v)}))"><template #default="{field}"><HelpMark v-if="showMeasurementHelp(field)" :anchor-id="measurementHelpAnchor(field)" />
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
                        <el-option label="线性响应 PV=aK+b" value="LINEAR" />
                        <el-option label="对数相关 PV=a·ln(K)+b" value="LOG" />
                        <el-option label="幂相关 PV=a·K^b（c=0）" value="POWER" />
                        <el-option label="自动选择最佳拟合（暂用）" value="BEST_FIT" />
                      </el-select>
                      <el-input
                        v-else
                        v-model="measurementValues[key(item.code, field.code)]"
                        :type="isTextField(item.code, field.code) ? 'text' : 'number'"
                        :disabled="!editable || field.code === 'a'"
                        :placeholder="pptFieldName(item.code,field.code,field.label)" :aria-label="pptFieldName(item.code,field.code,field.label)"
                      >
                        <template v-if="rawFieldUnit(item.code, field)" #append>{{ rawFieldUnit(item.code, field) }}</template>
                      </el-input>
                  </template></MeasurementTable>
                  <RadiologyMeasurementRows v-if="repeatableItems.includes(item.code)" :code="item.code" :fields="visibleFields(item).map(f=>({...f,unit:rawFieldUnit(item.code,f)}))" :values="measurementValues" :conditions="conditionValues" :results="computedFields(item.code).map(v=>({fieldCode:v.fieldCode,fieldLabel:v.fieldLabel,output:displayCalculated(v)}))" :disabled="!editable" />
                  <details v-if="computedFields(item.code).length" class="ppt-help"><summary>完整计算结果</summary><div class="computed-panel">
                    <div v-for="value in computedFields(item.code)" :key="value.id" :class="['computed-value', value.fieldRole.toLowerCase()]">
                      <span>{{ value.fieldLabel }}</span>
                      <strong>{{ resultDisplay(value) }}</strong>
                    </div>
                  </div></details>
                </section>
              </div></div>


            </el-tab-pane>

            <el-tab-pane label="场所防护" name="points" :disabled="!scopeReady || !protectionSelected"><div class="ppt-page-actions ppt-entry-actions"><el-button :disabled="!editable" @click="saveProtectionPoints">保存</el-button><el-button :disabled="!editable" @click="calculateCase">计算</el-button><el-button @click="workTab='data'">上一步</el-button></div>
              <div class="section-toolbar">
                <div><h4>场所卫生防护点位 <HelpMark anchor-id="radiology.protection-point" /></h4><p>DR 超过 25 μSv/h 后，系统按年工作负荷、居留因子和使用因子继续计算年有效剂量，并按 0.25 mSv/a 判定；CT 按 2.5 μSv/h 判定。</p></div>
                <el-button :disabled="!editable" @click="addProtectionPoint">增加点位</el-button>
                <el-button type="primary" :disabled="!editable || !pointDrafts.length" @click="saveProtectionPoints">保存并计算</el-button>
              </div>
<div class="protection-context">
                <el-input v-model="profileDraft.protectionConditions" :disabled="!editable" placeholder="防护检测条件：kV、mA、s/mAs、模体、扫描方式等" />
                <el-input v-model="profileDraft.backgroundOperation" :disabled="!editable" placeholder="设备运行情况（本底测量时未曝光）" />
                <el-input v-model="profileDraft.backgroundLocation" :disabled="!editable" placeholder="检测地点" />
                <el-input v-model="profileDraft.instrumentResponseTime" :disabled="!editable" placeholder="仪器响应时间（含单位）" />
                <el-button :disabled="!editable" @click="saveReportProfile">保存条件</el-button>
              </div>
              <h4>本底值与校准因子</h4><template v-for="item in catalog.filter(i=>i.code==='RPD')" :key="item.code"><MeasurementTable code="RPD" :fields="visibleFields(item)"><template #default="{field}"><el-input v-model="measurementValues[key('RPD',field.code)]" type="number" :disabled="!editable||field.code==='a'"><template v-if="rawFieldUnit('RPD',field)" #append>{{rawFieldUnit('RPD',field)}}</template></el-input></template></MeasurementTable></template><el-button :disabled="!editable" @click="saveMeasurements">保存本底与校准</el-button><div class="computed-panel"><div v-for="v in computedFields('RPD')" :key="v.id" class="computed-value"><span>{{v.fieldLabel}}</span><strong>{{resultDisplay(v)}}</strong></div></div>
              <el-table :data="pointDrafts" border @selection-change="(v:ProtectionPoint[])=>selectedPointRows=v"><el-table-column type="selection" width="38"/>
                <el-table-column label="点位代码" width="120"><template #default="scope"><el-input v-model="scope.row.pointCode" :disabled="!editable" /></template></el-table-column>
                <el-table-column label="检测点位" width="150"><template #default="scope"><el-input v-model="scope.row.pointName" :disabled="!editable" /></template></el-table-column>
                <el-table-column label="检测点位置" min-width="180"><template #default="scope"><el-input v-model="scope.row.locationDescription" :disabled="!editable" /></template></el-table-column>
                <el-table-column v-for="n in pointReadCount" :key="n" :label="`读数${n}（${selectedCase.rpdUnit || 'nSv/h'}）`" min-width="135"><template #default="scope"><el-input :model-value="pointRead(scope.row,n-1)" type="number" :disabled="!editable" @update:model-value="setPointRead(scope.row,n-1,$event)"/></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="150"><template #header>年工作负荷 <HelpMark anchor-id="radiology.annual-workload" /></template><template #default="scope"><el-input v-model.number="scope.row.annualWorkloadMas" type="number" :disabled="!editable" placeholder="mAs/a" /></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="120"><template #header>居留因子 T <HelpMark anchor-id="radiology.occupancy-factor" /></template><template #default="scope"><el-input v-model.number="scope.row.occupancyFactor" type="number" :disabled="!editable" /></template></el-table-column>
                <el-table-column v-if="modality === 'DR'" width="120"><template #header>使用因子 U <HelpMark anchor-id="radiology.use-factor" /></template><template #default="scope"><el-input v-model.number="scope.row.useFactor" type="number" :disabled="!editable" /></template></el-table-column>
                <el-table-column prop="averageDisplay" label="平均值" width="100" />
                <el-table-column label="检测结果" width="130"><template #default="scope">{{ scope.row.resultDisplay || '-' }} μSv/h</template></el-table-column>
                <el-table-column v-if="modality === 'DR'" label="年有效剂量" width="150"><template #default="scope">{{ scope.row.annualEffectiveDose ?? '-' }} mSv/a</template></el-table-column>
                <el-table-column label="结论" width="110"><template #default="scope">{{ judgementText(scope.row.judgement) }}</template></el-table-column>

              </el-table><div class="row-actions"><el-button :disabled="!editable" @click="addProtectionPoint">增加行</el-button><el-button :disabled="!editable||!selectedPointRows.length" @click="removeSelectedPoints">删除行</el-button></div>
            </el-tab-pane>

            <el-tab-pane label="附件及点位图" name="attachments"><div class="ppt-page-actions ppt-entry-actions"><el-button @click="workTab='points'">上一步</el-button></div>
              <div class="upload-zone">
                <el-select v-model="attachmentCategory" style="width: 160px" :disabled="!editable">
                  <el-option label="检测点位图" value="POINT_MAP" />
                  <el-option label="仪器证书" value="CERTIFICATE" />
                  <el-option label="模体说明书" value="PHANTOM_MANUAL" />
                  <el-option label="其他附件" value="OTHER" />
                </el-select>
                <input type="file" :disabled="!editable" accept="image/png,image/jpeg,image/webp,application/pdf,.docx,.xlsx" @change="chooseAttachment" aria-label="选择附件文件" />
                <el-button type="primary" :disabled="!editable||!pendingAttachment" @click="uploadAttachment">确认上传</el-button><span v-if="pendingAttachment">{{pendingAttachment.name}}</span>
                <span>点位图支持 PNG/JPEG/WebP；其他附件支持 PDF、DOCX、XLSX，单文件不超过 25MB。 <HelpMark anchor-id="radiology.phantom" /></span>
              </div>
              <el-table :data="selectedCase.attachments" border>
                <el-table-column label="类别" width="130"><template #default="scope">{{attachmentCategoryName(scope.row.category)}}</template></el-table-column>
                <el-table-column prop="originalName" label="文件名" min-width="240" />
                <el-table-column label="大小" width="100"><template #default="scope">{{ Math.ceil(scope.row.fileSize / 1024) }} KB</template></el-table-column>
                <el-table-column prop="sha256" label="SHA-256" min-width="220" show-overflow-tooltip />
                <el-table-column prop="uploadedAt" label="上传时间" width="180" />
                <el-table-column label="操作" width="150"><template #default="scope"><el-button link @click="downloadBlob(`/api/radiology/attachments/${scope.row.id}/download`, scope.row.originalName)">下载</el-button><label v-if="editable" class="replace-attachment">更改<input type="file" accept="image/png,image/jpeg,image/webp,.pdf,.docx,.xlsx" @change="replaceAttachment(scope.row.id,$event)" /></label><el-button link type="danger" :disabled="!editable" @click="removeAttachment(scope.row)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>



            <el-tab-pane v-if="props.canManageRules && showCompleteness" label="判定规则" name="rules">
              <div class="section-toolbar"><div><h4>版本化判定规则</h4><p>需求表未给出的限值不作猜测；在此按标准版本配置后自动判定。</p></div><el-button type="primary" @click="openCreateRule">新增规则</el-button></div>
              <el-table :data="rules" border>
                <el-table-column prop="itemCode" label="项目" width="90" />
                <el-table-column label="名称" min-width="160"><template #default="scope">{{pptItemName(scope.row.itemCode,scope.row.itemName)}}</template></el-table-column>
                <el-table-column prop="ruleVersion" label="规则版本" width="150" />
                <el-table-column label="限值/文本" min-width="170"><template #default="scope">{{ruleLimitText(scope.row)}}</template></el-table-column>
                <el-table-column prop="standardCode" label="标准" width="150" />
                <el-table-column label="启用" width="80"><template #default="scope">{{ scope.row.enabled ? '是' : '否' }}</template></el-table-column>
                <el-table-column label="操作" width="140"><template #default="scope"><el-button link @click="openEditRule(scope.row)">编辑</el-button><el-button link type="danger" @click="removeRule(scope.row)">删除</el-button></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
          <div v-if="viewStage<=2" class="ppt-page-actions"><el-button type="primary" @click="nextEntry">下一步</el-button></div>
<section v-if="viewStage===3"><el-tabs v-model="evaluationTab"><el-tab-pane label="自动评价" name="results">              <section  class="calculation-summary ppt-evaluation">
                <h4>本次计算结果 <HelpMark anchor-id="radiology.calculation" /></h4>
                <el-table :data="evaluationRows" :span-method="evaluationSpan" border>
                  <el-table-column prop="itemName" label="检测项目" min-width="160" />
                  <el-table-column prop="condition" label="检测条件" min-width="170" />
                  <el-table-column label="检测结果" min-width="160"><template #default="scope">{{scope.row.result}}<small v-if="scope.row.message && ['INCOMPLETE','NOT_CONFIGURED','NEEDS_ASSESSMENT'].includes(scope.row.judgement)" class="evaluation-note">{{scope.row.message}}</small></template></el-table-column>
                  <el-table-column prop="rule" label="标准要求" min-width="240" />
                  <el-table-column label="评价" width="100" fixed="right"><template #default="scope"><strong v-if="scope.row.judgement==='UNQUALIFIED'" class="evaluation-failure">不符合</strong><span v-else>{{evaluationText(scope.row.judgement)}}</span></template></el-table-column>
                </el-table>
              <el-input v-model="noteDraft" :disabled="!editable" type="textarea" placeholder="备注" :rows="2"/></section></el-tab-pane><el-tab-pane label="总体评价" name="overall"><div class="info-grid"><div><span>受理编号</span><strong>{{selectedCase.sampleAcceptanceNo}}</strong></div><div><span>受检单位名称</span><strong>{{selectedCase.inspectedUnitName}}</strong></div><div><span>检测标准</span><strong>{{selectedCase.standardBasis}}</strong></div></div>
              <div class="section-toolbar"><el-button :disabled="!editable" @click="generateNonconformities">点击生成不符合项</el-button></div>
              <el-input v-model="profileDraft.overallConclusion" :disabled="!editable" type="textarea" :rows="4" placeholder="根据检测范围撰写总体评价；留空时按质控与防护分别生成评价" />
</el-tab-pane></el-tabs><div class="ppt-page-actions"><el-button @click="goStage(2)">修改</el-button><el-button :disabled="!editable" @click="saveReportProfile">保存</el-button><el-button type="primary" @click="evaluationTab==='results'?evaluationTab='overall':goStage(4)">下一步</el-button></div></section>
<section v-if="viewStage===4"><h3>{{selectedCase.status==='PENDING_REVIEW'?'复核':selectedCase.status==='REVIEWED'?'签发':'编写人自核'}}</h3><div class="ppt-page-actions"><el-button @click="previewReport">报告预览</el-button><el-button @click="goStage(3)">返回修改</el-button><el-button @click="goStage(5)">下一步</el-button></div>              <div class="approval-grid">
                <div><span>编写人</span><strong>{{ selectedCase.writerName || '-' }}</strong></div>
                <div><span>复核人 <HelpMark anchor-id="radiology.review" /></span><strong>{{ selectedCase.reviewerName || '-' }}</strong><p>{{ selectedCase.reviewComment || '' }}</p></div>
                <div><span>授权签字人 <HelpMark anchor-id="radiology.approve" /></span><strong>{{ selectedCase.approverName || '-' }}</strong><p>{{ selectedCase.approvalComment || '' }}</p></div>
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

            </section>
<section v-if="viewStage===5"><h3>报告归档</h3>              <el-table :data="selectedCase.reports" border class="report-table">
                <el-table-column prop="reportNo" label="报告编号" min-width="180" />
                <el-table-column prop="fileName" label="文件名" min-width="260" />
                <el-table-column prop="generatedBy" label="生成人" width="110" />
                <el-table-column prop="generatedAt" label="生成时间" width="180" />

                <el-table-column label="操作" width="100"><template #default="scope"><el-button link @click="downloadBlob(`/api/radiology/reports/${scope.row.id}/download`, scope.row.fileName)">下载</el-button><el-button v-if="canGenerateReport" link type="danger" @click="deleteReport(scope.row.id)">删除</el-button></template></el-table-column>
              </el-table></section>
        </template>
      </main>


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

      <div ref="caseFormScroll" class="case-step-scroll"><el-form label-position="top" class="case-step-form">
        <section v-if="caseDialogStep === 0" class="case-form-section">
          <div class="case-section-title"><div><span>第 1 步</span><h3>受检单位信息</h3></div><HelpMark anchor-id="radiology.case-steps" /></div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12" :md="6"><el-form-item label="受理编号 *"><el-input v-model="caseForm.sampleAcceptanceNo" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="6"><el-form-item label="检测类别 *"><el-select v-model="caseForm.detectionCategory" style="width:100%"><el-option label="验收检测" value="验收检测" /><el-option label="状态检测" value="状态检测" /><el-option label="稳定性检测" value="稳定性检测" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="24" :md="12"><el-form-item label="委托单位"><el-input v-model="caseForm.profile.commissionedUnit" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :xs="24" :md="12"><el-form-item label="受检单位名称 *"><el-input v-model="caseForm.inspectedUnitName" placeholder="请填写单位完整名称" /></el-form-item></el-col>
            <el-col :xs="24" :md="12"><el-form-item label="受检单位地址"><el-input v-model="caseForm.inspectedUnitAddress" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :xs="24" :md="12"><el-form-item label="检测地点"><el-input v-model="caseForm.detectionLocation" /></el-form-item></el-col>
          </el-row><details class="ppt-help"><summary>报告及查询补充信息</summary><el-row :gutter="16">            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="案例编号 *"><el-input v-model="caseForm.caseCode" placeholder="例如 CT-20260828-01" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="样品名称"><el-input v-model="caseForm.profile.sampleName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="送检单位"><el-input v-model="caseForm.profile.submittingUnit" /></el-form-item></el-col>
            <el-col :xs="24" :sm="6"><el-form-item label="所在地市"><el-select v-model="caseForm.city" filterable allow-create><el-option v-for="r in cityOptions" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="6"><el-form-item label="所在县区"><el-select v-model="caseForm.county" filterable allow-create><el-option v-for="r in regionsLibrary.filter(r=>r.level>=3)" :key="r.id" :value="r.regionName" :label="r.regionName" /></el-select></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="联系人"><el-input v-model="caseForm.contactName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12" :md="8"><el-form-item label="联系电话"><el-input v-model="caseForm.contactPhone" /></el-form-item></el-col></el-row></details>
</section>
        <section v-else-if="caseDialogStep === 1" class="case-form-section">
          <div class="case-section-title"><div><span>第 2 步</span><h3>受检设备信息</h3></div><HelpMark anchor-id="radiology.equipment" /></div>
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12"><el-form-item><template #label>受检设备名称 * <HelpMark anchor-id="radiology.equipment" /></template><el-input v-model="caseForm.equipmentName" placeholder="填写设备铭牌名称" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="生产厂家"><el-input v-model="caseForm.manufacturer" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="受检设备编号"><el-input v-model="caseForm.equipmentNo" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="受检设备型号"><el-input v-model="caseForm.equipmentModel" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col v-if="modality==='CT'" :xs="24" :sm="12"><el-form-item label="出厂日期"><el-date-picker v-model="caseForm.profile.manufacturedDate" value-format="YYYY-MM-DD" /></el-form-item></el-col>
            <el-col :xs="24" :sm="modality==='CT'?12:24"><el-form-item label="设备参数"><EquipmentParameters v-model="caseForm.profile.equipmentParameters"/></el-form-item></el-col>
          </el-row>
          <details v-if="modality === 'CT'" class="ppt-help"><summary>检测模体信息</summary><el-row :gutter="16">
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体名称 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomName" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体厂家 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomManufacturer" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item><template #label>模体型号 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomModel" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="模体编号"><el-input v-model="caseForm.phantomSerialNo" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item><template #label>模体说明书/依据 <HelpMark anchor-id="radiology.phantom" /></template><el-input v-model="caseForm.phantomManualReference" placeholder="填写厂家、型号、说明书名称或版本" /></el-form-item></el-col>
          </el-row></details>
        </section>

        <section v-else-if="caseDialogStep === 2" class="case-form-section">
          <div class="case-section-title"><div><span>第 3 步</span><h3>检测机构信息</h3></div><HelpMark anchor-id="radiology.instrument" /></div>
<el-row :gutter="16">            <el-col :xs="24" :sm="12"><el-form-item label="检测起止日期 *"><el-date-picker v-model="inspectionRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="报告日期"><el-date-picker v-model="caseForm.reportDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="从标准库导入"><el-select v-model="selectedStandardIds" multiple filterable style="width:80%"><el-option v-for="s in standardsLibrary" :key="s.id" :value="s.id" :label="s.standardCode+' '+s.standardName" /></el-select><el-button @click="importStandards">导入依据</el-button></el-form-item></el-col>
            <el-col :span="24"><el-form-item><template #label>检测依据 <HelpMark anchor-id="basic.reference" /></template><el-input v-model="caseForm.standardBasis" type="textarea" :rows="2" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="从检测设备库选择"><el-select v-model="selectedInstrumentId" filterable style="width:80%"><el-option v-for="d in instrumentsLibrary" :key="d.id" :value="d.id" :label="d.deviceName+' '+(d.deviceNo || '')" /></el-select><el-button @click="addInstrument">添加仪器</el-button></el-form-item></el-col>
            <el-col :span="24"><el-table :data="caseForm.profile.instruments" border><el-table-column label="仪器" min-width="150"><template #default="s">{{s.row.name}} {{s.row.model}} / {{s.row.deviceNo}}</template></el-table-column><el-table-column label="证书编号" min-width="130"><template #default="s"><el-input v-model="s.row.certificateNo" /></template></el-table-column><el-table-column label="有效期起" min-width="150"><template #default="s"><el-date-picker v-model="s.row.validFrom" value-format="YYYY-MM-DD" style="width:140px" /></template></el-table-column><el-table-column label="有效期止" min-width="150"><template #default="s"><el-date-picker v-model="s.row.validTo" value-format="YYYY-MM-DD" style="width:140px" /></template></el-table-column><el-table-column width="70"><template #default="s"><el-button link @click="caseForm.profile.instruments?.splice(s.$index,1)">移除</el-button></template></el-table-column></el-table></el-col>
            <el-col :span="24"><el-form-item><template #label>检测设备及证书 <HelpMark anchor-id="radiology.instrument" /></template><el-input v-model="caseForm.instrumentDescription" type="textarea" :rows="2" placeholder="填写仪器名称、型号、编号和证书有效期" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="检测人"><el-select v-model="caseForm.profile.detectorName" filterable allow-create default-first-option><el-option v-for="s in staffLibrary" :key="s.id" :value="s.staffName || s.name || String(s.id)" :label="s.staffName || s.name || String(s.id)" /></el-select></el-form-item></el-col></el-row><details class="ppt-help"><summary>报告补充信息及防护参数</summary><el-row :gutter="16">            <el-col :xs="24" :sm="12"><el-form-item label="检测机构名称"><el-input v-model="caseForm.agencyName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="地区"><el-input v-model="caseForm.region" /></el-form-item></el-col>
            <el-col :xs="24" :sm="12"><el-form-item label="防护原始读数单位"><el-select v-model="caseForm.rpdUnit" style="width:100%"><el-option v-for="unit in ['nSv/h','μSv/h','mSv/h']" :key="unit" :value="unit" :label="unit" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="校准因子使用"><el-select v-model="caseForm.profile.calibrationMode" style="width:100%"><el-option label="结果无需修正" value="NONE" /><el-option label="需要仪器刻度修正，无需时间响应修正" value="INSTRUMENT" /><el-option label="需要仪器刻度、时间响应同步修正" value="BOTH" /><el-option label="需要时间响应修正，无需仪器刻度修正" value="TIME" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="场所防护数据处理"><el-select v-model="caseForm.protectionMode" style="width:100%"><el-option label="不扣本底" value="UNSUBTRACTED" /><el-option label="扣本底" value="SUBTRACTED" /></el-select></el-form-item></el-col>
            <el-col :span="24" v-if="modality === 'DR' && !caseForm.profile.itemApplicability"><el-checkbox v-model="caseForm.hasAec">设备具备AEC</el-checkbox><el-checkbox v-model="caseForm.hasRawImages">可获取未处理图像</el-checkbox><el-form-item v-if="!caseForm.hasAec || !caseForm.hasRawImages" label="设备功能与不检测理由"><el-input v-model="caseForm.featureReason" type="textarea" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item label="编写人"><el-input v-model="caseForm.writerName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item><template #label>预设审核人 <HelpMark anchor-id="radiology.review" /></template><el-input v-model="caseForm.reviewerName" /></el-form-item></el-col>
            <el-col :xs="24" :sm="8"><el-form-item><template #label>预设批准人 <HelpMark anchor-id="radiology.approve" /></template><el-input v-model="caseForm.approverName" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="备注"><el-input v-model="caseForm.notes" type="textarea" :rows="2" /></el-form-item></el-col></el-row></details></section>
        <section v-else class="case-form-section case-review-section">
          <div class="case-section-title"><div><span>第 4 步</span><h3>{{ editingCaseId ? '确认修改内容' : '确认并创建' }}</h3></div><HelpMark anchor-id="radiology.case-steps" /></div>
          <el-alert type="info" :closable="false" title="请核对关键字段。创建后仍可在草稿、已录入或已计算状态下修改案例信息。" show-icon />
          <RadiologyCaseSummary :modality="modality" :case-data="caseForm" />
<details class="ppt-help"><summary>补充信息</summary><dl class="case-review-grid"><div v-if="modality==='DR'"><dt>委托单位</dt><dd>{{caseForm.profile.commissionedUnit || '未填写'}}</dd></div>            <div><dt>案例编号</dt><dd>{{ caseForm.caseCode || '未填写' }}</dd></div>
            <div v-if="modality === 'CT'"><dt>检测模体</dt><dd>{{ [caseForm.phantomManufacturer, caseForm.phantomName, caseForm.phantomModel].filter(Boolean).join(' / ') || '未填写' }}</dd></div>
            <div><dt>所在地</dt><dd>{{ [caseForm.city,caseForm.county].filter(Boolean).join(' / ') || '未填写' }}</dd></div>
            <div><dt>本次检测范围</dt><dd>{{ (caseForm.profile.selectedGroups || []).map(detectionGroupLabel).join('、') }}</dd></div>
            <div><dt>编写 / 审核 / 批准</dt><dd>{{ [caseForm.writerName, caseForm.reviewerName, caseForm.approverName].map(value => value || '待指定').join(' / ') }}</dd></div>
</dl></details>
        </section>
      </el-form></div>

      <template #footer>
        <div class="case-dialog-footer">
          <div><el-button @click="requestCloseCaseDialog">取消</el-button><el-button v-if="!editingCaseId" plain @click="saveCaseDraftManually">保存草稿</el-button></div>
          <div>
            <el-button v-if="caseDialogStep > 0" @click="previousCaseStep">上一步</el-button>
            <el-button v-if="caseDialogStep < caseStepTitles.length - 1" type="primary" @click="nextCaseStep">下一步</el-button>
            <el-button v-else type="primary" @click="saveCase">{{ editingCaseId ? '保存修改' : '确定创建' }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="batchVisible" title="多案例Excel批量导入" width="88%">
      <p>每行填写一个原始字段，同一案例编号的数据合并建档。仅新增案例；已有编号或任意一行错误会阻止整批写入。导入后仍需补齐资料、计算和审核。</p>
      <div class="section-toolbar"><el-button @click="downloadBlob('/api/radiology/batch-import-template','多案例批量模板.xlsx')">下载批量模板与字段目录</el-button><input type="file" accept=".xlsx,.xls" aria-label="选择多案例批量工作簿" @change="selectBatchFile"/><el-button :disabled="!batchFile" @click="importBatch(true)">批量预检</el-button><el-button type="primary" :disabled="!batchFile || !batchResult?.dryRun || batchResult.rows.some(r=>r.status==='ERROR')" @click="importBatch(false)">正式批量导入</el-button></div>
      <el-alert v-if="batchResult" :closable="false" :type="batchResult.rows.some(r=>r.status==='ERROR')?'error':'success'" :title="`识别${batchResult.cases}个案例，${batchResult.dryRun?'预检未写入':`已导入${batchResult.appliedCases}个案例`}`" />
      <el-table v-if="batchResult" :data="batchResult.rows" border max-height="500"><el-table-column prop="row" label="Excel行号" width="110"/><el-table-column prop="caseCode" label="案例编号" min-width="200"/><el-table-column label="结果" width="110"><template #default="scope">{{scope.row.status==='ERROR'?'错误':scope.row.status==='IMPORTED'?'已导入':'预检通过'}}</template></el-table-column><el-table-column prop="message" label="说明" min-width="300"/></el-table>
    </el-dialog>
    <el-dialog v-model="previewVisible" :title="`报告预览-${previewSection==='cover'?'封面':previewSection==='notice'?'封二':'正文'}`" width="94%" top="3vh" destroy-on-close @opened="renderPreviewDocx">
      <el-radio-group v-if="previewPages.length" v-model="previewSection"><el-radio-button value="cover">封面</el-radio-button><el-radio-button value="notice">封二</el-radio-button><el-radio-button value="body">正文</el-radio-button></el-radio-group><div class="section-toolbar"><span v-if="previewPages.length">共{{previewPages.length}}页</span><el-button v-if="previewPages.length && selectedCase" @click="downloadBlob(`/api/radiology/cases/${selectedCase.id}/report-preview?format=pdf`,'检测报告预览.pdf')">下载PDF</el-button></div>
      <div v-if="previewPages.length" class="report-preview-pages"><figure v-for="(page,index) in previewPages" :key="index" v-show="previewSection==='cover'?index===0:previewSection==='notice'?index===1:index>=2"><figcaption>第{{index+1}}页 / 共{{previewPages.length}}页</figcaption><img :src="page" :alt="`检测报告第${index+1}页`" /></figure></div>
      <div v-else ref="previewContainer" class="report-preview-pages"></div><template #footer><el-button @click="previewVisible=false">退出</el-button><el-button @click="previewComplete=true">完成</el-button><template v-if="previewComplete && selectedCase"><el-button v-if="editable" @click="previewVisible=false;goStage(2)">返回修改</el-button><el-button v-if="(selectedCase.status==='PENDING_REVIEW'&&canReview)||(selectedCase.status==='REVIEWED'&&canApprove)" @click="workflow('return')">退回修改</el-button><el-form label-position="top" style="text-align:left"><el-form-item :label="selectedCase.status==='PENDING_REVIEW'?'复核意见':selectedCase.status==='REVIEWED'?'签发意见':'编写记录'"><el-input v-model="previewComment" type="textarea" placeholder="可留空"/></el-form-item></el-form><p>编写人：{{selectedCase.writerName || '—'}}　复核人：{{selectedCase.reviewerName || '—'}}　授权签字人：{{selectedCase.approverName || '—'}}（签名按客户答复线下完成）</p><el-button v-if="selectedCase.status==='CALCULATED'" :disabled="!!selectedCase.submissionIssues?.length" type="primary" @click="workflow('submit',previewComment)">发送</el-button><el-button v-if="selectedCase.status==='PENDING_REVIEW'&&canReview" type="primary" @click="workflow('review',previewComment)">发送</el-button><el-button v-if="selectedCase.status==='REVIEWED'&&canApprove" type="primary" @click="workflow('approve',previewComment)">发送</el-button></template></template>
    </el-dialog>
    <el-dialog v-model="queryVisible" title="综合查询（CT / DR / 个人剂量）" width="94%" destroy-on-close><InspectionQuery :token="token" @open="openQueryResult"/></el-dialog>
    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? '编辑判定规则' : '新增判定规则'" width="680px">
      <el-form label-position="top">
        <el-row :gutter="14">
          <el-col :span="12"><el-form-item label="检测项目"><el-select v-model="ruleForm.itemCode" style="width:100%"><el-option v-for="item in catalog" :key="item.code" :label="`${item.code} / ${pptItemName(item.code,item.name)}`" :value="item.code" /></el-select></el-form-item></el-col>
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
.report-preview-pages figure{margin:20px auto;text-align:center}.report-preview-pages img{width:100%;max-width:1000px;height:auto;display:block;margin:auto}.evaluation-note{display:block;color:#667}.evaluation-failure{font-weight:800;color:#a72c21}.ctdi-parts{padding:14px;border:1px solid #cddadf;margin:12px 0}
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
.info-grid strong { overflow-wrap:anywhere; }
.instrument-snapshot { margin-top:12px; }
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
.case-step-scroll { max-height:calc(100vh - 330px); overflow:auto; }.case-step-form { min-height:min(390px,45vh); padding:18px 16px 4px; }
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
@media (max-width:1500px) {
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

<style scoped>
.qc-layout{grid-template-columns:220px minmax(0,1fr)!important}.qc-status-rail{display:none}.qc-hero{background:white;color:#111;box-shadow:none;padding:18px 22px;border:0}.qc-hero h2{color:#111;font-size:25px}.case-main{background:white;padding:18px;border-radius:12px;min-width:0}.case-rail{background:white;border:0;border-radius:24px;box-shadow:none}.rail-filter{display:grid;grid-template-columns:1fr 1fr;gap:6px}.rail-filter>.el-button{grid-column:1/-1;margin:0}.select-hint{min-height:470px;display:flex;flex-direction:column;align-items:center;justify-content:center}.select-hint .el-button{margin:8px 0;min-width:180px}.workflow-actions{justify-content:flex-end}.case-heading{box-shadow:none;border:0}.calculation-summary{padding:0;border:0}.case-main :deep(.el-tabs__header){background:white}.qc-hero :deep(.help-mark),.info-grid :deep(.help-mark){display:none}.ppt-evaluation :deep(th.el-table__cell){background:#000;color:white}.ppt-evaluation :deep(td:last-child){background:#ccc}.evaluation-failure{color:#111}.submission-check{margin:12px 0}.field-grid{grid-template-columns:repeat(3,minmax(140px,1fr))}.computed-panel{background:#ddd}.info-grid{grid-template-columns:repeat(2,minmax(0,1fr))}@media(max-width:900px){.qc-layout{grid-template-columns:180px minmax(0,1fr)!important}.qc-hero h2{font-size:20px}}
</style>
<style scoped>.quality-navigation{margin:18px 0}.quality-navigation .el-select{width:min(100%,460px)}.quality-project header{padding:12px 0;border-bottom:1px solid #ddd}.computed-panel{display:flex;flex-wrap:wrap;background:#ddd}.computed-value{border:1px solid #aaa;min-width:140px;background:#ddd}.item-code{display:none}.quality-project .item-title strong{font-size:18px}</style>

<style scoped>.qc-layout.recording{grid-template-columns:minmax(0,1fr)!important}.quality-project :deep(.measurement-table td){min-width:90px}.equipment-features{margin:10px 0}.equipment-features .el-radio-group{display:flex;flex-wrap:wrap}.row-actions{margin:16px 0}</style>

<style scoped>.quality-project header{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.quality-project header .ppt-entry-actions{margin:0}.quality-project header .item-title{flex:1;min-width:180px}</style>

<style scoped>
.quality-layout{display:grid;grid-template-columns:240px minmax(0,1fr);gap:18px;align-items:start}.quality-navigation{border:1px solid #d1dce0;border-radius:8px;padding:12px;background:#f7fafb;max-height:75vh;overflow:auto}.quality-navigation h4{font-size:14px;margin:10px 0;line-height:1.5}.quality-navigation button{width:100%;display:block;text-align:left;margin:7px 0;padding:10px;border:1px solid #cbd8dc;border-radius:6px;background:white;color:#193642;cursor:pointer}.quality-navigation button.active{border-color:#00877a;background:#e5f5f1}.quality-navigation button:disabled{cursor:default;opacity:1}.quality-navigation button.excluded{background:#f0f1f2;color:#67737b}.quality-navigation button strong{display:block;font-size:13px;line-height:1.5}.quality-navigation button span{display:block;color:#007e73;font-size:12px;margin-top:4px}.quality-navigation button small{display:block;color:#63717b;font-size:12px;margin-top:4px}.quality-projects{min-width:0}@media(max-width:1050px){.quality-layout{grid-template-columns:1fr}.quality-navigation{max-height:320px}.quality-navigation section{display:flex;flex-wrap:wrap;gap:8px}.quality-navigation h4{width:100%}.quality-navigation button{width:calc(50% - 4px);margin:0}}@media(max-width:600px){.quality-navigation button{width:100%}}
</style>

<style scoped>.scope-current{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px;background:#f0f7f5;border:1px solid #cbded8;border-radius:8px;margin-bottom:12px}.scope-current>span{font-size:13px}@media(max-width:700px){.scope-current{display:block}.scope-current .el-button{margin-top:8px}}</style>
