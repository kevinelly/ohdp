<script setup lang="ts">
import { renderAsync } from 'docx-preview'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { clampReportPage, reportPageLabel } from './reportPreviewSupport'

const props = withDefaults(defineProps<{ pages: string[]; docx?: Blob; loading?: boolean }>(), { loading: false })
const emit = defineEmits<{ 'page-change': [page: { index: number; count: number; label: string }] }>()
const currentPage = ref(0)
const docxHost = ref<HTMLElement>()
const viewport = ref<HTMLElement>()
const docxPages = shallowRef<HTMLElement[]>([])
const renderError = ref('')
const rendering = ref(false)
const pageCount = computed(() => props.pages.length || docxPages.value.length)
const pageLabel = computed(() => reportPageLabel(currentPage.value))
let renderSequence = 0

function changePage(delta: number) {
  currentPage.value = clampReportPage(currentPage.value + delta, pageCount.value)
}

function syncPage() {
  currentPage.value = clampReportPage(currentPage.value, pageCount.value)
  docxPages.value.forEach((page, index) => { page.hidden = index !== currentPage.value })
  if (viewport.value) { viewport.value.scrollTop = 0; viewport.value.scrollLeft = 0 }
  emit('page-change', { index: currentPage.value, count: pageCount.value, label: pageLabel.value })
}

async function renderDocument() {
  const sequence = ++renderSequence
  currentPage.value = 0
  docxPages.value = []
  renderError.value = ''
  rendering.value = false
  if (props.pages.length || !props.docx) return
  rendering.value = true
  await nextTick()
  const host = docxHost.value
  if (!host || sequence !== renderSequence) return
  host.innerHTML = ''
  try {
    await renderAsync(props.docx, host, undefined, {
      breakPages: true, renderHeaders: true, renderFooters: true,
      inWrapper: true, ignoreLastRenderedPageBreak: false,
    })
    if (sequence !== renderSequence) return
    docxPages.value = Array.from(host.querySelectorAll<HTMLElement>('.docx-wrapper > section.docx'))
    if (!docxPages.value.length) throw new Error('empty document')
    syncPage()
  } catch {
    if (sequence === renderSequence) renderError.value = '报告内容预览失败，请退出后重试或下载报告查看。'
  } finally {
    if (sequence === renderSequence) rendering.value = false
  }
}

watch([() => props.pages, () => props.docx], renderDocument, { immediate: true, flush: 'post' })
watch([currentPage, pageCount], syncPage, { immediate: true, flush: 'post' })
onBeforeUnmount(() => { renderSequence++ })
</script>

<template>
  <div class="report-pager" tabindex="0" aria-label="逐页报告预览"
    @keydown.left.prevent="changePage(-1)" @keydown.right.prevent="changePage(1)">
    <div class="report-pager-frame">
      <button type="button" class="report-page-arrow" aria-label="上一页"
        :disabled="!pageCount || currentPage === 0" @click="changePage(-1)">
        <svg viewBox="0 0 32 52" aria-hidden="true"><path d="M27 5 L6 26 L27 47" /></svg>
      </button>
      <div ref="viewport" class="report-page-viewport" :aria-busy="loading || rendering">
        <p v-if="loading || rendering" class="report-pager-message" role="status">正在加载报告…</p>
        <p v-if="renderError" class="report-pager-message" role="alert">{{ renderError }}</p>
        <img v-if="pages.length" :key="currentPage" class="report-page-image" :src="pages[currentPage]"
          :alt="`${pageLabel}，第${currentPage + 1}页，共${pageCount}页`" />
        <div v-else ref="docxHost" class="report-docx-page" />
      </div>
      <button type="button" class="report-page-arrow" aria-label="下一页"
        :disabled="!pageCount || currentPage >= pageCount - 1" @click="changePage(1)">
        <svg viewBox="0 0 32 52" aria-hidden="true"><path d="M5 5 L26 26 L5 47" /></svg>
      </button>
    </div>
    <p v-if="pageCount" class="report-page-counter" aria-live="polite">{{ pageLabel }} · 第{{ currentPage + 1 }}页 / 共{{ pageCount }}页</p>
  </div>
</template>

<style scoped>
.report-pager{outline-offset:3px}
.report-pager-frame{display:grid;grid-template-columns:64px minmax(0,1fr) 64px;align-items:center;gap:12px;padding:12px 0;background:#10485c}
.report-page-arrow{display:flex;align-items:center;justify-content:center;width:100%;min-height:76px;padding:12px;border:0;background:transparent;color:white;cursor:pointer}
.report-page-arrow svg{width:30px;height:50px;fill:none;stroke:currentColor;stroke-width:7}
.report-page-arrow:disabled{opacity:.25;cursor:default}
.report-page-arrow:focus-visible{outline:2px solid white;outline-offset:-6px}
.report-page-viewport{min-width:0;max-height:65vh;overflow:auto;text-align:center;scrollbar-gutter:stable}
.report-page-image{display:block;width:100%;max-height:65vh;object-fit:contain;margin:auto}
.report-page-counter{margin:8px 0 0;text-align:center;color:#214754;font-size:15px}
.report-pager-message{padding:25px;background:white;color:#214754}
.report-docx-page :deep(.docx-wrapper){background:transparent;padding:0}
.report-docx-page :deep(.docx-wrapper > section.docx){margin:0 auto;box-shadow:none}
.report-docx-page :deep([hidden]){display:none!important}
@media(max-width:650px){.report-pager-frame{grid-template-columns:32px minmax(0,1fr) 32px;gap:4px}.report-page-arrow{padding:5px}.report-page-arrow svg{width:22px;height:40px}}
</style>
