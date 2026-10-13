<script setup lang="ts">
defineProps<{ compact?: boolean }>()

const manuals = [
  { title: '详尽操作手册', file: 'detailed-guide.docx', pages: 31, description: '安装配置、完整业务流程与日常管理' },
  { title: '简明操作指引', file: 'quick-guide.docx', pages: 10, description: '首次安装与常用操作，快速上手' },
]
const baseUrl = import.meta.env.BASE_URL
const downloadName = (title: string) => `职业危害与放射诊疗检测数据平台-${title}.docx`
</script>

<template>
  <details v-if="compact" class="manual-downloads-compact">
    <summary><span aria-hidden="true">↓</span>操作手册下载</summary>
    <div class="manual-download-links">
      <a v-for="manual in manuals" :key="manual.file" :href="`${baseUrl}manuals/${manual.file}`" :download="downloadName(manual.title)">
        <strong>{{ manual.title }}</strong>
        <span>Word · {{ manual.pages }} 页</span>
      </a>
      <p>2026-09-17 更新 · 含 EXE 安装说明</p>
    </div>
  </details>
  <section v-else class="manual-downloads" aria-label="操作手册下载">
    <h3>操作手册下载</h3>
    <p>两份手册均包含 Windows EXE 安装说明，可下载后用 Word 或 WPS 阅读。</p>
    <div class="manual-download-cards">
      <a v-for="manual in manuals" :key="manual.file" :href="`${baseUrl}manuals/${manual.file}`" :download="downloadName(manual.title)">
        <strong>{{ manual.title }} <span aria-hidden="true">↓</span></strong>
        <span>{{ manual.description }}</span>
        <small>下载 Word · {{ manual.pages }} 页</small>
      </a>
    </div>
    <p class="manual-version">手册 V1.1 · 2026-09-17 更新</p>
  </section>
</template>

<style scoped>
.manual-downloads-compact { margin: 0 12px 12px; color: #b7d4de; }
.manual-downloads-compact summary { display: flex; align-items: center; gap: 9px; min-height: 42px; padding: 4px 8px; cursor: pointer; border-radius: 6px; list-style: none; }
.manual-downloads-compact summary::-webkit-details-marker { display: none; }
.manual-downloads-compact summary > span { width: 20px; text-align: center; font-size: 20px; }
.manual-downloads-compact summary::after { content: '+'; margin-left: auto; }
.manual-downloads-compact[open] summary::after { content: '−'; }
.manual-downloads-compact summary:hover { background: #164d60; color: #fff; }
.manual-download-links { padding: 4px 0 0 12px; }
.manual-download-links a { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; color: #e4f2f6; border-radius: 6px; text-decoration: none; }
.manual-download-links a:hover { background: #164d60; }
.manual-download-links a span, .manual-download-links p { font-size: 12px; color: #b7d4de; }
.manual-download-links p { margin: 8px 12px; line-height: 1.6; }
.manual-downloads { margin: 0 0 20px; padding: 16px; border: 1px solid #cbdde1; border-radius: 8px; background: #f4faf9; }
.manual-downloads h3 { margin: 0 0 8px; color: #0d4055; font-size: 17px; }
.manual-downloads p { margin: 0 0 12px; color: #526975; line-height: 1.6; }
.manual-download-cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.manual-download-cards a { display: flex; flex-direction: column; gap: 8px; padding: 14px; border: 1px solid #b7d1d3; border-radius: 6px; background: #fff; text-decoration: none; color: #526975; }
.manual-download-cards a:hover { border-color: #007d72; background: #edf8f6; }
.manual-download-cards strong, .manual-download-cards small { color: #006a61; }
.manual-download-cards strong { display: flex; justify-content: space-between; }
.manual-download-cards span, .manual-download-cards small { font-size: 13px; line-height: 1.5; }
.manual-downloads .manual-version { margin: 12px 0 0; font-size: 12px; }
summary:focus-visible, a:focus-visible { outline: 2px solid #009d8c; outline-offset: 2px; }
@media (max-width: 700px) { .manual-download-cards { grid-template-columns: 1fr; } }
</style>
