<script setup lang="ts">
function reloadPage() { window.location.reload() }
</script>

<template>
  <section class="module-load-error" role="alert">
    <strong>页面暂未加载完成</strong>
    <p>请先保存其他页面正在编辑的内容，再刷新重试。</p>
    <button type="button" @click="reloadPage">刷新页面</button>
  </section>
</template>

<style scoped>
.module-load-error { margin:16px 0;padding:20px;border:1px solid #dce4e6;border-radius:10px;background:#fff;color:#203b31; }
.module-load-error p { margin:10px 0;line-height:1.6; }
.module-load-error button { padding:8px 16px;border:0;border-radius:6px;background:#007f75;color:#fff;cursor:pointer; }
</style>
