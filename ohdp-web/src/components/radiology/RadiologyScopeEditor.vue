<script setup lang="ts">
import type {ItemScope,ScopeProfile} from './radiologyScopeSupport'
const props=defineProps<{modality:'DR'|'CT';profile:ScopeProfile;scopes:ItemScope[];names:Record<string,string>;editable:boolean;busy:boolean;dirty:boolean}>()
const emit=defineEmits<{'update:profile':[ScopeProfile];confirm:[]}>()
const groups:Record<string,string>={GENERAL:'X射线摄影设备通用检测项目',SPECIAL:'数字X射线摄影（DR）设备专用检测项目',QC:'CT质量控制检测项目',PROTECTION:'放射工作场所卫生防护检测'}
function choices(){return props.profile.itemApplicability || Object.fromEntries(props.scopes.filter(s=>s.modelDependent).map(s=>[s.itemCode,s.pending?null:s.applicable]))}
function reasons(){return {...Object.fromEntries(props.scopes.filter(s=>s.modelDependent&&!s.pending&&!s.applicable).map(s=>[s.itemCode,s.reason])),...props.profile.itemExclusionReasons}}
function choose(code:string,value:boolean|string){emit('update:profile',{...props.profile,itemApplicability:{...choices(),[code]:value==='PENDING'?null:Boolean(value)},itemExclusionReasons:reasons()})}
function choice(scope:ItemScope){return props.profile.itemApplicability?props.profile.itemApplicability[scope.itemCode]??null:scope.pending?null:scope.applicable}
function reason(code:string,value:string){emit('update:profile',{...props.profile,itemApplicability:choices(),itemExclusionReasons:{...reasons(),[code]:value}})}
</script>
<template>
<section class="scope-selection" aria-label="本次检测范围与适用条件">
 <header><h4>检测项目</h4><el-button :disabled="!editable||busy" :loading="busy" @click="emit('confirm')">保存并确认检测范围</el-button></header>
 <el-checkbox-group :model-value="profile.selectedGroups || []" :disabled="!editable" @update:model-value="(value:any)=>emit('update:profile',{...profile,selectedGroups:value})"><el-checkbox v-for="group in modality==='DR'?['GENERAL','SPECIAL','PROTECTION']:['QC','PROTECTION']" :key="group" :value="group">{{groups[group]}}</el-checkbox></el-checkbox-group>
 <p v-if="profile.scopeConfirmed===false || dirty" class="scope-notice">请确认本次范围及机型适用条件，保存后按所选项目开始录入。</p>
 <p v-else class="scope-note">按所选检测项目跳转至对应类别；不开展项目保留名称及原因，不要求录入。</p>
 <div v-if="modality==='DR' && scopes.some(s=>s.selected&&s.modelDependent)" class="scope-capabilities">
  <h4>依据设备实际机型确认是否检测</h4>
  <div v-for="scope in scopes.filter(s=>s.selected&&s.modelDependent)" :key="scope.itemCode" class="scope-capability"><label :for="'capability-'+scope.itemCode">{{names[scope.itemCode]}}</label><el-select :id="'capability-'+scope.itemCode" :model-value="choice(scope) ?? 'PENDING'" :disabled="!editable" :aria-label="names[scope.itemCode]+'适用性'" @update:model-value="(value:boolean|string)=>choose(scope.itemCode,value)"><el-option label="待确认" value="PENDING"/><el-option label="开展检测" :value="true"/><el-option label="不开展" :value="false"/></el-select><el-input v-if="choice(scope)===false" :model-value="profile.itemExclusionReasons?.[scope.itemCode] || ''" :disabled="!editable" :aria-label="names[scope.itemCode]+'不开展原因'" placeholder="填写机型依据或不开展原因" @update:model-value="(value:string)=>reason(scope.itemCode,value)"/><span v-if="scope.reason" class="scope-reason">{{scope.reason}}</span></div>
 </div>
</section>
</template>
<style scoped>
.scope-selection{padding:18px 20px;border:1px solid #cbd8dc;border-radius:10px;margin:12px 0 18px;background:#f7fafb}.scope-selection header{display:flex;align-items:center;justify-content:space-between;gap:12px}.scope-selection h4{margin:0 0 10px}.scope-selection :deep(.el-checkbox-group){display:flex;flex-direction:column;align-items:flex-start;margin:10px 0;gap:8px}.scope-selection :deep(.el-checkbox){height:auto;white-space:normal}.scope-notice{color:#87570e}.scope-note,.scope-reason{font-size:13px;color:#526878}.scope-capabilities{border-top:1px solid #d1dfe3;padding-top:14px}.scope-capability{display:grid;grid-template-columns:minmax(180px,1fr) 140px minmax(180px,1fr);gap:8px;align-items:center;margin:8px 0}.scope-reason{grid-column:1/-1}.scope-capability>label{font-size:14px}@media(max-width:900px){.scope-capability{grid-template-columns:1fr 140px}.scope-capability>.el-input{grid-column:1/-1}}@media(max-width:600px){.scope-selection header{display:block}.scope-capability{grid-template-columns:1fr}}
</style>
<style scoped>.scope-selection :deep(.el-checkbox){flex-direction:row;align-items:center;justify-content:flex-start;margin-right:0}.scope-selection :deep(.el-checkbox__label){white-space:normal;line-height:1.6}</style>
