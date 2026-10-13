<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import ManualDownloads from './ManualDownloads.vue'
import { findHelpAnchor, findHelpTopic, helpAnchors, helpTopics, type HelpAnchor, type HelpTopicId } from '../../helpContent'

const props = defineProps<{
  activeTopicId: HelpTopicId
  activeAnchorId?: string | null
  returnLabel: string
  standalone?: boolean
}>()

const emit = defineEmits<{
  (event: 'select-topic', topicId: HelpTopicId): void
  (event: 'select-anchor', payload: { topicId: HelpTopicId; anchorId: string }): void
  (event: 'return'): void
}>()

const searchText = ref('')
const articleElement = ref<HTMLElement | null>(null)
const activeTopic = computed(() => findHelpTopic(props.activeTopicId))
const activeAnchor = computed(() => findHelpAnchor(props.activeAnchorId))
const allAnchors = Object.entries(helpAnchors).map(([id, anchor]) => ({ id, ...(anchor as HelpAnchor) }))

const filteredAnchors = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  if (!keyword) return []
  return allAnchors.filter((anchor) => [
    anchor.title,
    anchor.summary,
    anchor.source,
    anchor.example,
    anchor.calculationImpact,
    anchor.reportImpact,
    ...anchor.keywords,
  ].filter(Boolean).join(' ').toLocaleLowerCase().includes(keyword)).slice(0, 20)
})

const filteredTopics = computed(() => {
  const keyword = searchText.value.trim().toLocaleLowerCase()
  if (!keyword) return helpTopics
  return helpTopics.filter((topic) => [
    topic.title,
    topic.summary,
    topic.category,
    topic.audience,
    ...topic.keywords,
  ].join(' ').toLocaleLowerCase().includes(keyword))
})

const groupedTopics = computed(() => {
  const groups = new Map<string, typeof helpTopics>()
  for (const topic of filteredTopics.value) {
    const existing = groups.get(topic.category) || []
    existing.push(topic)
    groups.set(topic.category, existing)
  }
  return Array.from(groups.entries()).map(([category, topics]) => ({ category, topics }))
})

watch(() => props.activeTopicId, async () => {
  await nextTick()
  articleElement.value?.scrollTo({ top: 0, behavior: 'auto' })
})
</script>

<template>
  <section class="help-center" aria-label="系统帮助中心">
    <aside class="help-directory card">
      <div class="help-directory-heading">
        <span>使用手册</span>
        <strong>按功能查找</strong>
      </div>
      <label class="help-search">
        <span>搜索帮助</span>
        <input v-model="searchText" type="search" placeholder="输入功能或问题" autocomplete="off" />
      </label>
      <nav class="help-topic-nav" aria-label="帮助主题">
        <template v-if="filteredAnchors.length">
          <h3>具体问题</h3>
          <button
            v-for="anchor in filteredAnchors"
            :key="anchor.id"
            type="button"
            :class="{ active: anchor.id === activeAnchorId }"
            @click="emit('select-anchor', { topicId: anchor.topicId, anchorId: anchor.id })"
          >
            <strong>{{ anchor.title }}</strong>
            <span>{{ anchor.summary }}</span>
          </button>
        </template>
        <template v-for="group in groupedTopics" :key="group.category">
          <h3>{{ group.category }}</h3>
          <button
            v-for="topic in group.topics"
            :key="topic.id"
            type="button"
            :class="{ active: topic.id === activeTopic.id }"
            :aria-current="topic.id === activeTopic.id ? 'page' : undefined"
            @click="emit('select-topic', topic.id)"
          >
            <strong>{{ topic.title }}</strong>
            <span>{{ topic.summary }}</span>
          </button>
        </template>
        <p v-if="groupedTopics.length === 0 && filteredAnchors.length === 0" class="help-empty">没有找到相关帮助，请更换关键词。</p>
      </nav>
    </aside>

    <article ref="articleElement" class="help-article card">
      <ManualDownloads />
      <header class="help-article-header">
        <div>
          <p>{{ activeTopic.category }}</p>
          <h2>{{ activeTopic.title }}</h2>
          <span>{{ activeTopic.summary }}</span>
        </div>
        <button type="button" class="ghost help-return" @click="emit('return')">{{ standalone ? '关闭帮助标签页' : `返回${returnLabel}` }}</button>
      </header>

      <dl class="help-audience">
        <dt>适用对象</dt>
        <dd>{{ activeTopic.audience }}</dd>
      </dl>

      <section v-if="activeAnchor" class="help-focus-block">
        <div class="help-focus-heading">
          <span>精确帮助</span>
          <h3>{{ activeAnchor.title }}</h3>
        </div>
        <p>{{ activeAnchor.summary }}</p>
        <dl>
          <template v-if="activeAnchor.source"><dt>数据或规则来源</dt><dd>{{ activeAnchor.source }}</dd></template>
          <template v-if="activeAnchor.example"><dt>填写示例</dt><dd>{{ activeAnchor.example }}</dd></template>
          <template v-if="activeAnchor.calculationImpact"><dt>对计算的影响</dt><dd>{{ activeAnchor.calculationImpact }}</dd></template>
          <template v-if="activeAnchor.reportImpact"><dt>对报告的影响</dt><dd>{{ activeAnchor.reportImpact }}</dd></template>
        </dl>
        <div v-for="item in activeAnchor.cautions" :key="item" class="help-note"><strong>注意</strong><span>{{ item }}</span></div>
      </section>

      <section v-for="(section, sectionIndex) in activeTopic.sections" :key="section.title" class="help-section">
        <div class="help-section-title">
          <span>{{ String(sectionIndex + 1).padStart(2, '0') }}</span>
          <h3>{{ section.title }}</h3>
        </div>

        <p v-for="paragraph in section.paragraphs" :key="paragraph" class="help-paragraph">{{ paragraph }}</p>

        <ol v-if="section.steps?.length" class="help-steps">
          <li v-for="step in section.steps" :key="step">{{ step }}</li>
        </ol>

        <div v-if="section.fields?.length" class="help-table-wrap">
          <table class="help-field-table">
            <thead>
              <tr><th>项目</th><th>说明</th><th>填写要求</th></tr>
            </thead>
            <tbody>
              <tr v-for="field in section.fields" :key="field.name">
                <td>{{ field.name }}</td>
                <td>{{ field.description }}</td>
                <td>{{ field.required || '按实际情况' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-for="note in section.notes" :key="note" class="help-note">
          <strong>注意</strong>
          <span>{{ note }}</span>
        </div>
      </section>

      <footer v-if="activeTopic.related.length" class="help-related">
        <strong>相关帮助</strong>
        <div>
          <button
            v-for="topicId in activeTopic.related"
            :key="topicId"
            type="button"
            class="ghost"
            @click="emit('select-topic', topicId)"
          >{{ findHelpTopic(topicId).title }}</button>
        </div>
      </footer>
    </article>
  </section>
</template>
