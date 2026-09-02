<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { findHelpAnchor, type HelpTopicId } from '../../helpContent'

const props = defineProps<{
  anchorId: string | null
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'open-topic', payload: { topicId: HelpTopicId; anchorId: string }): void
}>()

const closeButton = ref<HTMLButtonElement | null>(null)
const anchor = computed(() => findHelpAnchor(props.anchorId))

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && anchor.value) emit('close')
}

watch(() => props.anchorId, async (value) => {
  if (!value) return
  await nextTick()
  closeButton.value?.focus()
})

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div v-if="anchor" class="context-help-layer" @click.self="emit('close')">
    <aside class="context-help-drawer" role="dialog" aria-modal="true" :aria-labelledby="`context-help-${anchorId}`">
      <header class="context-help-header">
        <div>
          <span>当前功能说明</span>
          <h2 :id="`context-help-${anchorId}`">{{ anchor.title }}</h2>
        </div>
        <button ref="closeButton" type="button" class="ghost context-help-close" @click="emit('close')">关闭</button>
      </header>

      <div class="context-help-body">
        <section>
          <h3>功能说明</h3>
          <p>{{ anchor.summary }}</p>
        </section>
        <section v-if="anchor.source">
          <h3>数据或规则来源</h3>
          <p>{{ anchor.source }}</p>
        </section>
        <section v-if="anchor.example">
          <h3>填写示例</h3>
          <p class="context-help-example">{{ anchor.example }}</p>
        </section>
        <section v-if="anchor.calculationImpact">
          <h3>对计算的影响</h3>
          <p>{{ anchor.calculationImpact }}</p>
        </section>
        <section v-if="anchor.reportImpact">
          <h3>对报告的影响</h3>
          <p>{{ anchor.reportImpact }}</p>
        </section>
        <section v-if="anchor.cautions?.length" class="context-help-caution">
          <h3>注意事项</h3>
          <ul>
            <li v-for="item in anchor.cautions" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>

      <footer class="context-help-footer">
        <button type="button" class="ghost" @click="emit('close')">继续当前操作</button>
        <button type="button" @click="emit('open-topic', { topicId: anchor.topicId, anchorId: anchorId || '' })">查看完整帮助</button>
      </footer>
    </aside>
  </div>
</template>
