<script setup lang="ts">
import { computed } from 'vue'
import RadiologyConditionEditor from './RadiologyConditionEditor.vue'
import HelpMark from '../help/HelpMark.vue'
import { pptFieldName } from './radiologyDraftSupport'
type Field={code:string;label:string;unit:string;required:boolean;role:'RAW'|'INTERMEDIATE'|'FINAL';sortNo:number}
const props=defineProps<{code:string;fields:Field[];conditions?:Record<string,string>;disabled?:boolean;hideReferences?:boolean;results?:{fieldCode:string;output:string}[]}>()
const referenceCodes:Record<string,string[]>={DDI:['DDI-CALCULATED','DDI-BASELINE'],STP:['STP-MODEL'],HCR:['HCR-MANUFACTURER','HCR-NYQUIST','HCR-BASELINE','厂家规定值/基线值'],RST:['RST-FACTOR'],LCR:['厂家规定值/基线值','LCR-BASELINE-UNIT'],RPD:['k','a','RPD-k2','RPD-mA'],NPS:['NPS-n','NPS-BASELINE'],MTF:['MTF-FACTOR'],ROA:['ROA-K'],ROQ:['ROQ-K']}
const referenceFields=computed(()=>(referenceCodes[props.code]||[]).map(code=>props.fields.find(f=>f.code===code)).filter((f):f is Field=>!!f))
const supplementalReferences=computed(()=>referenceFields.value.filter(f=>props.code==='MTF'||props.code==='HCR'&&f.code==='厂家规定值/基线值'||props.code==='NPS'&&f.code==='NPS-BASELINE'||props.code==='ROQ'))
const primaryReferences=computed(()=>referenceFields.value.filter(f=>!supplementalReferences.value.includes(f)))
const layout=computed(()=>{
 const c=props.code
 const groups:{label:string;codes:string[]}[]=[]
 const add=(label:string,codes:string[])=>groups.push({label,codes})
 if(['TVD','ETD'].includes(c))for(let n=1;n<=3;n++)add('第'+n+'组',[`${c}${n}-0`,...Array.from({length:3},(_,i)=>`${c}${n}-${i+1}`),`${c}-K${n}`])
 else if(c==='ROQ')for(let n=1;n<=2;n++)add('第'+n+'档',[`I${n}`,`T${n}`,...Array.from({length:5},(_,i)=>`ROQ${n}-${i+1}`)])
 else if(c==='ARS')for(let n=1;n<=2;n++)add(n===1?'20mm铝板':'20mm铝板＋1.5mm铜板',Array.from({length:5},(_,i)=>`ARS${n}-${i+1}`))
 else if(c==='AIC')for(const [key,label] of [['C','中央'],['L','左侧'],['R','右侧']])add(label!,[1,2,3].map(i=>`AIC-${key}${i}`))
 else if(c==='LRD')for(const [key,label] of [['a1','左'],['a2','右'],['b1','前'],['b2','后']])add(label+key,[`LRD-${key}-0`,`LRD-${key}-1`])
 else if(c==='REA'){add('水平方向',['REA-at','REA-am']);add('垂直方向',['REA-bt','REA-bm'])}
 else if(c==='RUE')for(let n=1;n<=5;n++)add(['中央区','左上象限','左下象限','右上象限','右下象限'][n-1]!,['RUE'+n])
 else if(c==='LCD')for(const [suffix,label] of [['1','1.0%'],['05','0.5%'],['03','0.3%']])add(label!,['LCD-COUNT-'+suffix])
 else if(c==='STP')for(let n=1;n<=5;n++)add([1,5,10,20,30][n-1]+'档',[`STP-K${n}`,`STP-R${n}`])
 else if(c==='TPA'){add('连续前进for',['Dfor','Lfor','Cfor']);add('连续后退back',['Dback','Lback','Cback'])}
 else if(c==='RST')add('测量值',['CTbackground','CTmax','R1','R2','R3','R4','R0'])
 else if(c==='CTDI')for(const [part,name] of [['H','头部模体'],['S','体部模体']])for(const [pos,label] of [['C','中心'],['U','上'],['D','下'],['L','左'],['R','右']])add(name+' / '+label,[1,2,3].map(i=>`${part}${pos}D${i}`))
 else if(c==='HUL')for(const [mat,label] of [['Air','空气'],['LDPE','LDPE（低密度聚乙烯）'],['Acylic','Acrylic（丙烯酸）'],['Teflon','Teflon（聚四氟乙烯）']])add(label!,[`${mat}-REF`,...[1,2,3].map(i=>`${mat}-HU${i}`)])
 else if(c==='HUA')add('中心',[1,2,3].map(i=>'HUA'+i))
 else if(c==='HUU')for(const [pos,label] of [['U','上'],['D','下'],['L','左'],['R','右']])add(label!,[1,2,3].map(i=>`HUU-${pos}${i}`))
 else if(c==='RPD'){add('本底读数1—5',[1,2,3,4,5].map(i=>`RPD0-${i}`));add('本底读数6—10',[6,7,8,9,10].map(i=>`RPD0-${i}`))}
 else add('测量值',props.fields.filter(f=>!referenceFields.value.some(ref=>ref.code===f.code)).map(f=>f.code))
 const used=new Set([...groups.flatMap(g=>g.codes),...referenceFields.value.map(f=>f.code)]);const extra=props.fields.filter(f=>!used.has(f.code))
 const rows=groups.map(g=>({label:g.label,fields:g.codes.map(code=>props.fields.find(f=>f.code===code)).filter((f):f is Field=>!!f)})).filter(g=>g.fields.length)
 return {rows,extra}
})
function heading(f:Field,i:number){
 if(['TVD','ETD'].includes(props.code))return i===0?(props.code==='TVD'?'预设值':'指示值T0'):i===4?'校准因子':(props.code==='ETD'?'读数T':'读数')+i
 if(props.code==='ARS'||props.code==='AIC'||props.code==='CTDI'||props.code==='HUU')return '读数'+(i+1)
 if(props.code==='HUL')return f.code.endsWith('-REF')?'标称值':'读数'+f.code.slice(-1)
 if(props.code==='STP')return i===0?'入射空气比释动能K':'平均像素值PV'
 if(props.code==='RUE')return '像素值PV'
 if(props.code==='LRD')return i===0?'光野':'照射野'
 if(props.code==='REA')return i===0?'标称值':'实测值'
 if(props.code==='TPA')return ['诊断床移动示值D','起始点L','终止点C'][i]
 if(/^ROA[1-5]$|^ARP[1-5]$/.test(f.code))return '读数K'+f.code.slice(-1)
 if(/^HVL[1-3]$|^HUA[1-3]$|^NPS[1-3]$|^DDI[1-3]$/.test(f.code))return '读数'+f.code.slice(-1)
 if(/^RUE[1-5]$/.test(f.code))return 'ROI'+f.code.slice(-1)
 return pptFieldName(props.code,f.code,f.label)
}
function calculated(row:{fields:Field[]},index:number,mean:boolean){
 const code=props.code,first=row.fields[0]?.code||'';let key=mean?'MEAN':'RESULT'
 if(['TVD','ETD'].includes(code))key=(mean?'MEAN_':code==='TVD'?'DISPLAY_':'DEVIATION_MS_')+(index+1)
 else if(['ROQ','ARS'].includes(code))key=mean?'MEAN_'+(index+1):code==='ARS'?'DEVIATION_'+(index+1):'RESULT'
 else if(code==='CTDI'){const part=first.startsWith('H')?'HEAD':'BODY',pos=first[1];key=mean?`${part}_MEAN_${pos}D`:pos==='C'?`${part}_CENTER`:`${part}_${pos}_CTDI100`}
 else if(['HUA','HVL'].includes(code))key='RESULT'
 else if(code==='HUL')key=first.split('-')[0]!.toUpperCase()+(mean?'_MEAN':'_DEVIATION')
 else if(code==='HUU')key=(mean?'MEAN_':'DEVIATION_')+first.split('-')[1]?.[0]
 else if(code==='LRD')key=mean?'':`RESULT_${first.split('-')[1]!.toUpperCase()}`
 else if(code==='REA')key=mean?'':index===0?'HORIZONTAL':'VERTICAL'
 else if(code==='AIC')key=['CENTER','LEFT','RIGHT'][index]+(mean?'_MEAN':'_DEVIATION')
 else if(code==='RUE')key=mean?'':'DOSE_'+(index+1)
 else if(code==='TPA')key=mean?'':index===0?'FORWARD':'BACKWARD'
 else if(['ART','ILA'].includes(code))key='RESULT_TEXT'
 else if(code==='STP')return '—'
 return props.results?.find(r=>r.fieldCode===key)?.output||'—'
}
function setCondition(field:string,index:number,value:string){if(props.conditions)props.conditions['STP.'+field+'.'+(index+1)]=value}
function output(key:string){return props.results?.find(r=>r.fieldCode===key)?.output||'—'}
const showRowHeading=computed(()=>['STP','HUL','HUA','HUU','TPA','REA','CTDI'].includes(props.code))
const showMean=computed(()=>['TVD','ETD','ROA','ROQ','ARP','HVL','HUL','HUA','HUU','NPS','DDI'].includes(props.code))
const meanHeading=computed(()=>['ROA','ETD','ROQ'].includes(props.code)?'平均值':props.code==='DDI'?'平均值（DDI测量值）':props.code==='TVD'?'校正平均值':props.code==='HUA'?'平均值CTC':'平均值')
const resultHeading=computed(()=>({LRD:'偏离值',REA:'误差',ROA:'重复性CV',ARP:'重复性CV',HUL:'平均值−标称值',HUA:'水的CT值',HUU:'CTC−CTP',NPS:'噪声值',UBD:'偏离度',TPA:'定位/归位误差'} as Record<string,string>)[props.code]||'检测结果')
const showResult=computed(()=>!['STP','ROQ'].includes(props.code))
const ctdiParts=computed(()=>[['HEAD','头部模体','H','1'],['BODY','体部模体','S','2']].filter(part=>props.fields.some(f=>f.code===part[2]+'CD1')))
function partParameters(part:string[]){return layout.value.extra.filter(f=>f.code.startsWith('CTDI-'+part[0])||['层数n','层厚T','刻度系数k'].some(prefix=>f.code===prefix+part[3]))}
function partField(part:string[],prefix:string){return props.fields.find(f=>f.code===prefix+part[3])}
function setPartCondition(part:string[],value:string,parameters=false){if(props.conditions)props.conditions[(parameters?'__params.':'')+'CTDI.'+part[3]]=value}
const models=[['LINEAR','线性响应','PV=aK+b'],['LOG','对数相关','PV=a ln(K)+b'],['POWER','幂相关','PV=aKᵇ（c=0）']]
function rowFields(row:{fields:Field[]}){return props.code==='ETD'?row.fields.filter(f=>!f.code.startsWith('ETD-K')):row.fields}
function factorField(row:{fields:Field[]}){return row.fields.find(f=>f.code.startsWith('ETD-K'))}
function field(code:string){return props.fields.find(f=>f.code===code)}
function selectedFormula(){
 const model=output('SELECTED_MODEL');if(model==='—')return '—'
 const a=output(model+'_A'),b=output(model+(model==='POWER'?'_B':'_C'))
 return model==='POWER'?`PV=${a}·K^${b}`:model==='LOG'?`PV=${a}·ln(K)+${b}`:`PV=${a}·K+${b}`
}
function setLcrCondition(value:string,parameters=false){if(props.conditions)props.conditions[parameters?'__params.LCR':'LCR']=value}
</script>
<template>
 <RadiologyConditionEditor v-if="code==='LCR'" item-code="LCR" compact-only hide-reference :disabled="disabled" :model-value="conditions?.LCR" :parameters="conditions?.['__params.LCR']" @update:model-value="setLcrCondition($event)" @update:parameters="setLcrCondition($event,true)"><template #before-reference><label v-for="f in referenceFields" :key="f.code"><span>{{pptFieldName(code,f.code,f.label)}}</span><slot :field="f"/></label></template></RadiologyConditionEditor>
 <details v-else-if="code==='ROA' && referenceFields.length" class="roa-calibration-detail"><summary>补充参数（校准因子）</summary><div class="measurement-extra"><label v-for="f in referenceFields" :key="f.code"><span>{{pptFieldName(code,f.code,f.label)}}</span><slot :field="f"/></label></div></details>
 <div v-else-if="primaryReferences.length && !hideReferences" class="measurement-extra"><label v-for="f in primaryReferences" :key="f.code"><span>{{pptFieldName(code,f.code,f.label)}}</span><slot :field="f"/></label></div>
 <details v-if="supplementalReferences.length && !hideReferences" class="roa-calibration-detail"><summary>补充参数与历史参考值</summary><div class="measurement-extra"><label v-for="f in supplementalReferences" :key="f.code"><span>{{pptFieldName(code,f.code,f.label)}}</span><slot :field="f"/></label></div></details>
 <div v-if="code==='UBD'" class="measurement-table-scroll"><table class="measurement-table ubd-table"><thead><tr><th>读数</th><th>偏离度 <HelpMark anchor-id="radiology.calculation"/></th></tr></thead><tbody><tr><td><div class="qualified-reading"><slot v-if="field('UBD-QUALIFIER')" :field="field('UBD-QUALIFIER')!"/><slot v-if="field('UBD')" :field="field('UBD')!"/></div></td><td class="calculated">{{output('RESULT')}}</td></tr></tbody></table></div>
 <div v-else-if="code==='ARS'" class="measurement-table-scroll"><table class="measurement-table compact-table" :data-item="code"><thead><tr><th>空气比释动能（μGy）</th><th v-for="n in 5" :key="n">N{{n}}</th><th>平均值</th><th>总平均值</th><th>相对偏差</th><th>检测结果 <HelpMark anchor-id="radiology.calculation"/></th></tr></thead><tbody><tr v-for="(row,index) in layout.rows" :key="row.label"><th>{{row.label}}</th><td v-for="f in row.fields" :key="f.code"><slot :field="f"/></td><td class="calculated">{{calculated(row,index,true)}}</td><td v-if="index===0" :rowspan="layout.rows.length" class="calculated">{{output('OVERALL_MEAN')}}</td><td class="calculated">{{calculated(row,index,false)}}</td><td v-if="index===0" :rowspan="layout.rows.length" class="calculated">{{output('RESULT')}}</td></tr></tbody></table></div>
 <div v-else-if="code==='AIC'" class="measurement-table-scroll"><table class="measurement-table chamber-table"><thead><tr><th>电离室位置</th><th v-for="row in layout.rows" :key="row.label">{{row.label}}</th></tr></thead><tbody><tr><th>测量读数</th><td v-for="row in layout.rows" :key="row.label"><div class="chamber-readings"><div v-for="(f,index) in row.fields" :key="f.code"><span class="reading-label">读数{{index+1}}</span><slot :field="f"/></div></div></td></tr><tr><th>平均值</th><td v-for="(row,index) in layout.rows" :key="row.label" class="calculated">{{calculated(row,index,true)}}</td></tr><tr><th>相对偏差</th><td v-for="(row,index) in layout.rows" :key="row.label" class="calculated">{{calculated(row,index,false)}}</td></tr><tr><th>电离室之间一致性</th><td class="calculated">总平均值：{{output('OVERALL_MEAN')}}</td><td colspan="2" class="calculated">最大相对偏差：{{output('RESULT')}}</td></tr></tbody></table></div>
 <div v-else-if="code==='RUE'" class="measurement-table-scroll"><table class="measurement-table compact-table" :data-item="code"><thead><tr><th>区域</th><th v-for="row in layout.rows" :key="row.label">{{row.label}}</th><th>平均值</th><th>变异系数 <HelpMark anchor-id="radiology.calculation"/></th></tr></thead><tbody><tr><th>像素值PV</th><td v-for="row in layout.rows" :key="row.label"><slot v-for="f in row.fields" :key="f.code" :field="f"/></td><td rowspan="2" class="calculated">{{output('MEAN')}}</td><td rowspan="2" class="calculated">{{output('RESULT')}}</td></tr><tr><th>剂量值V</th><td v-for="(row,index) in layout.rows" :key="row.label" class="calculated">{{calculated(row,index,false)}}</td></tr></tbody></table></div>
 <div v-else-if="code==='LRD'" class="measurement-table-scroll"><table class="measurement-table" :data-item="code"><thead><tr><th>方向</th><th v-for="row in layout.rows" :key="row.label">{{row.label}}</th></tr></thead><tbody><tr v-for="(label,i) in ['光野','照射野','偏离值']" :key="label"><th>{{label}}</th><td v-for="(row,index) in layout.rows" :key="row.label" :class="{calculated:i===2}"><slot v-if="i<2 && row.fields[i]" :field="row.fields[i]!"/><template v-else>{{calculated(row,index,false)}}</template></td></tr></tbody></table></div>
 <div v-else-if="code==='RST'" class="measurement-table-scroll"><table class="measurement-table" :data-item="code"><thead><tr><th>CTbackground</th><th>CTmax</th><th>最小窗宽WWmin</th><th>半高CT值CThm</th><th v-for="n in 4" :key="n">实测值{{n}}</th><th>平均值</th><th>标称值</th><th>偏差</th></tr></thead><tbody><tr><td v-for="key in ['CTbackground','CTmax']" :key="key"><slot v-if="field(key)" :field="field(key)!"/></td><td class="calculated">{{output('WWMIN')}}</td><td class="calculated">{{output('CTHM')}}</td><td v-for="n in 4" :key="n"><slot v-if="field('R'+n)" :field="field('R'+n)!"/></td><td class="calculated">{{output('MEAN')}}</td><td><slot v-if="field('R0')" :field="field('R0')!"/></td><td class="calculated">{{output('DEVIATION')}}</td></tr></tbody></table></div>
 <div v-else-if="code==='LCD'" class="measurement-table-scroll"><table class="measurement-table" :data-item="code"><thead><tr><th>标称对比度</th><th>可辨孔径数</th><th>最小直径（mm）</th><th>对比度计算值</th><th>平均值（mm）</th><th>检测结果（mm）</th></tr></thead><tbody><tr v-for="(row,index) in layout.rows" :key="row.label"><th>{{row.label}}</th><td><slot v-for="f in row.fields" :key="f.code" :field="f"/></td><td class="calculated">{{output('DIAMETER_'+['1','05','03'][index])}}</td><td class="calculated">{{output('CAL_'+['1','05','03'][index])}}</td><td v-if="index===0" :rowspan="layout.rows.length" class="calculated">{{output('RESULT')}}</td><td v-if="index===0" :rowspan="layout.rows.length" class="calculated">{{output('RESULT')}}</td></tr></tbody></table></div>
 <section v-else-if="code==='CTDI'" class="ctdi-blocks"><section v-for="part in ctdiParts" :key="part[0]" class="ctdi-block"><h4>{{part[1]}}</h4><RadiologyConditionEditor item-code="CTDI" :disabled="disabled" :model-value="conditions?.['CTDI.'+part[3]] ?? conditions?.CTDI" :parameters="conditions?.['__params.CTDI.'+part[3]] ?? conditions?.['__params.CTDI']" @update:model-value="setPartCondition(part,$event)" @update:parameters="setPartCondition(part,$event,true)"><template #ctdi-layers><label><span>单次扫描段层数N</span><slot v-if="partField(part,'层数n')" :field="partField(part,'层数n')!"/></label></template><template #ctdi-thickness><label><span>层厚T（mm）</span><slot v-if="partField(part,'层厚T')" :field="partField(part,'层厚T')!"/></label></template><template #ctdi-factor><label><span>刻度系数k</span><slot v-if="partField(part,'刻度系数k')" :field="partField(part,'刻度系数k')!"/></label></template></RadiologyConditionEditor><div class="ctdi-parameters"><label v-for="f in partParameters(part).filter(f=>f.code.startsWith('CTDI-'))" :key="f.code"><span>{{pptFieldName(code,f.code,f.label)}}</span><slot :field="f"/></label></div><div class="measurement-table-scroll"><table class="measurement-table" :data-item="code"><thead><tr><th>测量位置</th><th v-for="n in 3" :key="n">读数{{n}}</th><th>平均值D</th></tr></thead><tbody><tr v-for="row in layout.rows.filter(r=>r.fields[0]?.code.startsWith(part[2]!))" :key="row.label"><th>{{row.label.split(' / ')[1]}}</th><td v-for="f in row.fields" :key="f.code"><slot :field="f"/></td><td class="calculated">{{calculated(row,0,true)}}</td></tr><tr><th>CTDI100，C</th><td colspan="2" class="calculated">{{output(part[0]+'_CENTER')}}</td><th>CTDI100，P</th><td class="calculated">{{output(part[0]+'_PERIPHERAL')}}</td></tr><tr><th>CTDIw（mGy）</th><td colspan="2" class="calculated">{{output(part[0]+'_CTDIW')}}</td><th>厂家值比较（%）</th><td class="calculated">{{output(part[0]+'_MANUFACTURER_DEVIATION')}}</td></tr></tbody></table></div></section></section>
 <div v-else class="measurement-table-scroll"><table class="measurement-table" :data-item="code"><thead><tr><th v-if="showRowHeading">{{code==='STP'?'参考剂量档':['LRD','REA'].includes(code)?'方向':code==='HUL'?'模体材料':code==='TPA'?'移动方向':['HUA','HUU'].includes(code)?'位置':'测量组'}}</th><th v-if="code==='LCR'">参考剂量</th><th v-if="code==='STP'">管电压（kV）</th><th v-for="(f,i) in rowFields(layout.rows[0] || {fields:[]})" :key="f.code">{{code==='RPD'?'读数':heading(f,i)}}</th><th v-if="code==='STP'">DDI计算值</th><th v-if="results && showMean">{{meanHeading}}</th><th v-if="code==='ETD'">校准因子</th><th v-if="results && showResult">{{resultHeading}} <HelpMark anchor-id="radiology.calculation"/></th><th v-if="['HUL','HUU'].includes(code)">{{code==='HUU'?'均匀性':'检测结果'}}</th></tr></thead><tbody><tr v-for="(row,index) in layout.rows" :key="row.label"><th v-if="showRowHeading">{{row.label}}</th><td v-if="code==='LCR'"><RadiologyConditionEditor item-code="LCR" reference-only :disabled="disabled" :model-value="conditions?.LCR" :parameters="conditions?.['__params.LCR']" @update:model-value="setLcrCondition($event)" @update:parameters="setLcrCondition($event,true)"/></td><td v-if="code==='STP'"><el-input :model-value="conditions?.['STP.voltage.'+(index+1)]" :disabled="disabled" type="number" :aria-label="row.label+'管电压'" @update:model-value="(v:string)=>setCondition('voltage',index,v)"/></td><td v-for="f in rowFields(row)" :key="f.code"><slot :field="f"/></td><td v-if="code==='STP'"><el-input :model-value="conditions?.['STP.ddi.'+(index+1)]" :disabled="disabled" type="number" :aria-label="row.label+'DDI计算值'" @update:model-value="(v:string)=>setCondition('ddi',index,v)"/></td><td v-if="results && showMean" class="calculated">{{calculated(row,index,true)}}</td><td v-if="code==='ETD'"><slot v-if="factorField(row)" :field="factorField(row)!"/></td><td v-if="results && showResult" class="calculated">{{calculated(row,index,false)}}</td><td v-if="['HUL','HUU'].includes(code) && index===0" :rowspan="layout.rows.length" class="calculated">{{output('RESULT')}}</td></tr></tbody></table></div>
 <details v-if="layout.extra.length && code!=='CTDI'" class="measurement-table-scroll" ><summary>补充参数与历史记录</summary><table class="measurement-table" :data-item="code"><tbody><tr v-for="f in layout.extra" :key="f.code"><th>{{pptFieldName(code,f.code,f.label)}}</th><td><slot :field="f"/></td></tr></tbody></table></details>

 <div v-if="code==='ROQ'" class="stp-summary"><label>线性<output>{{output('RESULT')}}</output></label></div>
 <div v-if="code==='STP'" class="stp-summary"><label>拟合公式<output>{{selectedFormula()}}</output></label><label>R²<output>{{output(output('SELECTED_MODEL')+'_R2')}}</output></label></div>
 <details v-if="code==='STP'" class="measurement-table-scroll"><summary>拟合系数与完整模型</summary><table class="measurement-table" :data-item="code"><thead><tr><th>拟合关系</th><th>拟合公式</th><th>a</th><th>b</th><th>c</th><th>R²</th></tr></thead><tbody><tr v-for="[key,label,formula] in models" :key="key"><th>{{label}}</th><td>{{formula}}</td><td v-for="suffix in ['A','B','C','R2']" :key="suffix" class="calculated">{{key!=='POWER'&&suffix==='B'?output(key+'_C'):key!=='POWER'&&suffix==='C'?'—':output(key+'_'+suffix)}}</td></tr><tr><th>采用的拟合关系</th><td colspan="5" class="calculated">{{models.find(m=>m[0]===output('SELECTED_MODEL'))?.[1]||output('SELECTED_MODEL')}}</td></tr></tbody></table></details>


</template>
<style scoped>.measurement-table td.calculated{background:#ddd;text-align:center}</style>
<style scoped>.measurement-table-scroll{overflow:auto;margin:18px 0}.measurement-table{border-collapse:collapse;min-width:100%;table-layout:fixed}.measurement-table th,.measurement-table td{border:1px solid #aaa;padding:8px;min-width:120px}.measurement-table th{background:#d4d4d4;font-weight:500}.measurement-table td{background:white}.measurement-extra{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.measurement-extra label>span{display:block;margin-bottom:6px;font-size:13px}</style>

<style scoped>.ctdi-parameters{display:grid;grid-template-columns:repeat(3,minmax(150px,1fr));gap:12px;margin:16px 0}.ctdi-parameters label>span{display:block;margin-bottom:6px;font-size:13px}</style>

<style scoped>.compact-table th,.compact-table td{min-width:85px;padding:6px}.chamber-readings{display:grid;grid-template-columns:repeat(3,minmax(95px,1fr));gap:6px}.reading-label{display:block;font-size:12px;color:#526278;margin-bottom:4px}.measurement-table-scroll{margin:12px 0}.measurement-table td.calculated{vertical-align:middle}</style>

<style scoped>.ctdi-block{border-top:1px solid #bbb;margin-top:12px}.ctdi-block h4{margin:12px 0 8px}</style>

<style scoped>.chamber-table{min-width:1120px;table-layout:auto}.chamber-table tr>th:first-child{width:130px}.chamber-readings{grid-template-columns:repeat(3,minmax(100px,1fr))}.measurement-table :deep(.el-input-group__append){padding:0 6px}.measurement-table :deep(.el-input__inner){min-width:42px}</style>

<style scoped>.ctdi-fields :deep(label){font-size:12px;color:#526278}.chamber-table{min-width:940px}.chamber-readings{grid-template-columns:repeat(3,minmax(82px,1fr))}.chamber-table tr>th:first-child{width:100px}.measurement-table :deep(.el-input__wrapper){padding:1px 6px}.measurement-table :deep(.el-input-group__append){padding:0 4px}.measurement-table-scroll>summary{cursor:pointer;font-size:13px;margin-bottom:8px}</style>

<style scoped>.rst-results{display:flex;flex-wrap:wrap;gap:12px;margin:12px 0;padding:12px;background:#eee}</style>
