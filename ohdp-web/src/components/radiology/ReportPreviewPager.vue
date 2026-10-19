<script setup lang="ts">
import { renderAsync } from 'docx-preview'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { clampReportPage, reportPageLabel, clampReportZoom, MIN_REPORT_ZOOM, MAX_REPORT_ZOOM, REPORT_ZOOM_STEP } from './reportPreviewSupport'

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
const zoomPercent = ref(100)
const naturalSize = ref({ width: 0, height: 0 })
const viewportSize = ref({ width: 0, height: 0 })
const fittedScale = computed(() => naturalSize.value.width && naturalSize.value.height && viewportSize.value.width && viewportSize.value.height
  ? Math.min(viewportSize.value.width / naturalSize.value.width, viewportSize.value.height / naturalSize.value.height) : 1)
const contentScale = computed(() => fittedScale.value * zoomPercent.value / 100)
const hasPageSize = computed(() => naturalSize.value.width > 0 && naturalSize.value.height > 0)
const canvasStyle = computed(() => hasPageSize.value ? {
  width: `${Math.ceil(naturalSize.value.width * contentScale.value)}px`,
  height: `${Math.ceil(naturalSize.value.height * contentScale.value)}px`,
} : {})
const contentStyle = computed(() => hasPageSize.value ? {
  position: 'absolute' as const, width: `${naturalSize.value.width}px`, height: `${naturalSize.value.height}px`,
  transform: `scale(${contentScale.value})`,
} : { position: 'relative' as const })
let renderSequence = 0
let resizeObserver: ResizeObserver | undefined

function measureViewport() {
  if (!viewport.value) return
  const view = viewport.value
  const width = view.clientWidth || 0
  // Reserve the same scrollbar space before and after zooming, avoiding resize feedback at the overflow threshold.
  const gutter = Math.max(0, (view.offsetWidth || width) - width)
  viewportSize.value = { width, height: Math.max(0, (view.offsetHeight || view.clientHeight || 0) - gutter) }
}

function resetScroll() {
  if (viewport.value) { viewport.value.scrollTop = 0; viewport.value.scrollLeft = 0 }
}

function changeZoom(delta: number) {
  if (pageCount.value) zoomPercent.value = clampReportZoom(zoomPercent.value + delta)
}

function restoreZoom() {
  zoomPercent.value = 100
  resetScroll()
}

function imageLoaded(event: Event) {
  const image = event.target as HTMLImageElement
  naturalSize.value = { width: image.naturalWidth, height: image.naturalHeight }
  measureViewport()
}

function changePage(delta: number) {
  currentPage.value = clampReportPage(currentPage.value + delta, pageCount.value)
}

function syncPage() {
  currentPage.value = clampReportPage(currentPage.value, pageCount.value)
  docxPages.value.forEach((page, index) => { page.hidden = index !== currentPage.value })
  const wordPage = docxPages.value[currentPage.value]
  if (!props.pages.length && wordPage) naturalSize.value = { width: wordPage.offsetWidth, height: wordPage.offsetHeight }
  measureViewport()
  resetScroll()
  emit('page-change', { index: currentPage.value, count: pageCount.value, label: pageLabel.value })
}

async function renderDocument() {
  const sequence = ++renderSequence
  currentPage.value = 0
  zoomPercent.value = 100
  naturalSize.value = { width: 0, height: 0 }
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
onMounted(() => {
  measureViewport()
  if (viewport.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(measureViewport)
    resizeObserver.observe(viewport.value)
  }
})
onBeforeUnmount(() => { renderSequence++; resizeObserver?.disconnect() })
</script>

<template>
  <div class="report-pager" tabindex="0" aria-label="逐页报告预览"
    @keydown.left.prevent="changePage(-1)" @keydown.right.prevent="changePage(1)">
    <div class="report-zoom-controls" role="group" aria-label="报告缩放">
      <button type="button" aria-label="放大" :disabled="!pageCount || zoomPercent >= MAX_REPORT_ZOOM" @click="changeZoom(REPORT_ZOOM_STEP)">放大</button>
      <button type="button" aria-label="缩小" :disabled="!pageCount || zoomPercent <= MIN_REPORT_ZOOM" @click="changeZoom(-REPORT_ZOOM_STEP)">缩小</button>
      <button type="button" aria-label="还原" :disabled="!pageCount" @click="restoreZoom" title="还原为适合窗口的100%比例">还原</button>
      <output aria-live="polite">{{ zoomPercent }}%</output>
    </div>
    <div class="report-pager-frame">
      <button type="button" class="report-page-arrow" aria-label="上一页"
        :disabled="!pageCount || currentPage === 0" @click="changePage(-1)">
        <svg viewBox="0 0 32 52" aria-hidden="true"><path d="M27 5 L6 26 L27 47" /></svg>
      </button>
      <div ref="viewport" class="report-page-viewport" :aria-busy="loading || rendering">
        <p v-if="loading || rendering" class="report-pager-message" role="status">正在加载报告…</p>
        <p v-if="renderError" class="report-pager-message" role="alert">{{ renderError }}</p>
        <div class="report-zoom-canvas" :style="canvasStyle">
          <div class="report-zoom-content" :style="contentStyle">
            <img v-if="pages.length" :key="currentPage" class="report-page-image" :src="pages[currentPage]" @load="imageLoaded"
              :alt="`${pageLabel}，第${currentPage + 1}页，共${pageCount}页`" />
            <div v-else ref="docxHost" class="report-docx-page" />
          </div>
        </div>
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
.report-zoom-controls{display:flex;justify-content:center;align-items:center;gap:10px;flex-wrap:wrap;margin:0 0 12px}
.report-zoom-controls button{min-height:36px;min-width:70px;border:1px solid #16485b;border-radius:6px;background:white;color:#16485b;font:inherit;cursor:pointer}
.report-zoom-controls button:disabled{opacity:.45;cursor:default}
.report-zoom-controls output{min-width:58px;text-align:center;font-variant-numeric:tabular-nums}
.report-pager-frame{display:grid;grid-template-columns:64px minmax(0,1fr) 64px;align-items:center;gap:12px;padding:12px 0;background:#10485c}
.report-page-arrow{display:flex;align-items:center;justify-content:center;width:100%;min-height:76px;padding:12px;border:0;background:transparent;color:white;cursor:pointer}
.report-page-arrow svg{width:30px;height:50px;fill:none;stroke:currentColor;stroke-width:7}
.report-page-arrow:disabled{opacity:.25;cursor:default}
.report-page-arrow:focus-visible{outline:2px solid white;outline-offset:-6px}
.report-page-viewport{min-width:0;height:60vh;overflow:auto;text-align:center;scrollbar-gutter:stable}
.report-zoom-canvas{position:relative;overflow:hidden;margin:0 auto}
.report-zoom-content{transform-origin:top left;top:0;left:0}
.report-page-image{display:block;width:100%;height:auto;max-width:none;max-height:none;margin:0}
.report-page-counter{margin:8px 0 0;text-align:center;color:#214754;font-size:15px}
.report-pager-message{padding:25px;background:white;color:#214754}
.report-docx-page :deep(.docx-wrapper){background:transparent;padding:0}
.report-docx-page :deep(.docx-wrapper > section.docx){margin:0 auto;box-shadow:none}
.report-docx-page :deep([hidden]){display:none!important}
@media(max-width:650px){.report-pager-frame{grid-template-columns:32px minmax(0,1fr) 32px;gap:4px}.report-page-arrow{padding:5px}.report-page-arrow svg{width:22px;height:40px}}
</style>
