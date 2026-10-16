<script setup lang="ts">
import { computed, ref } from 'vue'
import HelpMark from '../help/HelpMark.vue'
import { pptFieldName } from './radiologyDraftSupport'
interface Field { code: string; label: string; unit: string; required: boolean }
const props=defineProps<{code:string;fields:Field[];values:Record<string,string|number>;conditions:Record<string,string>;disabled:boolean;results?:{fieldCode:string;fieldLabel:string;output:string}[]}>()
const rows=computed<number[]>(()=>JSON.parse(props.conditions['__rows.'+props.code] || '[]'))
const fields=computed(()=>props.fields.filter(f=>!['TVD','ETD'].includes(props.code) || f.code.startsWith(props.code+'1-') || f.code===props.code+'-K1'))
const key=(id:number,field:string)=>`${props.code}::R${id}__${field}`
function label(f:Field) {
  if(['TVD','ETD'].includes(props.code)) {
    if(f.code.endsWith('-0'))return props.code==='TVD'?'预设值':'指示值T0'
    if(f.code.includes('-K'))return '校准因子'
    return (props.code==='ETD'?'读数T':'读数')+f.code.slice(-1)
  }
  return pptFieldName(props.code,f.code,f.label)
}
function enable() {
  const ids=['TVD','ETD'].includes(props.code)?[1,2,3]:[1]
  for(const id of ids) {
    props.conditions[`${props.code}.R${id}`]=props.conditions[`${props.code}.${id}`] || ''
    for(const field of fields.value) {
      const old=['TVD','ETD'].includes(props.code)?field.code.replace(props.code+'1-',props.code+id+'-').replace(props.code+'-K1',props.code+'-K'+id):field.code
      props.values[key(id,field.code)]=props.values[props.code+'::'+old] ?? ''
    }
  }
  props.conditions['__rows.'+props.code]=JSON.stringify(ids)
}
const resultColumns=computed(()=>{
 const columns=new Map<string,string>()
 for(const result of props.results||[]) { const match=/^R\d+__(.+)$/.exec(result.fieldCode);if(match)columns.set(match[1]!,result.fieldLabel.replace(/^第\d+行\s*/,'')) }
 return [...columns].map(([code,label])=>({code,label}))
})
function rowResult(id:number,code:string){return props.results?.find(r=>r.fieldCode===`R${id}__${code}`)?.output||'—'}
const columns=computed(()=>{
 const inputs=fields.value.filter(field=>props.code!=='ROA'||field.code!=='ROA-K').map(field=>({code:field.code,label:label(field),field:field as Field|undefined}))
 const planned:Record<string,{code:string;label:string}[]>={
  TVD:[{code:'MEAN_1',label:'平均值'},{code:'DISPLAY_1',label:'偏差'}],
  ETD:[{code:'MEAN_1',label:'平均值'},{code:'DEVIATION_MS_1',label:'检测结果'}],
  ROA:[{code:'MEAN',label:'平均值'},{code:'RESULT',label:'重复性CV'}],
  HCR:[{code:'RESULT',label:'检测结果'}],LCR:[{code:'RESULT',label:'检测结果'}],
  RST:[{code:'WWMIN',label:'最小窗宽WWmin'},{code:'CTHM',label:'半高CT值CThm'},{code:'MEAN',label:'平均值'},{code:'DEVIATION',label:'偏差'}]
 }
 const outputs=(planned[props.code] || resultColumns.value).map(column=>({...column,field:undefined as Field|undefined}))
 if(['HCR','RST'].includes(props.code)){
  const order=props.code==='HCR'?['HCR-MANUFACTURER','HCR-NYQUIST','HCR-BASELINE','HCR1']:['RST-FACTOR','CTbackground','CTmax','WWMIN','CTHM','R1','R2','R3','R4','MEAN','R0','DEVIATION']
  const rank=(code:string)=>order.includes(code)?order.indexOf(code):order.length
  return [...inputs,...outputs].sort((a,b)=>rank(a.code)-rank(b.code))
 }
 if(props.code!=='ETD')return [...inputs,...outputs]
 const mean=outputs.find(c=>c.code.startsWith('MEAN')) || {code:'MEAN_1',label:'校正平均值',field:undefined}
 return [...inputs.filter(c=>!c.code.includes('-K')),mean,...inputs.filter(c=>c.code.includes('-K')),...outputs.filter(c=>c.code!==mean.code)]
})
const selected=ref<number[]>([])
function addRow(){if(!rows.value.length)enable();add()}
function deleteRows(){if(!rows.value.length){enable();remove(rows.value[rows.value.length-1]!);return}for(const id of selected.value)if(rows.value.length>1)remove(id);selected.value=[]}
function add() {props.conditions['__rows.'+props.code]=JSON.stringify([...rows.value,Math.max(...rows.value)+1])}
function remove(id:number) {
  props.conditions['__rows.'+props.code]=JSON.stringify(rows.value.filter(r=>r!==id))
  delete props.conditions[`${props.code}.R${id}`]
  for(const field of fields.value) delete props.values[key(id,field.code)]
}
</script>
<template>
 <div class="measurement-rows">
  <details v-if="code==='ROA' && rows.length" class="roa-calibration-detail"><summary>补充参数（各行校准因子）</summary><div class="measurement-extra"><label v-for="(id,index) in rows" :key="id">第{{index+1}}行校准因子<el-input v-model="values[key(id,'ROA-K')]" type="number" :disabled="disabled" :aria-label="`ROA第${id}行校准因子`"/></label></div></details>
  <div v-if="rows.length" class="rows-scroll"><table><thead><tr><th></th><th class="row-condition-column">检测条件</th><th v-for="(column,index) in columns" :key="column.code">{{column.label}} <HelpMark v-if="!column.field && index===columns.length-1" anchor-id="radiology.calculation"/></th></tr></thead><tbody><tr v-for="id in rows" :key="id"><td><input type="checkbox" v-model="selected" :value="id" :disabled="disabled" :aria-label="`选择第${id}行`"/></td><td class="row-condition-column"><el-input v-model="conditions[`${code}.R${id}`]" :disabled="disabled" :aria-label="`${code}第${id}行检测条件`" placeholder="使用共有条件"/></td><td v-for="column in columns" :key="column.code" :class="{calculated:!column.field}"><template v-if="column.field"><el-select v-if="column.field.code==='LCR-BASELINE-UNIT'" v-model="values[key(id,column.code)]" :disabled="disabled"><el-option value="个" label="个"/></el-select><el-input v-else v-model="values[key(id,column.code)]" type="number" :disabled="disabled" :aria-label="`${code}第${id}行${column.label}`"><template v-if="column.field.unit" #append>{{column.field.unit}}</template></el-input></template><template v-else>{{rowResult(id,column.code)}}</template></td></tr></tbody></table></div>
  <div class="row-actions"><el-button :disabled="disabled||rows.length>=30" @click="addRow">增加行</el-button><el-button :disabled="disabled||rows.length===1||(rows.length>0&&!selected.length)" @click="deleteRows">删除行</el-button></div>
 </div>
</template>
<style scoped>.rows-scroll{overflow:auto;margin:16px 0}table{border-collapse:collapse;min-width:100%}th,td{border:1px solid #aaa;padding:8px;min-width:120px}th{background:#d4d4d4;font-weight:500}td:first-child,th:first-child{min-width:28px}.row-actions{margin:16px 0}</style>

<style scoped>td.calculated{background:#ddd;text-align:center}</style>

<style scoped>.row-condition-column{display:none}</style>
