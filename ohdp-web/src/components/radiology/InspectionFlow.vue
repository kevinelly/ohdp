<script setup lang="ts">
defineProps<{modelValue:number;selected:boolean}>()
defineEmits<{ 'update:modelValue':[number] }>()
const labels=['项目创建','检测录入','结果评价','报告审核','报告归档']
</script>
<template><nav class="ppt-flow" aria-label="检测流程"><button v-for="(label,i) in labels" :key="label" type="button" :disabled="i>0&&!selected" :class="{active:modelValue===i+1}" @click="$emit('update:modelValue',i+1)">{{String(i+1).padStart(2,'0')}} {{label}}</button></nav></template>
<style scoped>.ppt-flow{display:flex;background:white;padding:10px 14px;border-radius:12px;margin:10px 0}.ppt-flow button{flex:1;border:0;border-right:1px solid #d3d3d3;border-radius:0;padding:13px 8px;background:white;color:#111;font:inherit;cursor:pointer}.ppt-flow button:last-child{border-right:0}.ppt-flow button.active{background:#00857a;color:white;border-radius:7px}.ppt-flow button:disabled{color:#8b9498;cursor:default}</style>
