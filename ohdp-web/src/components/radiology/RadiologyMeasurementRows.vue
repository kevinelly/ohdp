<script setup lang="ts">
import { computed } from 'vue'
interface Field { code: string; label: string; unit: string; required: boolean }
const props=defineProps<{code:string;fields:Field[];values:Record<string,string|number>;conditions:Record<string,string>;disabled:boolean}>()
const rows=computed<number[]>(()=>JSON.parse(props.conditions['__rows.'+props.code] || '[]'))
const fields=computed(()=>props.fields.filter(f=>!['TVD','ETD'].includes(props.code) || f.code.startsWith(props.code+'1-') || f.code===props.code+'-K1'))
const key=(id:number,field:string)=>`${props.code}::R${id}__${field}`
function label(f:Field) {
  if(['TVD','ETD'].includes(props.code)) {
    if(f.code.endsWith('-0'))return props.code==='TVD'?'标称管电压':'标称曝光时间'
    if(f.code.includes('-K'))return '校准因子'
    return '第'+f.code.slice(-1)+'次读数'
  }
  return f.label
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
function add() {props.conditions['__rows.'+props.code]=JSON.stringify([...rows.value,Math.max(...rows.value)+1])}
function remove(id:number) {
  props.conditions['__rows.'+props.code]=JSON.stringify(rows.value.filter(r=>r!==id))
  delete props.conditions[`${props.code}.R${id}`]
  for(const field of fields.value) delete props.values[key(id,field.code)]
}
</script>
<template>
  <div class="measurement-rows">
    <el-button v-if="!rows.length" :disabled="disabled" @click="enable">按行录入及增删测量行</el-button>
    <template v-else>
      <div class="rows-toolbar"><strong>测量条件行</strong><span>每行独立计算和评价，最多30行</span><el-button :disabled="disabled || rows.length>=30 || Math.max(...rows)>=999" @click="add">增加行</el-button></div>
      <section v-for="id in rows" :key="id" class="measurement-row">
        <header><strong>第{{id}}行</strong><el-button type="danger" link :disabled="disabled || rows.length===1" @click="remove(id)">删除第{{id}}行</el-button></header>
        <el-input v-model="conditions[`${code}.R${id}`]" :disabled="disabled" :aria-label="`${code}第${id}行检测条件`" placeholder="本行检测条件，留空使用上方共有条件" />
        <div class="row-fields"><label v-for="f in fields" :key="f.code"><span>{{label(f)}} <em v-if="f.required">*</em></span>
          <el-select v-if="f.code==='LCR-BASELINE-UNIT'" v-model="values[key(id,f.code)]" :disabled="disabled" :aria-label="`${code}第${id}行${f.label}`"><el-option value="个" label="个"/></el-select>
          <el-input v-else v-model="values[key(id,f.code)]" type="number" :disabled="disabled" :aria-label="`${code}第${id}行${f.label}`"><template v-if="f.unit" #append>{{f.unit}}</template></el-input>
        </label></div>
      </section>
    </template>
  </div>
</template>
<style scoped>
.measurement-rows{margin:16px 0}.rows-toolbar,.measurement-row header{display:flex;align-items:center;gap:16px;margin-bottom:12px}.rows-toolbar span{font-size:13px;color:#667}.measurement-row{padding:16px;border:1px solid #cddadf;border-radius:6px;margin:12px 0}.measurement-row header{justify-content:space-between}.row-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;margin-top:14px}.row-fields label span{display:block;margin-bottom:6px;font-size:13px}.row-fields em{color:#ba3027}
</style>
