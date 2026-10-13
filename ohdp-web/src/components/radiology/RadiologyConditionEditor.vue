<script setup lang="ts">
import { reactive, watch } from 'vue'
const props=defineProps<{modelValue?: string;parameters?: string;itemCode: string;disabled?: boolean}>()
const emit=defineEmits<{ 'update:modelValue':[string];'update:parameters':[string] }>()
const fields=reactive({mode:'mAs',sid:'',voltage:'',current:'',time:'',mas:'',doseUnit:'',extra:''})
watch(()=>props.parameters,value=>{
  try {Object.assign(fields,{mode:'mAs',sid:'',voltage:'',current:'',time:'',mas:'',doseUnit:'',extra:''},value?JSON.parse(value):{})}catch{/* Keep the editable text if a legacy condition is not structured. */}
},{immediate:true})
function update() {
  emit('update:parameters',JSON.stringify(fields))
  const values=[fields.sid?`SID=${fields.sid}cm`:'',fields.voltage?`${fields.voltage}kV`:'',
    fields.mode==='mAs'?(fields.mas?`${fields.mas}mAs`:''):[fields.current?`${fields.current}mA`:'',fields.time?`${fields.time}s`:''].filter(Boolean).join('，'),
    fields.doseUnit?`读数单位：${fields.doseUnit}`:'',fields.extra].filter(Boolean)
  if(values.length)emit('update:modelValue',values.join('，'))
}
</script>
<template>
  <div class="condition-fields">
    <label>SID（cm）<el-input v-model="fields.sid" :disabled="disabled" @change="update" /></label>
    <label>管电压（kV）<el-input v-model="fields.voltage" :disabled="disabled" @change="update" /></label>
    <label>曝光参数方式<el-select v-model="fields.mode" :disabled="disabled" @change="update"><el-option label="管电流时间积 mAs" value="mAs" /><el-option label="管电流 mA＋时间 s" value="mA+s" /></el-select></label>
    <label v-if="fields.mode==='mAs'">mAs<el-input v-model="fields.mas" :disabled="disabled" @change="update" /></label>
    <template v-else><label>mA<el-input v-model="fields.current" :disabled="disabled" @change="update" /></label><label>时间（s）<el-input v-model="fields.time" :disabled="disabled" @change="update" /></label></template>
    <label v-if="['ROA','ROQ','ARP','ARS','AIC'].includes(itemCode)">读数单位<el-select v-model="fields.doseUnit" :disabled="disabled" @change="update"><template v-if="['ROA','ROQ'].includes(itemCode)"><el-option label="mGy" value="mGy" /><el-option label="μGy" value="μGy" /></template><template v-else-if="itemCode==='ARS'"><el-option label="μGy" value="μGy" /></template><template v-else><el-option v-if="itemCode==='AIC'" label="电流 mA、时间 s" value="mA,s" /><el-option label="mAs" value="mAs" /><el-option label="DDI" value="DDI" /></template></el-select></label>
    <label class="extra">其他检测条件<el-input v-model="fields.extra" :disabled="disabled" placeholder="扫描方式、层厚、模体、滤过、参考剂量等" @change="update" /></label>
  </div>
  <el-input :model-value="modelValue" :disabled="disabled" type="textarea" :rows="2" placeholder="检测条件，可核对并补充" @update:model-value="emit('update:modelValue',$event)" />
</template>
<style scoped>
.condition-fields{display:grid;grid-template-columns:repeat(4,minmax(120px,1fr));gap:10px;margin:10px 0}.condition-fields label{font-size:12px;color:#526278}.extra{grid-column:span 2}@media(max-width:900px){.condition-fields{grid-template-columns:repeat(2,minmax(120px,1fr))}}
</style>
