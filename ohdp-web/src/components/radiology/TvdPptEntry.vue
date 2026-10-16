<script setup lang="ts">
import { computed } from 'vue'
import RadiologyConditionEditor from './RadiologyConditionEditor.vue'
import HelpMark from '../help/HelpMark.vue'

const props=defineProps<{
  values:Record<string,string|number>
  conditions:Record<string,string>
  results:{fieldCode:string;fieldLabel?:string;output:string}[]
  supplementOpen?:boolean
  disabled:boolean
}>()
const emit=defineEmits<{next:[];'close-supplement':[]}>()
const dynamicRows=computed<number[]>(()=>JSON.parse(props.conditions['__rows.TVD'] || '[]'))
const rows=computed(()=>dynamicRows.value.length ? dynamicRows.value : [1,2,3])
const inputColumns=['预设值','读数1','读数2','读数3','校准因子']
function fieldKey(id:number,column:number) {
  const field=column===4 ? 'TVD-K1' : `TVD1-${column}`
  return dynamicRows.value.length ? `TVD::R${id}__${field}` : `TVD::${column===4 ? 'TVD-K'+id : 'TVD'+id+'-'+column}`
}
function result(id:number,kind:'MEAN'|'DISPLAY') {
  const code=dynamicRows.value.length ? `R${id}__${kind}_1` : `${kind}_${id}`
  return props.results.find(value=>value.fieldCode===code)?.output || '—'
}
function meanValue(id:number){const value=result(id,'MEAN');return value==='—'?'':value.replace(/\s*kV$/, '')}
function deviationValue(id:number){const value=result(id,'DISPLAY');return value==='—'?'':value}
function enableRows() {
  if(dynamicRows.value.length)return
  // Preserve the original three groups and their individual conditions when
  // switching to the existing repeatable-row storage format.
  for(const id of rows.value) {
    props.conditions[`TVD.R${id}`]=props.conditions[`TVD.${id}`] || ''
    for(let column=0;column<5;column++) {
      const value=props.values[fieldKey(id,column)]
      const field=column===4 ? 'TVD-K1' : `TVD1-${column}`
      props.values[`TVD::R${id}__${field}`]=value ?? ''
    }
  }
  props.conditions['__rows.TVD']=JSON.stringify(rows.value)
}
function addRow() {
  if(props.disabled || rows.value.length>=30)return
  enableRows()
  props.conditions['__rows.TVD']=JSON.stringify([...rows.value,Math.max(...rows.value)+1])
}
function deleteLastRow() {
  if(props.disabled || rows.value.length<=1)return
  enableRows()
  const id=rows.value[rows.value.length-1]!
  for(let column=0;column<5;column++)delete props.values[fieldKey(id,column)]
  delete props.conditions[`TVD.R${id}`]
  props.conditions['__rows.TVD']=JSON.stringify(rows.value.slice(0,-1))
}
function setCondition(value:string,parameters=false) {
  props.conditions[parameters ? '__params.TVD' : 'TVD']=value
}
</script>

<template>
  <section class="tvd-ppt-entry" aria-label="管电压指示的偏离录入">
    <h4 class="conditions-title">检测条件</h4>
    <RadiologyConditionEditor item-code="TVD" ppt-tvd compact-only :model-value="conditions.TVD" :parameters="conditions['__params.TVD']" :disabled="disabled" @update:model-value="setCondition($event)" @update:parameters="setCondition($event,true)" />
    <div class="tvd-matrix-scroll">
      <div class="tvd-matrix" role="table" aria-label="管电压指示的偏离测量值">
        <div class="tvd-grid tvd-column-headings" role="row">
          <span v-for="(label,index) in inputColumns" :key="label" role="columnheader">{{label}}<span v-if="index===0" class="required">*</span></span>
          <span role="columnheader" title="沿用系统计算的校正平均值，单位kV">平均值</span>
          <span role="columnheader">偏差 <HelpMark anchor-id="radiology.calculation" /></span>
        </div>
        <div v-for="(id,index) in rows" :key="id" class="tvd-grid tvd-reading-row" role="row" :aria-label="`第${index+1}行`">
          <div v-for="(label,column) in inputColumns" :key="label" class="tvd-reading-cell" role="cell">
            <input v-model="values[fieldKey(id,column)]" type="number" step="any" :disabled="disabled" :aria-label="`TVD第${index+1}行${label}`" />
            <span v-if="column<4" class="reading-unit">kV</span>
          </div>
          <div class="tvd-output tvd-mean" role="cell" :aria-label="`第${index+1}行平均值`"><span>{{meanValue(id)}}</span><span>kV</span></div>
          <div class="tvd-output tvd-deviation" role="cell" :aria-label="`第${index+1}行偏差`">{{deviationValue(id)}}</div>
        </div>
      </div>
    </div>
    <div class="tvd-footer">
      <div class="tvd-row-actions">
        <el-button :disabled="disabled || rows.length>=30" @click="addRow">增加行</el-button>
        <el-button :disabled="disabled || rows.length<=1" title="删除最后一行，至少保留一行；保存后生效" @click="deleteLastRow">删除行</el-button>
      </div>
      <el-button class="tvd-next" type="primary" @click="emit('next')">下一步</el-button>
    </div>
    <el-dialog :model-value="!!supplementOpen" title="管电压补充条件与历史记录" width="min(900px,94vw)" @update:model-value="emit('close-supplement')">
      <RadiologyConditionEditor item-code="TVD" :model-value="conditions.TVD" :parameters="conditions['__params.TVD']" :disabled="disabled" @update:model-value="setCondition($event)" @update:parameters="setCondition($event,true)" />
      <div class="tvd-group-conditions">
        <label v-for="(id,index) in rows" :key="id">第{{index+1}}组检测条件
          <el-input v-model="conditions[dynamicRows.length ? `TVD.R${id}` : `TVD.${id}`]" :disabled="disabled" placeholder="留空使用共有条件" :aria-label="`TVD第${index+1}组检测条件`" />
        </label>
      </div>
      <h4>完整计算结果</h4><p v-for="value in results" :key="value.fieldCode">{{value.fieldLabel || value.fieldCode}}：{{value.output}}</p>
      <template #footer><el-button @click="emit('close-supplement')">关闭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.tvd-ppt-entry{padding:12px 0 0;color:#111;font-family:SimSun,"Songti SC",serif}
.conditions-title{font-size:26px;font-weight:400;margin:14px 0 4px 16px;line-height:1.4}
.tvd-ppt-entry>:deep(.tvd-inline-conditions){margin-left:16px}
.tvd-matrix-scroll{overflow-x:auto;margin-top:18px;padding:0 2px 2px}
.tvd-matrix{min-width:790px}
.tvd-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:10px}
.tvd-column-headings{align-items:center;margin:0 0 12px;font-size:24px;text-align:center;line-height:1.5;font-weight:400}
.required{margin-left:2px}
.tvd-reading-row{margin-bottom:5px;align-items:stretch}
.tvd-reading-cell,.tvd-output{display:flex;align-items:center;height:56px;min-width:0;border:2px solid #aaa;border-radius:8px;background:white;overflow:hidden;font:24px Arial,sans-serif;color:#111}
.tvd-reading-cell:focus-within{border-color:#00857a}
.tvd-reading-cell input{border:0;outline:0;background:transparent;min-width:0;width:100%;padding:0 4px 0 8px;height:100%;color:inherit;font:inherit;text-align:right;box-shadow:none;-moz-appearance:textfield}
.tvd-reading-cell input::-webkit-inner-spin-button,.tvd-reading-cell input::-webkit-outer-spin-button{-webkit-appearance:none;margin:0}
.tvd-reading-cell input:disabled{color:#68777d;cursor:not-allowed}
.reading-unit{flex:0 0 auto;padding:0 13px 0 4px}
.tvd-output{justify-content:center;padding:0 12px;font-size:22px;overflow-wrap:anywhere}
.tvd-mean{justify-content:flex-end;gap:6px}
.tvd-deviation{background:#f2f2f2}
.tvd-column-headings :deep(.help-mark){display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;background:#588d37;color:white;border:0;border-radius:50%;font:700 15px Arial,sans-serif;vertical-align:middle}
.tvd-footer{display:flex;justify-content:space-between;align-items:flex-start;margin:28px 36px 20px 20px;gap:16px}
.tvd-row-actions{display:flex;gap:24px}
.tvd-row-actions .el-button{width:108px;height:48px;padding:0;font:700 20px "Microsoft YaHei",sans-serif;border:2px solid #133f51;background:transparent;border-radius:8px}
.tvd-row-actions .el-button+.el-button{margin-left:0}
.tvd-footer .tvd-next{margin-top:40px;width:144px;height:48px;font:400 22px "Microsoft YaHei",sans-serif;color:#fff;border-radius:8px}
.tvd-group-conditions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:20px 0}
.tvd-group-conditions label{display:flex;flex-direction:column;gap:6px}
@media(max-width:850px){.tvd-footer{margin-right:4px;margin-left:4px}.tvd-row-actions{gap:10px}.tvd-row-actions .el-button{width:94px}.tvd-group-conditions{grid-template-columns:1fr}}
</style>

<style scoped>
.tvd-mean>span{white-space:nowrap;flex-shrink:0}
@media(max-width:1500px){
 .tvd-matrix{min-width:700px}
 .tvd-grid{grid-template-columns:repeat(5,minmax(0,1fr)) minmax(0,1.2fr) minmax(0,1.15fr);gap:8px}
 .tvd-column-headings{font-size:20px}
 .tvd-reading-cell,.tvd-output{font-size:18px}
 .tvd-output{padding:0 7px;white-space:nowrap}
 .reading-unit{padding-right:7px}
 .tvd-footer{margin-right:8px;margin-left:8px}
}
</style>
