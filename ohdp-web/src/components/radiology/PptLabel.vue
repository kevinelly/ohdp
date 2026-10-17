<script setup lang="ts">
import { computed } from 'vue'
const props=defineProps<{text:string}>()
// Semantic markup keeps bars and subscripts aligned in Chinese and Latin fonts.
const parts=computed(()=>props.text.split(/(fNyquist|CTbackground|CTmax|WWmin|CThm|CTDI100，[CP]|CTDI[Ww]|CT[CP]|D[tm]|Eᵣ|[KT]̄|[A-Za-zα][₀₁₂₃₄₅₆₇₈₉]+|R²)/g).filter(Boolean).map(text=>{
 if(text.endsWith('̄'))return {text:text.slice(0,-1),bar:true}
 if(text==='R²')return {text:'R',sup:'2'}
 const match=/^(f)(Nyquist)$|^(CT)(background|max|hm|C|P)$|^(WW)(min)$|^(CTDI)(100，[CP]|W|w)$|^(D)(t|m)$/.exec(text)
 if(match){const values=match.slice(1).filter(v=>v!==undefined);return {text:values[0],sub:values[1]}}
 const sub=/^([A-Za-zα])([₀₁₂₃₄₅₆₇₈₉ᵣ]+)$/.exec(text)
 if(sub)return {text:sub[1],sub:[...sub[2]!].map(c=>c==='ᵣ'?'r':String('₀₁₂₃₄₅₆₇₈₉'.indexOf(c))).join('')}
 return {text}
}))
</script>
<template><span class="ppt-label"><template v-for="(part,index) in parts" :key="index"><span v-if="part.bar" class="ppt-overbar">{{part.text}}</span><template v-else>{{part.text}}<sub v-if="part.sub">{{part.sub}}</sub><sup v-if="part.sup">{{part.sup}}</sup></template></template></span></template>
<style scoped>.ppt-label{font:inherit;color:inherit}.ppt-label sub,.ppt-label sup{font-size:.7em;line-height:0;position:relative;vertical-align:baseline}.ppt-label sub{bottom:-.25em}.ppt-label sup{top:-.5em}.ppt-overbar{text-decoration:overline;text-decoration-thickness:1px;font-family:Arial,sans-serif}</style>
