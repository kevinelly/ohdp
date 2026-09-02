<script setup lang="ts">
import { computed } from 'vue'
import { findHelpAnchor } from '../../helpContent'

const props = withDefaults(defineProps<{
  anchorId: string
  tone?: 'default' | 'inverse'
}>(), {
  tone: 'default',
})

const anchor = computed(() => findHelpAnchor(props.anchorId))

function openHelp(event: Event) {
  event.preventDefault()
  event.stopPropagation()
  window.dispatchEvent(new CustomEvent('ohdp:open-context-help', {
    detail: { anchorId: props.anchorId },
  }))
}
</script>

<template>
  <span
    v-if="anchor"
    class="help-mark"
    :class="`help-mark-${tone}`"
    role="button"
    tabindex="0"
    :title="anchor.summary"
    :aria-label="`查看${anchor.title}帮助`"
    @click="openHelp"
    @keydown.enter="openHelp"
    @keydown.space="openHelp"
  >?</span>
</template>
