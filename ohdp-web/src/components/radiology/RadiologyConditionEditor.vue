<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
const props=defineProps<{modelValue?: string;parameters?: string;itemCode: string;disabled?: boolean;pptTvd?:boolean;compactOnly?:boolean;referenceOnly?:boolean;hideReference?:boolean}>()
const ctScan=computed(()=>['CTDI','LBA','RST','MTF','LCD','HUL','HUA','HUU','NPS'].includes(props.itemCode))
const automaticExposure=computed(()=>['ARS','AIC','RUE'].includes(props.itemCode))
const noExposure=computed(()=>['TPA','GTA'].includes(props.itemCode))
const simpleExposure=computed(()=>['HVL','STP','ETD','LCR'].includes(props.itemCode))
const emit=defineEmits<{ 'update:modelValue':[string];'update:parameters':[string] }>()
const fields=reactive({mode:'mAs',sid:'',voltage:'',current:'',time:'',mas:'',doseUnit:'',thickness:'',referenceDose:'',extra:''})
watch(()=>props.parameters,value=>{
  try {Object.assign(fields,{mode:'mAs',sid:'',voltage:'',current:'',time:'',mas:'',doseUnit:'',thickness:'',referenceDose:'',extra:''},value?JSON.parse(value):{extra:props.modelValue||''})}catch{/* Keep the editable text if a legacy condition is not structured. */}
},{immediate:true})
function update() {
  emit('update:parameters',JSON.stringify(fields))
  const values=[fields.sid?`SID=${fields.sid}cm`:'',fields.voltage?`${fields.voltage}kV`:'',
    !ctScan.value && props.itemCode!=='ETD' && fields.mode==='mAs'?(fields.mas?`${fields.mas}mAs`:''):[fields.current?`${fields.current}mA`:'',fields.time?`${fields.time}s`:''].filter(Boolean).join('，'),
    fields.referenceDose?`参考剂量=${fields.referenceDose}μSv`:'',fields.thickness?`层厚=${fields.thickness}mm`:'',fields.doseUnit?`读数单位：${fields.doseUnit}`:'',fields.extra].filter(Boolean)
  emit('update:modelValue',values.join('，'))
}
</script>
<template>
  <el-input v-if="referenceOnly" v-model="fields.referenceDose" :disabled="disabled" aria-label="参考剂量（μSv）" @update:model-value="update"><template #append>μSv</template></el-input>
  <div v-else-if="pptTvd" class="tvd-inline-conditions">
    <label class="tvd-sid"><span>SID</span><el-input v-model="fields.sid" :disabled="disabled" aria-label="SID（cm）" @update:model-value="update"><template #append>cm</template></el-input></label>
    <div class="tvd-exposure">
      <el-select v-model="fields.mode" :disabled="disabled" aria-label="曝光参数方式" @update:model-value="update"><el-option label="管电流时间积" value="mAs"/><el-option label="管电流／时间" value="mA+s"/></el-select>
      <el-input v-if="fields.mode==='mAs'" v-model="fields.mas" :disabled="disabled" aria-label="管电流时间积（mAs）" @update:model-value="update"><template #append>mAs</template></el-input>
      <template v-else><el-input v-model="fields.current" :disabled="disabled" aria-label="管电流（mA）" @update:model-value="update"><template #append>mA</template></el-input><el-input v-model="fields.time" :disabled="disabled" aria-label="时间（s）" @update:model-value="update"><template #append>s</template></el-input></template>
    </div>
  </div>
  <template v-else-if="!noExposure">
    <h4 class="conditions-heading">检测条件</h4>
    <div class="condition-fields" :class="{'ctdi-fields':itemCode==='CTDI'}">
      <label v-if="!ctScan && !['AIC','RPD'].includes(itemCode)"><span>SID</span><el-input v-model="fields.sid" :disabled="disabled" aria-label="SID（cm）" @update:model-value="update"><template #append>cm</template></el-input></label>
      <label v-if="!compactOnly || !['STP','ILA'].includes(itemCode)"><span>管电压</span><el-input v-model="fields.voltage" :disabled="disabled" aria-label="管电压（kV）" @update:model-value="update"><template #append>kV</template></el-input></label>
      <div v-if="!ctScan && !automaticExposure && !simpleExposure && itemCode!=='ROQ' && (!compactOnly || itemCode!=='ILA')" class="exposure-controls">
        <el-select v-model="fields.mode" :disabled="disabled" aria-label="曝光参数方式" @update:model-value="update"><el-option label="管电流时间积" value="mAs"/><el-option label="管电流／时间" value="mA+s"/></el-select>
        <label v-if="fields.mode==='mAs'"><el-input v-model="fields.mas" :disabled="disabled" aria-label="管电流时间积（mAs）" @update:model-value="update"><template #append>mAs</template></el-input></label>
        <template v-else><label><el-input v-model="fields.current" :disabled="disabled" aria-label="管电流（mA）" @update:model-value="update"><template #append>mA</template></el-input></label><label><el-input v-model="fields.time" :disabled="disabled" aria-label="时间（s）" @update:model-value="update"><template #append>s</template></el-input></label></template>
      </div>
      <template v-if="ctScan"><label><span>管电流</span><el-input v-model="fields.current" :disabled="disabled" aria-label="管电流（mA）" @update:model-value="update"><template #append>mA</template></el-input></label><slot name="ctdi-layers"/><slot name="ctdi-thickness"/><label><span>扫描时间</span><el-input v-model="fields.time" :disabled="disabled" aria-label="扫描时间（s）" @update:model-value="update"><template #append>s</template></el-input></label><slot name="ctdi-factor"/></template>
      <label v-if="itemCode==='ETD'"><span>管电流</span><el-input v-model="fields.current" :disabled="disabled" aria-label="管电流（mA）" @update:model-value="update"><template #append>mA</template></el-input></label>
      <label v-if="ctScan && !['CTDI','LBA'].includes(itemCode) && (!compactOnly || !['RST','NPS'].includes(itemCode))"><span>{{itemCode==='NPS'?'层厚n':'层厚'}}</span><el-input v-model="fields.thickness" :disabled="disabled" aria-label="层厚（mm）" @update:model-value="update"><template #append>mm</template></el-input></label>
      <label v-if="['ROA','ROQ','ARP','ARS','AIC'].includes(itemCode)"><span>读数单位</span><el-select v-model="fields.doseUnit" :disabled="disabled" aria-label="读数单位" @update:model-value="update"><template v-if="['ROA','ROQ'].includes(itemCode)"><el-option label="mGy" value="mGy"/><el-option label="μGy" value="μGy"/></template><template v-else-if="itemCode==='ARS'"><el-option label="μGy" value="μGy"/></template><template v-else><el-option v-if="itemCode==='AIC'" label="电流 mA、时间 s" value="mA,s"/><el-option label="mAs" value="mAs"/><el-option label="DDI" value="DDI"/></template></el-select></label>
      <slot name="before-reference"/><label v-if="!hideReference && ['RUE','LCR'].includes(itemCode)"><span>参考剂量</span><el-input v-model="fields.referenceDose" :disabled="disabled" aria-label="参考剂量（μSv）" @update:model-value="update"><template #append>μSv</template></el-input></label><slot name="response-model"/>
    </div>
  </template>
  <p v-if="itemCode==='TPA'" class="condition-context">检测条件：床面负重约70kg</p>
  <details v-if="!compactOnly && !referenceOnly" class="condition-detail"><summary>{{pptTvd?'补充条件与历史记录':'补充及完整检测条件'}}</summary><label v-if="pptTvd">管电压（kV）<el-input v-model="fields.voltage" :disabled="disabled" @update:model-value="update" /></label><label>其他检测条件<el-input v-model="fields.extra" :disabled="disabled" placeholder="保留历史条件，可补充模体、滤过等" @update:model-value="update" /></label><el-input :model-value="modelValue" :disabled="disabled" type="textarea" :rows="2" placeholder="检测条件，可核对并补充" @update:model-value="emit('update:modelValue',$event)" /></details>
</template>
<style scoped>
.condition-fields{display:grid;grid-template-columns:repeat(4,minmax(120px,1fr));gap:10px;margin:10px 0}.condition-fields label{font-size:12px;color:#526278}.extra{grid-column:span 2}@media(max-width:900px){.condition-fields{grid-template-columns:repeat(2,minmax(120px,1fr))}}
</style>

<style scoped>
.tvd-inline-conditions{display:flex;flex-wrap:wrap;align-items:center;gap:46px;margin:4px 0 0;font-family:SimSun,"Songti SC",serif}
.tvd-sid{display:flex;flex-direction:row;align-items:center;gap:0;flex:0 0 230px;height:56px;border:2px solid #aaa;border-radius:8px;overflow:hidden;font-size:24px;font-weight:400;background:white}
.tvd-sid>span{color:#111;flex:0 0 72px;text-align:center;font-family:Arial,sans-serif}
.tvd-sid>.el-input{min-width:0;height:100%}
.tvd-exposure{display:flex;align-items:center;flex:0 0 394px;height:56px;min-width:0;gap:0;border:2px solid #aaa;border-radius:8px;overflow:hidden;background:white}
.tvd-exposure>.el-select{flex:0 0 200px;width:200px;height:100%}
.tvd-exposure>.el-input{flex:1;min-width:0;height:100%}
.tvd-inline-conditions :deep(.el-select__wrapper){min-height:52px;height:100%;padding:0 14px;font:24px SimSun,serif;box-shadow:none;border-radius:0}
.tvd-inline-conditions :deep(.el-select__placeholder){color:#111;font-weight:400}
.tvd-inline-conditions :deep(.el-input__wrapper){background:#d9d9d9;box-shadow:none;border-radius:0;padding:0 7px}
.tvd-inline-conditions :deep(.el-input__inner){font:24px Arial,sans-serif;color:#111;min-width:0;text-align:right}
.tvd-inline-conditions :deep(.el-input-group__append){flex:0 0 72px;padding:0;background:#fff;color:#111;box-shadow:none;border-radius:0;font:24px Arial,sans-serif}
.tvd-exposure:has(input[aria-label="管电流（mA）"]){flex-basis:520px}
.tvd-exposure:has(input[aria-label="管电流（mA）"]) :deep(.el-input-group__append){flex-basis:48px}
@media(max-width:850px){.tvd-inline-conditions{gap:18px}.tvd-exposure,.tvd-exposure:has(input[aria-label="管电流（mA）"]){flex-basis:min(520px,100%)}.tvd-exposure>.el-select{flex-basis:180px;width:180px}}
</style>

<style scoped>.condition-fields{grid-template-columns:repeat(4,minmax(100px,1fr))}.condition-detail{font-size:13px;margin:8px 0}.condition-detail summary{cursor:pointer}.extra{grid-column:span 2}</style>

<style scoped>.condition-fields.ctdi-fields{grid-template-columns:repeat(3,minmax(100px,1fr))}.condition-detail label{display:block;margin:8px 0}.condition-detail :deep(textarea){margin-top:8px}@media(max-width:700px){.condition-fields,.condition-fields.ctdi-fields{grid-template-columns:repeat(2,minmax(100px,1fr))}}</style>

<style scoped>.conditions-heading{font:400 24px SimSun,serif;margin:14px 8px 8px;color:#111}.condition-context{font:20px SimSun,serif;margin:14px 8px;color:#111}</style>
