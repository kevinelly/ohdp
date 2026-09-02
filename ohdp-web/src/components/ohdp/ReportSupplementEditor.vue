<script setup lang="ts">
import { computed } from 'vue'
import HelpMark from '../help/HelpMark.vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const model = computed(() => props.modelValue)

function addMaterial() {
  model.value.materials.push({ workplace: '', materialName: '', processStage: '', mainComponents: '', annualAmount: '', physicalState: '', positions: '' })
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
</script>

<template>
  <section class="report-supplement">
    <header>
      <div>
        <p class="eyebrow">2026 北海定期检测报告</p>
        <h3>正式报告内容 <HelpMark anchor-id="report.beihai-supplement" /></h3>
      </div>
      <p>按报告章节录入；带“必填”的内容会参加生成前预检。</p>
    </header>

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
        <label class="wide">生产工艺及危害因素产生环节（必填）<textarea v-model="model.productionProcess" rows="5" /></label>
        <label class="wide">辐射源项说明<textarea v-model="model.radiationSourceSummary" rows="2" /></label>
        <label class="wide">物料 VOC 定性分析开展情况（必填）<textarea v-model="model.vocAnalysisConclusion" rows="2" placeholder="需要明确：未开展，或已开展并填写组分结果" /></label>
        <label class="wide">排除性检测开展情况及结论（必填）<textarea v-model="model.exclusionScreeningConclusion" rows="2" placeholder="需要明确：未开展，或已开展并列出检测结果与接触判定" /></label>
        <label class="wide">原始记录完整性确认（必填）<textarea v-model="model.rawRecordCompletenessNote" rows="2" placeholder="确认采样编号、采样/检测日期、起止时间、检测仪器编号和检测条件已具备" /></label>
        <label class="wide">现场检测时生产情况（必填）<textarea v-model="model.productionCondition" rows="3" /></label>
        <label class="wide">检测布点说明（必填）<textarea v-model="model.samplingLayoutDescription" rows="3" /></label>
        <label class="wide">检测条件说明（必填）<textarea v-model="model.detectionConditions" rows="3" /></label>
        <label>现场环境气象条件（必填）<input v-model="model.environmentConditions" placeholder="温度、湿度、气压" /></label>
        <label>现场检测人员（必填）<input v-model="model.onsiteInspectors" placeholder="多人用顿号分隔" /></label>
        <label class="wide">未按计划检测/计划无变更说明（必填）<textarea v-model="model.planDeviation" rows="2" /></label>
      </div>

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
  </section>
</template>

<style scoped>
.report-supplement { margin-top: 18px; border-top: 1px solid #c9d7dc; padding-top: 16px; }
.report-supplement > header { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 12px; }
.report-supplement h3 { margin: 2px 0 0; font-size: 20px; }
.report-supplement header p { margin: 0; color: #5f7179; }
.eyebrow { color: #087f73 !important; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
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
button.add, button.remove { border-radius: 4px; padding: 7px 12px; cursor: pointer; }
button.add { margin-top: 8px; border: 1px solid #087f73; color: #087f73; background: #fff; }
button.remove { border: 1px solid #d7a2a2; color: #a23a3a; background: #fff; white-space: nowrap; }
@media (max-width: 900px) { .form-grid { grid-template-columns: 1fr; } .wide { grid-column: auto; } .report-supplement > header { display: block; } }
</style>
