<script setup lang="ts">
import { computed } from 'vue'
type Field={code:string;label:string;unit:string;required:boolean;role:'RAW'|'INTERMEDIATE'|'FINAL';sortNo:number}
const props=defineProps<{code:string;fields:Field[];results?:{fieldCode:string;output:string}[]}>()
const layout=computed(()=>{
 const c=props.code
 const groups:{label:string;codes:string[]}[]=[]
 const add=(label:string,codes:string[])=>groups.push({label,codes})
 if(['TVD','ETD'].includes(c))for(let n=1;n<=3;n++)add('第'+n+'组',[`${c}${n}-0`,...Array.from({length:3},(_,i)=>`${c}${n}-${i+1}`),`${c}-K${n}`])
 else if(c==='ROQ')for(let n=1;n<=2;n++)add('第'+n+'档',[`I${n}`,`T${n}`,...Array.from({length:5},(_,i)=>`ROQ${n}-${i+1}`)])
 else if(c==='ARS')for(let n=1;n<=2;n++)add(n===1?'20mm铝板':'20mm铝板＋1.5mm铜板',Array.from({length:5},(_,i)=>`ARS${n}-${i+1}`))
 else if(c==='AIC')for(const [key,label] of [['C','中央'],['L','左侧'],['R','右侧']])add(label!,[1,2,3].map(i=>`AIC-${key}${i}`))
 else if(c==='LRD')for(const [key,label] of [['a1','左'],['a2','右'],['b1','前'],['b2','后']])add(label!,[`LRD-${key}-0`,`LRD-${key}-1`])
 else if(c==='REA'){add('水平方向',['REA-at','REA-am']);add('垂直方向',['REA-bt','REA-bm'])}
 else if(c==='STP')for(let n=1;n<=5;n++)add('第'+n+'档',[`STP-K${n}`,`STP-R${n}`])
 else if(c==='TPA'){add('进床',['Dfor','Lfor','Cfor']);add('出床',['Dback','Lback','Cback'])}
 else if(c==='CTDI')for(const [part,name] of [['H','头部模体'],['S','体部模体']])for(const [pos,label] of [['C','中心'],['U','上'],['D','下'],['L','左'],['R','右']])add(name+' / '+label,[1,2,3].map(i=>`${part}${pos}D${i}`))
 else if(c==='HUL')for(const [mat,label] of [['Air','空气'],['LDPE','低密度聚乙烯'],['Acylic','丙烯酸'],['Teflon','聚四氟乙烯']])add(label!,[1,2,3].map(i=>`${mat}-HU${i}`).concat(`${mat}-REF`))
 else if(c==='HUU')for(const [pos,label] of [['U','上'],['D','下'],['L','左'],['R','右']])add(label!,[1,2,3].map(i=>`HUU-${pos}${i}`))
 else if(c==='RPD'){add('本底读数1—5',[1,2,3,4,5].map(i=>`RPD0-${i}`));add('本底读数6—10',[6,7,8,9,10].map(i=>`RPD0-${i}`))}
 else add('测量值',props.fields.map(f=>f.code))
 const used=new Set(groups.flatMap(g=>g.codes));const extra=props.fields.filter(f=>!used.has(f.code))
 const rows=groups.map(g=>({label:g.label,fields:g.codes.map(code=>props.fields.find(f=>f.code===code)).filter((f):f is Field=>!!f)})).filter(g=>g.fields.length)
 return {rows,extra}
})
function heading(f:Field,i:number){
 if(['TVD','ETD'].includes(props.code))return i===0?(props.code==='TVD'?'预设值':'指示值T0'):i===4?'校准因子':'读数'+i
 if(props.code==='ARS'||props.code==='AIC'||props.code==='CTDI'||props.code==='HUU')return '读数'+(i+1)
 if(props.code==='HUL')return i===3?'模体标称CT值':'读数'+(i+1)
 if(props.code==='STP')return i===0?'空气比释动能':'像素值'
 if(props.code==='REA'||props.code==='LRD')return i===0?'标称值':'实测值'
 if(props.code==='TPA')return ['诊断床移动示值D','起始点L','终止点C'][i]
 if(/^ROA[1-5]$|^ARP[1-5]$/.test(f.code))return '读数K'+f.code.slice(-1)
 if(/^HVL[1-3]$|^HUA[1-3]$|^NPS[1-3]$|^DDI[1-3]$/.test(f.code))return '读数'+f.code.slice(-1)
 if(/^RUE[1-5]$/.test(f.code))return 'ROI'+f.code.slice(-1)
 return f.label
}
function calculated(row:{fields:Field[]},index:number,mean:boolean){
 const code=props.code,first=row.fields[0]?.code||'';let key=mean?'MEAN':'RESULT'
 if(['TVD','ETD'].includes(code))key=(mean?'MEAN_':code==='TVD'?'DISPLAY_':'DEVIATION_MS_')+(index+1)
 else if(['ROQ','ARS'].includes(code))key=mean?'MEAN_'+(index+1):'RESULT'
 else if(code==='CTDI'){const part=first.startsWith('H')?'HEAD':'BODY',pos=first[1];key=mean?`${part}_MEAN_${pos}D`:pos==='C'?`${part}_CENTER`:`${part}_${pos}_CTDI100`}
 else if(code==='HUL')key=first.split('-')[0]!.toUpperCase()+(mean?'_MEAN':'_DEVIATION')
 else if(code==='HUU')key=mean?'MEAN_'+first.split('-')[1]?.[0]:'RESULT'
 else if(code==='LRD')key=mean?'':`RESULT_${first.split('-')[1]!.toUpperCase()}`
 else if(code==='REA')key=mean?'':index===0?'HORIZONTAL':'VERTICAL'
 else if(code==='AIC')key=mean?['CENTER_MEAN','LEFT_MEAN','RIGHT_MEAN'][index]!:''
 else if(code==='TPA')key=mean?'':index===0?'FORWARD':'BACKWARD'
 else if(code==='STP')return '—'
 return props.results?.find(r=>r.fieldCode===key)?.output||'—'
}
</script>
<template>
 <div class="measurement-table-scroll"><table class="measurement-table"><thead><tr><th>测量组</th><th v-for="(f,i) in layout.rows[0]?.fields || []" :key="f.code">{{heading(f,i)}}</th><th v-if="results">{{['TVD','ETD','ROA','ROQ'].includes(code)?'校正平均值':'平均值'}}</th><th v-if="results">{{code==='CTDI'?'CTDI100':'检测结果'}}</th></tr></thead><tbody><tr v-for="(row,index) in layout.rows" :key="row.label"><th>{{row.label}}</th><td v-for="f in row.fields" :key="f.code"><slot :field="f"/></td><td v-if="results" class="calculated">{{calculated(row,index,true)}}</td><td v-if="results" class="calculated">{{calculated(row,index,false)}}</td></tr></tbody></table></div>
 <div v-if="layout.extra.length" class="measurement-extra"><label v-for="f in layout.extra" :key="f.code"><span>{{f.label}}</span><slot :field="f"/></label></div>
</template>
<style scoped>.measurement-table td.calculated{background:#ddd;text-align:center}</style>
<style scoped>.measurement-table-scroll{overflow:auto;margin:18px 0}.measurement-table{border-collapse:collapse;min-width:100%;table-layout:fixed}.measurement-table th,.measurement-table td{border:1px solid #aaa;padding:8px;min-width:120px}.measurement-table th{background:#d4d4d4;font-weight:500}.measurement-table td{background:white}.measurement-extra{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.measurement-extra label>span{display:block;margin-bottom:6px;font-size:13px}</style>
