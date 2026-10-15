<script setup lang="ts">
import { computed } from 'vue'
import { equipmentParameter, updateEquipmentParameter } from './radiologyDraftSupport'
const props=defineProps<{modelValue?:string}>()
const emit=defineEmits<{'update:modelValue':[string]}>()
const voltage=computed({get:()=>equipmentParameter(props.modelValue||'','kV'),set:(v:string)=>emit('update:modelValue',updateEquipmentParameter(props.modelValue||'','kV',v))})
const current=computed({get:()=>equipmentParameter(props.modelValue||'','mA'),set:(v:string)=>emit('update:modelValue',updateEquipmentParameter(props.modelValue||'','mA',v))})
</script>
<template><div class="equipment-parameters"><el-input v-model="voltage" aria-label="设备参数管电压" placeholder="管电压"><template #append>kV</template></el-input><el-input v-model="current" aria-label="设备参数管电流" placeholder="管电流"><template #append>mA</template></el-input></div><details class="ppt-help"><summary>完整设备参数</summary><el-input :model-value="modelValue" type="textarea" aria-label="完整设备参数" @update:model-value="(v:string)=>emit('update:modelValue',v)"/></details></template>
<style scoped>.equipment-parameters{display:flex;gap:16px;width:100%}.equipment-parameters>.el-input{max-width:280px}.ppt-help{width:100%}</style>
