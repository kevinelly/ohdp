<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
const props=defineProps<{token:string}>()
const emit=defineEmits<{open:[{id:number;modality:'CT'|'DR'|'DOSE'}]}>()
const blank=()=>({modalities:[] as string[],year:undefined as number|undefined,from:'',to:'',city:'',county:'',receipt:'',company:'',status:''})
const filters=reactive(blank()), rows=ref<any[]>([]), page=ref(1), total=ref(0), busy=ref(false)
const statuses:Record<string,string>={DRAFT:'草稿',DATA_ENTERED:'已录入',CALCULATED:'已计算',PENDING_REVIEW:'待复核',REVIEWED:'待签发',APPROVED:'已签发',REPORTED:'已出报告'}
async function load(reset=false){if(reset)page.value=1;if(filters.from&&filters.to&&filters.to<filters.from){ElMessage.warning('结束日期不能早于开始日期');return}busy.value=true;try{const r=await axios.get('/api/inspection-query',{baseURL:import.meta.env.VITE_API_BASE_URL||'/',headers:{Authorization:`Bearer ${props.token}`},params:{...filters,year:filters.year||undefined,from:filters.from||undefined,to:filters.to||undefined,modalities:filters.modalities.join(',')||undefined,page:page.value-1,size:20}});rows.value=r.data.content;total.value=r.data.totalElements}catch(e:any){ElMessage.error(e.response?.data?.message||'综合查询失败')}finally{busy.value=false}}
function reset(){Object.assign(filters,blank());void load(true)}
onMounted(()=>load())
</script>
<template>
<div v-loading="busy">
  <el-form label-position="top" class="query-fields">
    <el-form-item label="检测类别"><el-select v-model="filters.modalities" multiple clearable placeholder="全部类别"><el-option label="CT" value="CT"/><el-option label="DR" value="DR"/><el-option label="个人剂量监测" value="DOSE"/></el-select></el-form-item>
    <el-form-item label="检测年份"><el-input-number v-model="filters.year" :min="1900" :max="2200"/></el-form-item>
    <el-form-item label="检测开始日期"><el-date-picker v-model="filters.from" value-format="YYYY-MM-DD"/></el-form-item>
    <el-form-item label="检测结束日期"><el-date-picker v-model="filters.to" value-format="YYYY-MM-DD"/></el-form-item>
    <el-form-item label="报告状态"><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="(v,k) in statuses" :key="k" :label="v" :value="k"/></el-select></el-form-item>
    <el-form-item v-for="[key,label] in [['city','所在地市'],['county','所在县区'],['receipt','受理编号'],['company','受检单位']]" :key="key" :label="label"><el-input v-model="filters[key as 'city'|'county'|'receipt'|'company']" clearable @keyup.enter="load(true)"/></el-form-item>
  </el-form>
  <el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button>
  <el-table :data="rows" border style="margin-top:16px"><el-table-column type="index" label="序号" width="60"/><el-table-column label="检测类别" width="130"><template #default="{row}">{{row.modality==='DOSE'?'个人剂量监测':row.modality}}</template></el-table-column><el-table-column prop="receipt" label="受理编号" min-width="165"/><el-table-column prop="unitName" label="受检单位" min-width="180"/><el-table-column prop="equipmentNo" label="设备编号" min-width="130"/><el-table-column prop="city" label="地市"/><el-table-column prop="county" label="县区"/><el-table-column prop="inspectionDate" label="检测日期" width="120"/><el-table-column label="报告状态" width="110"><template #default="{row}">{{statuses[row.status]}}</template></el-table-column><el-table-column prop="generatedAt" label="出具报告时间" width="200"/><el-table-column label="操作" width="80" fixed="right"><template #default="{row}"><el-button link type="primary" @click="emit('open',row)">查看</el-button></template></el-table-column></el-table>
  <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next,total" @current-change="load()"/>
</div>
</template>
<style scoped>.query-fields{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:0 16px}.query-fields :deep(.el-date-editor),.query-fields :deep(.el-input-number){width:100%}.el-pagination{margin-top:16px}</style>
