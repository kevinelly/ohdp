<script setup lang="ts">
import { computed } from 'vue'
import RadiologyConditionEditor from './RadiologyConditionEditor.vue'
import HelpMark from '../help/HelpMark.vue'

const props=defineProps<{
  values:Record<string,string|number>
  conditions:Record<string,string>
  results:{fieldCode:string;output:string}[]
  disabled:boolean
}>()
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
    <RadiologyConditionEditor item-code="TVD" ppt-tvd :model-value="conditions.TVD" :parameters="conditions['__params.TVD']" :disabled="disabled" @update:model-value="setCondition($event)" @update:parameters="setCondition($event,true)" />
    <div class="tvd-matrix-scroll">
      <div class="tvd-matrix" role="table" aria-label="管电压指示的偏离测量值">
        <div class="tvd-grid tvd-column-headings" role="row">
          <span v-for="(label,index) in inputColumns" :key="label" role="columnheader">{{label}}<span v-if="index===0" class="required">*</span></span>
          <span role="columnheader" title="沿用系统计算的校正平均值，单位kV">平均值</span>
          <span role="columnheader">偏差 <HelpMark anchor-id="radiology.calculation" /></span>
        </div>
        <div v-for="(id,index) in rows" :key="id" class="tvd-grid tvd-reading-row" role="row" :aria-label="`第${index+1}行`">
          <div v-for="(label,column) in inputColumns" :key="label" role="cell">
            <el-input v-model="values[fieldKey(id,column)]" type="number" :disabled="disabled" :aria-label="`TVD第${index+1}行${label}`">
              <template v-if="column<4" #append>kV</template>
            </el-input>
          </div>
          <div class="tvd-output" role="cell" :aria-label="`第${index+1}行平均值`">{{result(id,'MEAN')}}</div>
          <div class="tvd-output" role="cell" :aria-label="`第${index+1}行偏差`">{{result(id,'DISPLAY')}}</div>
        </div>
      </div>
    </div>
    <div class="tvd-row-actions">
      <el-button :disabled="disabled || rows.length>=30" @click="addRow">增加行</el-button>
      <el-button :disabled="disabled || rows.length<=1" title="删除最后一行，至少保留一行；保存后生效" @click="deleteLastRow">删除行</el-button>
    </div>
    <details class="tvd-group-conditions">
      <summary>分组检测条件</summary>
      <label v-for="(id,index) in rows" :key="id">第{{index+1}}组
        <el-input v-model="conditions[dynamicRows.length ? `TVD.R${id}` : `TVD.${id}`]" :disabled="disabled" placeholder="留空使用上方共有条件" :aria-label="`TVD第${index+1}组检测条件`" />
      </label>
    </details>
  </section>
</template>

<style scoped>
.tvd-ppt-entry{padding:12px 0 0;color:#162f3a}
.conditions-title{font-size:19px;font-weight:400;margin:10px 0 8px}
.tvd-matrix-scroll{overflow-x:auto;margin-top:18px;padding-bottom:2px}
.tvd-matrix{min-width:790px}
.tvd-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:8px}
.tvd-column-headings{align-items:center;margin:0 0 9px;font-size:17px;text-align:center;line-height:1.7}
.required{margin-left:3px}
.tvd-reading-row{margin-bottom:7px;align-items:stretch}
.tvd-reading-row :deep(.el-input){height:52px;font-size:17px}
.tvd-reading-row :deep(.el-input__wrapper){padding:1px 10px;border-radius:7px 0 0 7px;box-shadow:0 0 0 1.5px #acb3b5 inset;background:#fff}
.tvd-reading-row :deep(.el-input:not(.el-input-group) .el-input__wrapper){border-radius:7px}
.tvd-reading-row :deep(.el-input__inner){min-width:0;text-align:right;color:#111;-moz-appearance:textfield}
.tvd-reading-row :deep(input::-webkit-inner-spin-button),.tvd-reading-row :deep(input::-webkit-outer-spin-button){-webkit-appearance:none;margin:0}
.tvd-reading-row :deep(.el-input-group__append){padding:0 9px;background:white;color:#111;box-shadow:0 0 0 1.5px #acb3b5 inset;border-radius:0 7px 7px 0}
.tvd-output{display:flex;align-items:center;justify-content:center;min-height:52px;padding:4px;border:1.5px solid #acb3b5;border-radius:7px;background:#e9e9e9;font-size:17px;overflow-wrap:anywhere}
.tvd-row-actions{display:flex;gap:12px;margin:20px 0 12px}
.tvd-row-actions .el-button+.el-button{margin-left:0}
.tvd-group-conditions{margin-top:18px;font-size:13px;color:#5b6e75}
.tvd-group-conditions summary{cursor:pointer}
.tvd-group-conditions label{display:inline-flex;flex-direction:column;gap:6px;width:calc(33.333% - 12px);margin:12px 12px 0 0}
@media(max-width:850px){.tvd-column-headings{font-size:15px}.tvd-group-conditions label{width:100%}}
</style>
