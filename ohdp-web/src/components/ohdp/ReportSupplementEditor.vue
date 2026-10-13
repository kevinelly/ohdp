<script setup lang="ts">
import { computed } from 'vue'
import HelpMark from '../help/HelpMark.vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const model = computed(() => props.modelValue)

const formatDecisions = computed(() => [
  { label: '资质证书', value: model.value.qualificationCertificatePlan },
  { label: 'VOC 定性分析', value: model.value.vocAnalysisConclusion },
  { label: '辐射源项', value: model.value.radiationSourceSummary },
  { label: '排除性检测', value: model.value.exclusionScreeningConclusion },
  { label: '原始记录', value: model.value.rawRecordCompletenessNote },
  { label: '附件交付', value: model.value.attachmentDeliveryPlan },
  { label: '检测照片', value: model.value.photoEmbeddingPlan },
  { label: '多日检测', value: model.value.multiDayDisplayRule },
  { label: '加班折减', value: model.value.overtimeReductionRule },
  { label: '物理因素范围', value: model.value.physicalFactorScope },
  { label: '特殊部位与波段', value: model.value.specialMeasurementRule },
  { label: '签名与盖章', value: model.value.signatureStampMode },
])

const confirmedFormatCount = computed(() => formatDecisions.value.filter((item) => String(item.value || '').trim()).length)

function addMaterial() {
  model.value.materials.push({ workplace: '', materialName: '', processStage: '', mainComponents: '', annualAmount: '', physicalState: '', positions: '' })
}

function addVocAnalysisItem() {
  model.value.vocAnalysisItems.push({ chemicalName: '', manufacturerBatchNo: '', detectedComponents: '', casNo: '', peakAreaPercent: '' })
}

function addXrayDevice() {
  model.value.xrayDevices.push({ serialNo: '', workplace: '', deviceName: '', category: '', model: '', manufacturer: '', mainParameters: '', radiationType: '' })
}

function addSealedSource() {
  model.value.sealedSources.push({ serialNo: '', workplace: '', nuclideName: '', code: '', factoryActivity: '', category: '', installationLocation: '', purpose: '' })
}

function addUnsealedSource() {
  model.value.unsealedSources.push({ workplace: '', nuclideName: '', dailyMaxActivity: '', dailyEquivalentMaxActivity: '', annualMaxActivity: '', physicochemicalProperty: '', operationMode: '', storageMethodLocation: '' })
}

function addExclusionItem() {
  model.value.exclusionScreeningItems.push({ workplace: '', jobName: '', factorName: '', ctwa: '', pcTwa: '', contactJudgement: '' })
}

function addEquipment() {
  model.value.equipment.push({ workplace: '', location: '', equipmentName: '', totalCount: 1, runningCount: 1, operatingPositions: '' })
}

function addFacility() {
  model.value.protectionFacilities.push({ workplace: '', locationOrPosition: '', facilityName: '', facilityType: '', totalCount: 1, runningCount: 1 })
}

function addPpe() {
  model.value.personalProtectiveEquipment.push({ ppeName: '', manufacturer: '', model: '', protectionParameters: '', positions: '', usageStatus: '正常使用', category: '' })
}

function addSamplingPlan() {
  model.value.samplingPlans.push({
    workplace: '', jobName: '', operationType: '定岗', workersPerShift: 1,
    targetLocationTiming: '', factorName: '', limitType: 'PC-TWA', contactType: '相对稳定',
    detectionMode: '定点', detectionTimeType: '长时间检测', pointsPerDay: 1, timesPerDay: 1, days: 3,
  })
}

function removeRow(list: any[], index: number) {
  list.splice(index, 1)
}

function readImage(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

async function uploadCertificate(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) throw new Error('资质证书请上传图片文件')
  model.value.qualificationCertificateFileName = file.name
  model.value.qualificationCertificateImageDataUrl = await readImage(file)
}

async function uploadReportPhotos(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
    model.value.reportPhotos.push({ fileName: file.name, caption: '', takenDate: '', imageDataUrl: await readImage(file) })
  }
  input.value = ''
}
</script>

<template>
  <section class="report-supplement">
    <fieldset>
      <legend>混合接触评价</legend>
      <label>适用性（由专业人员确认）<select v-model="model.mixedExposureAssessment"><option value="">待确认</option><option value="NOT_APPLICABLE">不适用</option><option value="APPLICABLE">适用同效应相加评价 Σ(Ci/Li) ≤ 1</option></select></label>
      <label>适用或不适用依据（必填）<textarea v-model="model.mixedExposureAssessmentBasis" /></label>
      <p>适用时，在检测结果中指定组合编号及共同健康效应依据。同岗位、同日期的组合分别评价。</p>
    </fieldset>
    <header>
      <div>
        <h3>2026 北海定期检测报告内容 <HelpMark anchor-id="report.beihai-supplement" /></h3>
      </div>
      <p>按报告章节录入；带“必填”的内容会参加生成前预检。</p>
    </header>

    <section class="format-readiness" aria-labelledby="format-readiness-title">
      <div class="format-readiness-summary">
        <div>
          <h4 id="format-readiness-title">正式报告格式确认</h4>
          <p>未确认的项目会阻止正式报告生成；系统不会自行写成“不涉及”，原模板章节将继续保留。</p>
        </div>
        <strong>{{ confirmedFormatCount }}/{{ formatDecisions.length }} 已确认</strong>
      </div>
      <ul>
        <li v-for="item in formatDecisions" :key="item.label" :class="{ confirmed: String(item.value || '').trim() }">
          <span>{{ item.label }}</span>
          <b>{{ String(item.value || '').trim() ? '已确认' : '待确认' }}</b>
        </li>
      </ul>
    </section>

    <details open>
      <summary>一、用人单位基本情况</summary>
      <div class="form-grid">
        <label>报告页眉单位简称（必填）<input v-model="model.reportHeaderName" placeholder="用于每页页眉，如：北海某公司" /></label>
        <label>统一社会信用代码（必填）<input v-model="model.socialCreditCode" /></label>
        <label>法定代表人/负责人（必填）<input v-model="model.legalRepresentative" /></label>
        <label class="wide">单位注册地址<input v-model="model.registeredAddress" /></label>
        <label class="wide">技术服务地址<input v-model="model.technicalServiceAddress" /></label>
        <label>联系人<input v-model="model.contactName" /></label>
        <label>联系人电话<input v-model="model.contactPhone" /></label>
        <label class="wide">行业类型（大类/中类/小类，必填）<input v-model="model.industryCategory" placeholder="如：制造业/专用设备制造业/环境保护专用设备制造" /></label>
        <label>建成（投产）时间<input v-model="model.commissioningDate" placeholder="如：2018年6月" /></label>
        <label>经济类型<input v-model="model.economicType" placeholder="如：有限责任公司" /></label>
        <label>企业规模<input v-model="model.companyScale" placeholder="大型/中型/小型/微型" /></label>
        <label>劳动定员<input v-model.number="model.employeeCount" type="number" min="0" /></label>
        <label>外包人数<input v-model.number="model.outsourcedWorkerCount" type="number" min="0" /></label>
        <label>劳务派遣人数<input v-model.number="model.dispatchedWorkerCount" type="number" min="0" /></label>
      </div>
    </details>

    <details>
      <summary>二、原辅材料、设备与防护配置</summary>
      <h4>原辅材料及产品</h4>
      <label class="confirm"><input v-model="model.materialsConfirmedNotApplicable" type="checkbox" /> 已核实并确认本项目不涉及原辅材料/产品表内容</label>
      <div class="table-scroll">
        <table>
          <thead><tr><th>车间/场所</th><th>物料名称</th><th>物料环节</th><th>主要成分</th><th>年用量/产量</th><th>物理状态</th><th>使用岗位</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.materials" :key="`m-${index}`">
              <td><input v-model="row.workplace" /></td><td><input v-model="row.materialName" /></td><td><input v-model="row.processStage" /></td>
              <td><input v-model="row.mainComponents" /></td><td><input v-model="row.annualAmount" /></td><td><input v-model="row.physicalState" /></td>
              <td><input v-model="row.positions" /></td><td><button type="button" class="remove" @click="removeRow(model.materials, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button type="button" class="add" @click="addMaterial">添加原辅材料</button>

      <h4>物料挥发性有机组分定性分析</h4>
      <label class="confirm"><input v-model="model.vocAnalysisPerformed" type="checkbox" /> 已实际开展物料挥发性有机组分定性分析</label>
      <p class="section-guidance">未勾选时，正式报告不生成“表3.2-2 物料挥发性有机组分定性结果表”。</p>
      <div v-if="model.vocAnalysisPerformed" class="table-scroll">
        <div class="form-grid">
          <label>采样日期<input v-model="model.vocSamplingDate" /></label><label>检测日期<input v-model="model.vocDetectionDate" /></label>
          <label class="wide">检测依据<input v-model="model.vocDetectionBasis" /></label><label>检测方法<input v-model="model.vocDetectionMethod" /></label>
          <label>检测机构（必填）<input v-model="model.vocTestingOrganization" /></label><label>外部报告编号（必填）<input v-model="model.vocSourceReportNo" /></label><label>仪器/设备及编号<input v-model="model.vocInstrument" /></label><label>总 VOC 峰面积百分比<input v-model="model.vocTotalPeakAreaPercent" /></label>
          <label>无机组分峰面积百分比<input v-model="model.vocInorganicPeakAreaPercent" /></label>
        </div>
        <table>
          <thead><tr><th>有机化学品名称</th><th>生产厂家及批号</th><th>检出组分</th><th>CAS 号</th><th>峰面积百分比</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.vocAnalysisItems" :key="`voc-${index}`">
              <td><input v-model="row.chemicalName" /></td><td><input v-model="row.manufacturerBatchNo" /></td>
              <td><input v-model="row.detectedComponents" /></td><td><input v-model="row.casNo" /></td><td><input v-model="row.peakAreaPercent" /></td>
              <td><button type="button" class="remove" @click="removeRow(model.vocAnalysisItems, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button v-if="model.vocAnalysisPerformed" type="button" class="add" @click="addVocAnalysisItem">添加 VOC 定性结果</button>

      <h4>主要生产设备</h4>
      <label class="confirm"><input v-model="model.equipmentConfirmedNotApplicable" type="checkbox" /> 已核实并确认本项目不涉及生产设备表内容</label>
      <div class="table-scroll">
        <table>
          <thead><tr><th>车间/场所</th><th>厂房/地点</th><th>设备名称</th><th>总数</th><th>运行数</th><th>操作岗位</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.equipment" :key="`e-${index}`">
              <td><input v-model="row.workplace" /></td><td><input v-model="row.location" /></td><td><input v-model="row.equipmentName" /></td>
              <td><input v-model.number="row.totalCount" type="number" min="0" /></td><td><input v-model.number="row.runningCount" type="number" min="0" /></td>
              <td><input v-model="row.operatingPositions" /></td><td><button type="button" class="remove" @click="removeRow(model.equipment, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button type="button" class="add" @click="addEquipment">添加生产设备</button>

      <h4>职业病防护设施</h4>
      <label class="confirm"><input v-model="model.protectionFacilitiesConfirmedNotApplicable" type="checkbox" /> 已核实并确认本项目不涉及职业病防护设施表内容</label>
      <div class="table-scroll">
        <table>
          <thead><tr><th>车间/场所</th><th>设置地点/岗位</th><th>设施名称</th><th>设施类型</th><th>总数</th><th>运行数</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.protectionFacilities" :key="`f-${index}`">
              <td><input v-model="row.workplace" /></td><td><input v-model="row.locationOrPosition" /></td><td><input v-model="row.facilityName" /></td>
              <td><input v-model="row.facilityType" /></td><td><input v-model.number="row.totalCount" type="number" min="0" /></td>
              <td><input v-model.number="row.runningCount" type="number" min="0" /></td><td><button type="button" class="remove" @click="removeRow(model.protectionFacilities, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button type="button" class="add" @click="addFacility">添加防护设施</button>

      <h4>个人防护用品</h4>
      <label class="confirm"><input v-model="model.ppeConfirmedNotApplicable" type="checkbox" /> 已核实并确认本项目不涉及个人防护用品表内容</label>
      <div class="table-scroll">
        <table>
          <thead><tr><th>用品名称</th><th>生产厂家</th><th>规格型号</th><th>防护参数</th><th>配备岗位</th><th>使用情况</th><th>分类</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.personalProtectiveEquipment" :key="`p-${index}`">
              <td><input v-model="row.ppeName" /></td><td><input v-model="row.manufacturer" /></td><td><input v-model="row.model" /></td>
              <td><input v-model="row.protectionParameters" /></td><td><input v-model="row.positions" /></td><td><input v-model="row.usageStatus" /></td>
              <td><input v-model="row.category" /></td><td><button type="button" class="remove" @click="removeRow(model.personalProtectiveEquipment, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button type="button" class="add" @click="addPpe">添加防护用品</button>
    </details>

    <details>
      <summary>三、检测范围、生产工艺与现场情况</summary>
      <div class="form-grid">
        <label class="wide">检测范围（必填）<textarea v-model="model.detectionScope" rows="3" /></label>
        <label class="wide">法律、法规与规章（必填）<textarea v-model="model.lawsRegulations" rows="4" /></label>
        <label class="wide">基础资料（必填）<textarea v-model="model.basicMaterials" rows="4" /></label>
        <label class="wide">生产工艺及危害因素产生环节（必填）<textarea v-model="model.productionProcess" rows="5" /></label>
        <label>辐射源项处理方式（必填）
          <select v-model="model.radiationSourceMode"><option value="NONE">无辐射源项</option><option value="EXCLUDED_CONTRACT">①合同排除，另行委托</option><option value="SEPARATE_REPORT">②合作检测，另出报告</option><option value="INCLUDED_REPORT">③合并写入本报告（主体按实际资料）</option></select>
        </label>
        <label v-if="model.radiationSourceMode === 'SEPARATE_REPORT'">另行报告编号<input v-model="model.radiationSeparateReportNo" /></label>
        <label class="wide">辐射源项说明<textarea v-model="model.radiationSourceSummary" rows="2" /></label>
        <label class="wide">物料 VOC 定性分析开展情况（必填）<textarea v-model="model.vocAnalysisConclusion" rows="2" placeholder="需要明确：未开展，或已开展并填写组分结果" /></label>
        <label class="wide">排除性检测开展情况及结论（必填）<textarea v-model="model.exclusionScreeningConclusion" rows="2" placeholder="需要明确：未开展，或已开展并列出检测结果与接触判定" /></label>
        <label class="wide">物理因素检测范围（必填）<textarea v-model="model.physicalFactorScope" rows="2" placeholder="逐项确认噪声、高温、工频电场、高频、微波、紫外等涉及或不涉及" /></label>
        <label class="wide">原始记录完整性确认（必填）<textarea v-model="model.rawRecordCompletenessNote" rows="2" placeholder="确认采样编号、采样/检测日期、起止时间、检测仪器编号和检测条件已具备" /></label>
        <label class="wide">现场检测时生产情况（必填）<textarea v-model="model.productionCondition" rows="3" /></label>
        <label class="wide">检测布点说明（必填）<textarea v-model="model.samplingLayoutDescription" rows="3" /></label>
        <label class="wide">检测条件说明（必填）<textarea v-model="model.detectionConditions" rows="3" /></label>
        <label>现场环境气象条件（必填）<input v-model="model.environmentConditions" placeholder="温度、湿度、气压" /></label>
        <label>现场检测人员（必填）<input v-model="model.onsiteInspectors" placeholder="多人用顿号分隔" /></label>
        <label class="wide">未按计划检测/计划无变更说明（必填）<textarea v-model="model.planDeviation" rows="2" /></label>
      </div>

      <template v-if="model.radiationSourceMode === 'INCLUDED_REPORT'">
        <h4>③ 纳入本报告的辐射源项</h4>
        <div class="table-scroll"><table><thead><tr><th>序号</th><th>场所</th><th>射线装置</th><th>类别</th><th>型号</th><th>厂家</th><th>主要参数</th><th>射线种类</th><th></th></tr></thead><tbody>
          <tr v-for="(row,index) in model.xrayDevices" :key="`xr-${index}`"><td><input v-model="row.serialNo" /></td><td><input v-model="row.workplace" /></td><td><input v-model="row.deviceName" /></td><td><input v-model="row.category" /></td><td><input v-model="row.model" /></td><td><input v-model="row.manufacturer" /></td><td><input v-model="row.mainParameters" /></td><td><input v-model="row.radiationType" /></td><td><button type="button" class="remove" @click="removeRow(model.xrayDevices,index)">删除</button></td></tr>
        </tbody></table></div><button type="button" class="add" @click="addXrayDevice">添加射线装置</button>
        <div class="table-scroll"><table><thead><tr><th>序号</th><th>场所</th><th>核素</th><th>编码</th><th>出厂活度</th><th>类别</th><th>安装位置</th><th>用途</th><th></th></tr></thead><tbody>
          <tr v-for="(row,index) in model.sealedSources" :key="`ss-${index}`"><td><input v-model="row.serialNo" /></td><td><input v-model="row.workplace" /></td><td><input v-model="row.nuclideName" /></td><td><input v-model="row.code" /></td><td><input v-model="row.factoryActivity" /></td><td><input v-model="row.category" /></td><td><input v-model="row.installationLocation" /></td><td><input v-model="row.purpose" /></td><td><button type="button" class="remove" @click="removeRow(model.sealedSources,index)">删除</button></td></tr>
        </tbody></table></div><button type="button" class="add" @click="addSealedSource">添加密封源</button>
        <div class="table-scroll"><table><thead><tr><th>场所</th><th>核素</th><th>日最大操作量</th><th>日等效最大操作量</th><th>年最大操作量</th><th>理化性质</th><th>操作方式</th><th>贮存方式/地点</th><th></th></tr></thead><tbody>
          <tr v-for="(row,index) in model.unsealedSources" :key="`us-${index}`"><td><input v-model="row.workplace" /></td><td><input v-model="row.nuclideName" /></td><td><input v-model="row.dailyMaxActivity" /></td><td><input v-model="row.dailyEquivalentMaxActivity" /></td><td><input v-model="row.annualMaxActivity" /></td><td><input v-model="row.physicochemicalProperty" /></td><td><input v-model="row.operationMode" /></td><td><input v-model="row.storageMethodLocation" /></td><td><button type="button" class="remove" @click="removeRow(model.unsealedSources,index)">删除</button></td></tr>
        </tbody></table></div><button type="button" class="add" @click="addUnsealedSource">添加非密封源</button>
      </template>

      <h4>排除性检测</h4>
      <label class="confirm"><input v-model="model.exclusionScreeningPerformed" type="checkbox" /> 已实际开展排除性检测</label>
      <p class="section-guidance">未勾选时，正式报告删除第 4.2 节及表 4.2-1。</p>
      <div v-if="model.exclusionScreeningPerformed" class="table-scroll"><table><thead><tr><th>场所</th><th>岗位</th><th>危害因素</th><th>CTWA</th><th>PC-TWA</th><th>接触判定</th><th></th></tr></thead><tbody>
        <tr v-for="(row,index) in model.exclusionScreeningItems" :key="`ex-${index}`"><td><input v-model="row.workplace" /></td><td><input v-model="row.jobName" /></td><td><input v-model="row.factorName" /></td><td><input v-model="row.ctwa" /></td><td><input v-model="row.pcTwa" /></td><td><input v-model="row.contactJudgement" /></td><td><button type="button" class="remove" @click="removeRow(model.exclusionScreeningItems,index)">删除</button></td></tr>
      </tbody></table></div><button v-if="model.exclusionScreeningPerformed" type="button" class="add" @click="addExclusionItem">添加排除性检测结果</button>

      <h4>现场采样与测量计划（至少 1 条）</h4>
      <div class="table-scroll">
        <table class="sampling-table">
          <thead><tr><th>车间/场所</th><th>岗位</th><th>作业类型</th><th>每班人数</th><th>对象/地点及时机</th><th>检测项目</th><th>限值类型</th><th>接触类型</th><th>方式</th><th>时间类型</th><th>点/对象数</th><th>次数/天</th><th>天数</th><th></th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in model.samplingPlans" :key="`s-${index}`">
              <td><input v-model="row.workplace" /></td><td><input v-model="row.jobName" /></td><td><input v-model="row.operationType" /></td>
              <td><input v-model.number="row.workersPerShift" type="number" min="0" /></td><td><input v-model="row.targetLocationTiming" /></td>
              <td><input v-model="row.factorName" /></td><td><input v-model="row.limitType" /></td><td><input v-model="row.contactType" /></td>
              <td><input v-model="row.detectionMode" /></td><td><input v-model="row.detectionTimeType" /></td>
              <td><input v-model.number="row.pointsPerDay" type="number" min="0" /></td><td><input v-model.number="row.timesPerDay" type="number" min="0" /></td>
              <td><input v-model.number="row.days" type="number" min="0" /></td><td><button type="button" class="remove" @click="removeRow(model.samplingPlans, index)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button type="button" class="add" @click="addSamplingPlan">添加采样计划</button>
    </details>

    <details>
      <summary>四、检测结论、问题与建议</summary>
      <div class="form-grid">
        <label>职业病危害风险分类（必填）
          <select v-model="model.riskCategory"><option value="">请选择</option><option>一般</option><option>严重</option></select>
        </label>
        <label class="wide">存在的主要职业病危害因素（必填）<textarea v-model="model.majorHazards" rows="2" /></label>
        <label class="wide">超标情况及原因分析<textarea v-model="model.overLimitSummary" rows="3" placeholder="无超标时写明本次检测未发现超标岗位" /></label>
        <label class="wide">职业病危害防治存在的主要问题（必填）<textarea v-model="model.majorProblems" rows="4" /></label>
        <label class="wide">针对性建议（必填）<textarea v-model="model.recommendations" rows="6" /></label>
      </div>
    </details>

    <details>
      <summary>五、正式报告格式、附件与签章</summary>
      <p class="section-guidance">以下内容属于报告格式决策。请记录客户确认后的口径；没有确认时保持为空，生成前预检会明确拦截。</p>
      <div class="form-grid">
        <label class="wide">资质证书使用方案（必填）
          <textarea v-model="model.qualificationCertificatePlan" rows="2" placeholder="填写证书版本和有效期" />
        </label>
        <label class="wide">当前有效资质证书图片（必传，嵌入第3页）
          <input type="file" accept="image/png,image/jpeg" @change="uploadCertificate" />
          <small v-if="model.qualificationCertificateFileName">已选：{{ model.qualificationCertificateFileName }}</small>
        </label>
        <label class="wide">附件交付方案（必填）
          <textarea v-model="model.attachmentDeliveryPlan" rows="2" readonly />
        </label>
        <label class="wide">检测照片处理方案（必填）
          <textarea v-model="model.photoEmbeddingPlan" rows="2" readonly />
        </label>
        <label class="wide">现场检测照片（可多选）<input type="file" accept="image/png,image/jpeg" multiple @change="uploadReportPhotos" /></label>
        <div v-if="model.reportPhotos?.length" class="wide photo-list">
          <div v-for="(photo,index) in model.reportPhotos" :key="`photo-${index}`" class="photo-row">
            <span>{{ photo.fileName }}</span><input v-model="photo.caption" placeholder="地点/内容说明" /><input v-model="photo.takenDate" type="date" />
            <button type="button" class="remove" @click="removeRow(model.reportPhotos,index)">删除</button>
          </div>
        </div>
        <label>多日检测日期展示规则（必填）
          <input v-model="model.multiDayDisplayRule" readonly />
        </label>
        <label class="wide">超过 8 小时工作制折减规则（必填）
          <textarea v-model="model.overtimeReductionRule" rows="2" placeholder="建议：明确采用的标准条款、折减公式、适用岗位及报告展示口径" />
        </label>
        <label>签名与盖章方式（必填）
          <input v-model="model.signatureStampMode" readonly />
        </label>
        <label class="wide">特殊检测部位与波段规则（必填）
          <textarea v-model="model.specialMeasurementRule" rows="2" placeholder="不涉及时需明确确认；涉及时填写头、胸、腹或其他部位，以及实际检测波段" />
        </label>
      </div>
    </details>
  </section>
</template>

<style scoped>
.report-supplement { margin-top: 18px; border-top: 1px solid #c9d7dc; padding-top: 16px; }
.report-supplement > header { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 12px; }
.report-supplement h3 { margin: 2px 0 0; font-size: 20px; }
.report-supplement header p { margin: 0; color: #5f7179; }
.format-readiness { margin: 12px 0 16px; padding: 14px; border: 1px solid #cbd8dc; border-radius: 8px; background: #f7f9f9; }
.format-readiness-summary { display: flex; align-items: start; justify-content: space-between; gap: 20px; }
.format-readiness-summary h4 { margin: 0 0 4px; font-size: 15px; }
.format-readiness-summary p, .section-guidance { margin: 0; color: #5f7179; font-weight: 400; }
.format-readiness-summary strong { flex: 0 0 auto; padding: 4px 8px; border-radius: 4px; color: #6a4d16; background: #fff3d7; font-size: 12px; }
.format-readiness ul { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; margin: 12px 0 0; padding: 0; list-style: none; }
.format-readiness li { display: flex; align-items: center; justify-content: space-between; gap: 8px; min-width: 0; padding: 7px 8px; border: 1px solid #d5dfe2; border-radius: 6px; background: #fff; }
.format-readiness li span { overflow: hidden; color: #405761; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.format-readiness li b { flex: 0 0 auto; color: #a9680f; font-size: 11px; }
.format-readiness li.confirmed { border-color: #b9d9d1; background: #e7f5f2; }
.format-readiness li.confirmed b { color: #137d58; }
.section-guidance { margin-bottom: 12px; }
details { border: 1px solid #cbd8dc; border-radius: 6px; background: #fff; margin: 10px 0; padding: 0 14px 14px; }
summary { cursor: pointer; padding: 14px 0; font-size: 16px; font-weight: 700; color: #17323d; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }
label { display: grid; gap: 6px; color: #405761; font-weight: 600; }
.wide { grid-column: 1 / -1; }
input, textarea, select { width: 100%; box-sizing: border-box; border: 1px solid #b9cbd1; border-radius: 4px; background: #fff; padding: 9px 10px; color: #17323d; font: inherit; }
.confirm { display: flex; align-items: center; gap: 8px; margin: -2px 0 8px; color: #6a4d16; font-weight: 500; }
.confirm input { width: auto; }
textarea { resize: vertical; }
h4 { margin: 18px 0 8px; color: #17323d; }
.table-scroll { overflow-x: auto; }
table { min-width: 980px; width: 100%; border-collapse: collapse; }
th, td { border: 1px solid #cedadd; padding: 6px; vertical-align: top; }
th { background: #edf3f4; color: #405761; white-space: nowrap; }
td input { min-width: 110px; padding: 7px; }
.sampling-table { min-width: 1780px; }
.photo-list { display: grid; gap: 8px; }
.photo-row { display: grid; grid-template-columns: 180px 1fr 170px auto; gap: 8px; align-items: center; }
.photo-row span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
button.add, button.remove { border-radius: 4px; padding: 7px 12px; cursor: pointer; }
button.add { margin-top: 8px; border: 1px solid #087f73; color: #087f73; background: #fff; }
button.remove { border: 1px solid #d7a2a2; color: #a23a3a; background: #fff; white-space: nowrap; }
@media (max-width: 1200px) { .format-readiness ul { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } .wide { grid-column: auto; } .report-supplement > header { display: block; } .format-readiness-summary { display: block; } .format-readiness-summary strong { display: inline-block; margin-top: 10px; } }
</style>
