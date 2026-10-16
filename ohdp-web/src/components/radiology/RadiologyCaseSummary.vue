<script setup lang="ts">
const props=defineProps<{modality:'CT'|'DR';caseData:{sampleAcceptanceNo?:string;inspectedUnitName?:string;detectionLocation?:string;detectionCategory?:string;equipmentName?:string;equipmentModel?:string;inspectionDate?:string;inspectionEndDate?:string;standardBasis?:string;instrumentDescription?:string;profile?:{commissionedUnit?:string}}}>()
</script>
<template>
  <dl class="ppt-case-summary" :class="modality.toLowerCase()">
    <div><dt>受理编号</dt><dd>{{props.caseData.sampleAcceptanceNo || '未填写'}}</dd></div>
    <template v-if="modality==='CT'">
      <div><dt>受检单位</dt><dd>{{props.caseData.inspectedUnitName || '未填写'}}</dd></div>
      <div><dt>检测地点</dt><dd>{{props.caseData.detectionLocation || '未填写'}}</dd></div>
      <div><dt>委托单位</dt><dd>{{props.caseData.profile?.commissionedUnit || '未填写'}}</dd></div>
      <div><dt>检测类别</dt><dd>{{props.caseData.detectionCategory || '未填写'}}</dd></div>
      <div class="date"><dt>检测日期</dt><dd>{{props.caseData.inspectionDate || '未填写'}} 至 {{props.caseData.inspectionEndDate || '未填写'}}</dd></div>
    </template>
    <template v-else>
      <div><dt>检测类别</dt><dd>{{props.caseData.detectionCategory || '未填写'}}</dd></div>
      <div class="date"><dt>检测日期</dt><dd>{{props.caseData.inspectionDate || '未填写'}} 至 {{props.caseData.inspectionEndDate || '未填写'}}</dd></div>
      <div><dt>受检单位名称</dt><dd>{{props.caseData.inspectedUnitName || '未填写'}}</dd></div>
      <div><dt>检测地点</dt><dd>{{props.caseData.detectionLocation || '未填写'}}</dd></div>
    </template>
    <div><dt>设备名称</dt><dd>{{props.caseData.equipmentName || '未填写'}}</dd></div>
    <div><dt>设备型号</dt><dd>{{props.caseData.equipmentModel || '未填写'}}</dd></div>
    <div class="wide"><dt>检测依据</dt><dd>{{props.caseData.standardBasis || '未填写'}}</dd></div>
    <div class="wide"><dt>检测设备及证书</dt><dd>{{props.caseData.instrumentDescription || '未填写'}}</dd></div>
  </dl>
</template>
<style scoped>
.ppt-case-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin:16px 0}.ppt-case-summary>div{min-width:0;padding:12px 14px;background:#f5f8f8;border:1px solid #dce4e6;border-radius:7px}.ppt-case-summary dt{color:#60747d;font-size:12px;margin-bottom:5px}.ppt-case-summary dd{margin:0;overflow-wrap:anywhere;line-height:1.6;font-weight:650}.wide{grid-column:1/-1}.dr .date{grid-row:span 3}.ct .date{grid-row:span 2}
@media(max-width:700px){.ppt-case-summary{grid-template-columns:1fr}.dr .date,.ct .date{grid-row:auto}.wide{grid-column:auto}}
</style>
