<script setup lang="ts">
import { computed } from 'vue'
import { convertDoseRate } from './radiologyDraftSupport'
const props=defineProps<{modelValue?:string|number;canonicalUnit:string;displayUnit?:string;label:string;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[string];'update:displayUnit':[string]}>()
const unit=computed(()=>props.displayUnit||props.canonicalUnit)
const displayed=computed(()=>convertDoseRate(props.modelValue,props.canonicalUnit,unit.value))
function update(value:string){emit('update:modelValue',convertDoseRate(value,unit.value,props.canonicalUnit))}
</script>
<template><div class="dose-rate-input" :title="displayed ? displayed+' '+unit : ''"><el-input :model-value="displayed" type="number" :disabled="disabled" :aria-label="label" @update:model-value="update"/><el-select :model-value="unit" :disabled="disabled" :aria-label="label+'单位'" @update:model-value="emit('update:displayUnit',$event)"><el-option v-for="u in ['mSv/h','μSv/h','nSv/h']" :key="u" :value="u" :label="u"/></el-select></div></template>
<style scoped>.dose-rate-input{display:flex;min-width:0;height:54px;border:2px solid #aaa;border-radius:8px;overflow:hidden;background:white}.dose-rate-input>.el-input{flex:1;min-width:48px}.dose-rate-input>.el-select{flex:0 0 108px;width:108px}.dose-rate-input :deep(.el-input__wrapper),.dose-rate-input :deep(.el-select__wrapper){height:100%;min-height:0;box-shadow:none!important;border:0!important;border-radius:0!important;background:white!important;padding:0 6px}.dose-rate-input :deep(.el-input__inner){min-width:0;text-align:right;font:20px Arial,sans-serif}.dose-rate-input :deep(.el-select__wrapper){font:18px Arial,sans-serif}.dose-rate-input :deep(.el-select__placeholder){color:#111}</style>
