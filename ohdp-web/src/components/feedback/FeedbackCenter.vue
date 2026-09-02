<script setup lang="ts">
import axios from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import HelpMark from '../help/HelpMark.vue'

type FeedbackStatus = 'ACCEPTED' | 'IN_PROGRESS' | 'NEED_INFO' | 'PENDING_CONFIRMATION' | 'CLOSED' | 'REJECTED' | 'WITHDRAWN'
type FeedbackCategory = 'FEATURE' | 'UI_UX' | 'OPERATION' | 'DATA' | 'REPORT' | 'PERFORMANCE' | 'OTHER'
type FeedbackUrgency = 'NORMAL' | 'URGENT'
type FeedbackPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

interface TicketSummary {
  id: number
  ticketNo: string
  title: string
  category: FeedbackCategory
  categoryLabel: string
  urgency: FeedbackUrgency
  urgencyLabel: string
  priority: FeedbackPriority
  priorityLabel: string
  status: FeedbackStatus
  statusLabel: string
  sourceModule?: string
  relatedCode?: string
  submitterId: number
  submitterName: string
  assigneeId?: number
  assigneeName?: string
  plannedCompletionDate?: string
  plannedVersion?: string
  unread: boolean
  createdAt: string
  updatedAt: string
}

interface FeedbackEvent {
  id: number
  eventType: string
  eventLabel: string
  fromStatus?: FeedbackStatus
  toStatus?: FeedbackStatus
  content?: string
  operatorName: string
  internalOnly: boolean
  createdAt: string
}

interface FeedbackAttachment {
  id: number
  originalName: string
  mediaType: string
  fileSize: number
  sha256: string
  uploaderName: string
  uploadedAt: string
  canDelete: boolean
}

interface TicketDetail {
  ticket: TicketSummary
  description: string
  sourcePage?: string
  relatedType?: string
  resolutionSummary?: string
  resolvedAt?: string
  closedAt?: string
  events: FeedbackEvent[]
  attachments: FeedbackAttachment[]
  allowedTransitions: FeedbackStatus[]
  canHandle: boolean
  canClose: boolean
  owner: boolean
}

interface Summary {
  total: number
  unread: number
  accepted: number
  inProgress: number
  needInfo: number
  pendingConfirmation: number
  closed: number
}

const props = defineProps<{
  token: string
  canReadAll: boolean
  canHandle: boolean
  canClose: boolean
  currentUsername: string
  sourceModule?: string
}>()

const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/' })
const headers = computed(() => ({ Authorization: `Bearer ${props.token}` }))
const scope = ref<'mine' | 'all' | 'assigned'>('mine')
const tickets = ref<TicketSummary[]>([])
const selected = ref<TicketDetail | null>(null)
const selectedId = ref<number | null>(null)
const summary = ref<Summary>({ total: 0, unread: 0, accepted: 0, inProgress: 0, needInfo: 0, pendingConfirmation: 0, closed: 0 })
const busy = ref(false)
const message = ref('')
const messageType = ref<'success' | 'error' | 'info'>('info')
const showCreate = ref(false)
const keyword = ref('')
const statusFilter = ref<'' | FeedbackStatus>('')
const selectedFiles = ref<File[]>([])
const fileInput = ref<HTMLInputElement | null>(null)

const categories: Array<{ value: FeedbackCategory; label: string }> = [
  { value: 'FEATURE', label: '功能建议' },
  { value: 'UI_UX', label: '界面体验' },
  { value: 'OPERATION', label: '操作问题' },
  { value: 'DATA', label: '数据问题' },
  { value: 'REPORT', label: '报告问题' },
  { value: 'PERFORMANCE', label: '性能问题' },
  { value: 'OTHER', label: '其他' },
]

const transitionLabels: Record<FeedbackStatus, string> = {
  ACCEPTED: '补充完成，重新提交',
  IN_PROGRESS: '开始或恢复处理',
  NEED_INFO: '要求补充信息',
  PENDING_CONFIRMATION: '提交用户确认',
  CLOSED: '确认关闭',
  REJECTED: '不予采纳',
  WITHDRAWN: '撤回意见',
}

const createForm = reactive({
  title: '', description: '', category: 'FEATURE' as FeedbackCategory, urgency: 'NORMAL' as FeedbackUrgency,
  sourceModule: props.sourceModule || '意见反馈', relatedType: '', relatedCode: '',
})
const handlingForm = reactive({
  priority: 'NORMAL' as FeedbackPriority, plannedCompletionDate: '', plannedVersion: '',
})
const commentForm = reactive({ content: '', internalOnly: false })
const transitionForm = reactive({ targetStatus: '' as '' | FeedbackStatus, comment: '', resolutionSummary: '' })

const listCaption = computed(() => scope.value === 'mine' ? '我提交的意见' : scope.value === 'assigned' ? '分配给我的意见' : '全部意见')
const actionNeedsResolution = computed(() => transitionForm.targetStatus === 'PENDING_CONFIRMATION')
const actionNeedsComment = computed(() => ['NEED_INFO', 'REJECTED'].includes(transitionForm.targetStatus)
  || (transitionForm.targetStatus === 'IN_PROGRESS' && selected.value?.ticket.status === 'PENDING_CONFIRMATION'))

watch(() => props.sourceModule, (value) => {
  if (!createForm.title && value) createForm.sourceModule = value
})

watch(scope, () => {
  selected.value = null
  selectedId.value = null
  showCreate.value = false
  void refresh()
})

function notify(text: string, type: 'success' | 'error' | 'info' = 'info') {
  message.value = text
  messageType.value = type
}

function errorText(error: any, fallback: string) {
  return error?.response?.data?.message || fallback
}

async function refresh() {
  busy.value = true
  try {
    const params: Record<string, string | number> = { scope: scope.value, page: 0, size: 100 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value) params.status = statusFilter.value
    const [listResponse, summaryResponse] = await Promise.all([
      api.get('/api/feedback', { headers: headers.value, params }),
      api.get('/api/feedback/summary', { headers: headers.value, params: { scope: scope.value } }),
    ])
    tickets.value = listResponse.data.content || []
    summary.value = summaryResponse.data
    if (selectedId.value && !tickets.value.some(item => item.id === selectedId.value)) {
      selected.value = null
      selectedId.value = null
    }
  } catch (error: any) {
    notify(errorText(error, '意见列表加载失败'), 'error')
  } finally {
    busy.value = false
  }
}

async function selectTicket(item: TicketSummary) {
  busy.value = true
  showCreate.value = false
  try {
    const { data } = await api.get<TicketDetail>(`/api/feedback/${item.id}`, { headers: headers.value })
    selected.value = data
    selectedId.value = item.id
    syncHandling(data)
    const row = tickets.value.find(ticket => ticket.id === item.id)
    if (row) row.unread = false
  } catch (error: any) {
    notify(errorText(error, '意见详情加载失败'), 'error')
  } finally {
    busy.value = false
  }
}

function openCreate() {
  selected.value = null
  selectedId.value = null
  showCreate.value = true
  createForm.sourceModule = props.sourceModule || '意见反馈'
}

function resetCreate() {
  createForm.title = ''
  createForm.description = ''
  createForm.category = 'FEATURE'
  createForm.urgency = 'NORMAL'
  createForm.sourceModule = props.sourceModule || '意见反馈'
  createForm.relatedType = ''
  createForm.relatedCode = ''
  selectedFiles.value = []
  if (fileInput.value) fileInput.value.value = ''
}

function chooseFiles(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFiles.value = Array.from(input.files || [])
}

async function submitFeedback() {
  if (createForm.title.trim().length < 5) return notify('标题至少填写 5 个字', 'error')
  if (createForm.description.trim().length < 10) return notify('请用至少 10 个字说明现象、期望和使用场景', 'error')
  busy.value = true
  try {
    const { data } = await api.post<TicketDetail>('/api/feedback', {
      ...createForm,
      sourcePage: `${window.location.pathname}${window.location.search}`,
      relatedType: createForm.relatedType || null,
      relatedCode: createForm.relatedCode || null,
    }, { headers: headers.value })
    for (const file of selectedFiles.value) {
      const formData = new FormData()
      formData.append('file', file)
      await api.post(`/api/feedback/${data.ticket.id}/attachments`, formData, { headers: headers.value })
    }
    resetCreate()
    showCreate.value = false
    await refresh()
    const created = tickets.value.find(item => item.id === data.ticket.id) || data.ticket
    await selectTicket(created)
    notify('意见已提交并自动采纳，可在时间线中跟踪处理进度', 'success')
  } catch (error: any) {
    notify(errorText(error, '意见提交失败'), 'error')
  } finally {
    busy.value = false
  }
}

function syncHandling(detail: TicketDetail) {
  handlingForm.priority = detail.ticket.priority
  handlingForm.plannedCompletionDate = detail.ticket.plannedCompletionDate || ''
  handlingForm.plannedVersion = detail.ticket.plannedVersion || ''
  transitionForm.targetStatus = ''
  transitionForm.comment = ''
  transitionForm.resolutionSummary = detail.resolutionSummary || ''
}

async function saveHandling(assignToMe = false) {
  if (!selected.value) return
  busy.value = true
  try {
    const { data } = await api.put<TicketDetail>(`/api/feedback/${selected.value.ticket.id}/handling`, {
      assignToMe,
      priority: handlingForm.priority,
      plannedCompletionDate: handlingForm.plannedCompletionDate || null,
      plannedVersion: handlingForm.plannedVersion || null,
    }, { headers: headers.value })
    selected.value = data
    syncHandling(data)
    await refresh()
    notify(assignToMe ? '已领取该意见' : '处理安排已保存', 'success')
  } catch (error: any) {
    notify(errorText(error, '处理安排保存失败'), 'error')
  } finally {
    busy.value = false
  }
}

function prepareTransition(target: FeedbackStatus) {
  transitionForm.targetStatus = target
  transitionForm.comment = ''
  if (target !== 'PENDING_CONFIRMATION') transitionForm.resolutionSummary = selected.value?.resolutionSummary || ''
}

async function submitTransition() {
  if (!selected.value || !transitionForm.targetStatus) return
  if (actionNeedsComment.value && !transitionForm.comment.trim()) return notify('该操作需要填写说明', 'error')
  if (actionNeedsResolution.value && !transitionForm.resolutionSummary.trim()) return notify('请填写处理结果后再提交用户确认', 'error')
  busy.value = true
  try {
    const { data } = await api.post<TicketDetail>(`/api/feedback/${selected.value.ticket.id}/transitions`, {
      targetStatus: transitionForm.targetStatus,
      comment: transitionForm.comment || null,
      resolutionSummary: transitionForm.resolutionSummary || null,
    }, { headers: headers.value })
    selected.value = data
    syncHandling(data)
    await refresh()
    notify('意见状态已更新', 'success')
  } catch (error: any) {
    notify(errorText(error, '状态更新失败'), 'error')
  } finally {
    busy.value = false
  }
}

async function submitComment() {
  if (!selected.value || !commentForm.content.trim()) return notify('请先填写补充内容', 'error')
  busy.value = true
  try {
    const { data } = await api.post<TicketDetail>(`/api/feedback/${selected.value.ticket.id}/comments`, {
      content: commentForm.content,
      internalOnly: commentForm.internalOnly,
    }, { headers: headers.value })
    selected.value = data
    commentForm.content = ''
    commentForm.internalOnly = false
    notify('补充内容已记录到时间线', 'success')
  } catch (error: any) {
    notify(errorText(error, '补充内容保存失败'), 'error')
  } finally {
    busy.value = false
  }
}

async function uploadAdditional(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!selected.value || !file) return
  busy.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    await api.post(`/api/feedback/${selected.value.ticket.id}/attachments`, formData, { headers: headers.value })
    await selectTicket(selected.value.ticket)
    notify('附件已上传', 'success')
  } catch (error: any) {
    notify(errorText(error, '附件上传失败'), 'error')
  } finally {
    input.value = ''
    busy.value = false
  }
}

async function downloadAttachment(item: FeedbackAttachment) {
  try {
    const response = await api.get(`/api/feedback/attachments/${item.id}/download`, {
      headers: headers.value, responseType: 'blob',
    })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = item.originalName
    link.click()
    URL.revokeObjectURL(url)
  } catch (error: any) {
    notify(errorText(error, '附件下载失败'), 'error')
  }
}

async function deleteAttachment(item: FeedbackAttachment) {
  if (!selected.value || !item.canDelete) return
  busy.value = true
  try {
    await api.delete(`/api/feedback/attachments/${item.id}`, { headers: headers.value })
    await selectTicket(selected.value.ticket)
    notify('附件已删除', 'success')
  } catch (error: any) {
    notify(errorText(error, '附件删除失败'), 'error')
  } finally {
    busy.value = false
  }
}

function statusClass(status: FeedbackStatus) {
  return `feedback-status feedback-status-${status.toLowerCase().replace('_', '-')}`
}

function formatDate(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
    .format(new Date(value))
}

function formatBytes(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(refresh)
</script>

<template>
  <section class="feedback-center" data-testid="feedback-center">
    <header class="feedback-toolbar">
      <div>
        <h2>意见跟踪</h2>
        <p>提交后自动采纳，后续处理安排、补充说明和状态变化均保留时间记录。</p>
      </div>
      <button type="button" class="feedback-primary" data-testid="feedback-create" @click="openCreate">提交新意见</button>
    </header>

    <p v-if="message" class="feedback-message" :class="messageType" role="status">{{ message }}</p>

    <div class="feedback-summary" aria-label="意见概况">
      <button type="button" :class="{ active: !statusFilter }" @click="statusFilter = ''; refresh()"><strong>{{ summary.total }}</strong><span>全部</span></button>
      <button type="button" :class="{ active: statusFilter === 'ACCEPTED' }" @click="statusFilter = 'ACCEPTED'; refresh()"><strong>{{ summary.accepted }}</strong><span>已采纳</span></button>
      <button type="button" :class="{ active: statusFilter === 'IN_PROGRESS' }" @click="statusFilter = 'IN_PROGRESS'; refresh()"><strong>{{ summary.inProgress }}</strong><span>处理中</span></button>
      <button type="button" :class="{ active: statusFilter === 'NEED_INFO' }" @click="statusFilter = 'NEED_INFO'; refresh()"><strong>{{ summary.needInfo }}</strong><span>待补充</span></button>
      <button type="button" :class="{ active: statusFilter === 'PENDING_CONFIRMATION' }" @click="statusFilter = 'PENDING_CONFIRMATION'; refresh()"><strong>{{ summary.pendingConfirmation }}</strong><span>待确认</span></button>
      <span class="feedback-unread">{{ summary.unread }} 条未读</span>
    </div>

    <div class="feedback-layout">
      <aside class="feedback-queue" aria-label="意见列表">
        <div v-if="canReadAll" class="feedback-scope" role="tablist" aria-label="意见范围">
          <button type="button" :class="{ active: scope === 'mine' }" @click="scope = 'mine'">我的意见</button>
          <button type="button" :class="{ active: scope === 'assigned' }" @click="scope = 'assigned'">分配给我</button>
          <button type="button" :class="{ active: scope === 'all' }" @click="scope = 'all'">处理工作台</button>
        </div>
        <div class="feedback-search">
          <input v-model="keyword" placeholder="编号、标题或提交人" aria-label="查询意见" @keyup.enter="refresh" />
          <button type="button" class="feedback-secondary" @click="refresh">查询</button>
        </div>
        <div class="feedback-queue-title"><strong>{{ listCaption }}</strong><span>{{ tickets.length }} 条</span></div>
        <div v-if="busy && !tickets.length" class="feedback-list-state">正在加载…</div>
        <div v-else-if="!tickets.length" class="feedback-list-state">当前没有符合条件的意见</div>
        <button
          v-for="item in tickets"
          :key="item.id"
          type="button"
          class="feedback-ticket-row"
          :class="{ selected: selectedId === item.id, unread: item.unread }"
          @click="selectTicket(item)"
        >
          <span class="ticket-row-top"><strong>{{ item.ticketNo }}</strong><i :class="statusClass(item.status)">{{ item.statusLabel }}</i></span>
          <span class="ticket-row-title">{{ item.title }}</span>
          <span class="ticket-row-meta">{{ item.categoryLabel }} · {{ item.submitterName }} · {{ formatDate(item.updatedAt) }}</span>
        </button>
      </aside>

      <main class="feedback-main">
        <form v-if="showCreate" class="feedback-form" data-testid="feedback-create-form" @submit.prevent="submitFeedback">
          <div class="feedback-section-heading">
            <div><h3>提交新意见</h3><p>系统会自动记录当前模块，并在提交后立即标记为“已采纳”。</p></div>
            <HelpMark anchor-id="feedback.auto-accept" />
          </div>
          <div class="feedback-form-grid">
            <label>意见分类 <HelpMark anchor-id="feedback.category" />
              <select v-model="createForm.category"><option v-for="item in categories" :key="item.value" :value="item.value">{{ item.label }}</option></select>
            </label>
            <label>紧急程度 <HelpMark anchor-id="feedback.urgency" />
              <select v-model="createForm.urgency"><option value="NORMAL">一般</option><option value="URGENT">紧急</option></select>
            </label>
            <label class="full">标题 <span class="required">*</span>
              <input v-model="createForm.title" maxlength="120" placeholder="用一句话说明需要改进的内容" />
            </label>
            <label class="full">详细说明 <span class="required">*</span> <HelpMark anchor-id="feedback.description" />
              <textarea v-model="createForm.description" maxlength="4000" rows="8" placeholder="建议说明：当前现象、期望结果、复现步骤和影响范围。"></textarea>
              <small>{{ createForm.description.length }}/4000</small>
            </label>
            <label>来源模块
              <input v-model="createForm.sourceModule" maxlength="100" />
            </label>
            <label>关联业务编号 <HelpMark anchor-id="feedback.relation" />
              <input v-model="createForm.relatedCode" maxlength="100" placeholder="项目、DR/CT 案例或报告编号" />
            </label>
            <label>关联类型
              <select v-model="createForm.relatedType"><option value="">无</option><option value="PROJECT">项目</option><option value="RADIOLOGY_CASE">DR/CT 案例</option><option value="REPORT">报告</option><option value="OTHER">其他</option></select>
            </label>
            <label>附件 <HelpMark anchor-id="feedback.attachment" />
              <input ref="fileInput" type="file" multiple accept=".png,.jpg,.jpeg,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="chooseFiles" />
            </label>
          </div>
          <div v-if="selectedFiles.length" class="selected-files"><span v-for="file in selectedFiles" :key="`${file.name}-${file.size}`">{{ file.name }}</span></div>
          <div class="feedback-actions">
            <button type="button" class="feedback-secondary" @click="showCreate = false">取消</button>
            <button type="submit" class="feedback-primary" :disabled="busy">{{ busy ? '正在提交…' : '提交并自动采纳' }}</button>
          </div>
        </form>

        <article v-else-if="selected" class="feedback-detail">
          <header class="ticket-detail-header">
            <div>
              <div class="ticket-detail-code"><span>{{ selected.ticket.ticketNo }}</span><i :class="statusClass(selected.ticket.status)">{{ selected.ticket.statusLabel }}</i></div>
              <h3>{{ selected.ticket.title }}</h3>
              <p>{{ selected.ticket.categoryLabel }} · {{ selected.ticket.urgencyLabel }} · 由 {{ selected.ticket.submitterName }} 提交</p>
            </div>
            <HelpMark anchor-id="feedback.status" />
          </header>
          <section class="ticket-description">
            <h4>意见说明</h4>
            <p>{{ selected.description }}</p>
            <dl>
              <div><dt>来源模块</dt><dd>{{ selected.ticket.sourceModule || '未记录' }}</dd></div>
              <div><dt>关联编号</dt><dd>{{ selected.ticket.relatedCode || '无' }}</dd></div>
              <div><dt>提交时间</dt><dd>{{ formatDate(selected.ticket.createdAt) }}</dd></div>
            </dl>
          </section>

          <section v-if="selected.resolutionSummary" class="resolution-block">
            <h4>处理结果</h4><p>{{ selected.resolutionSummary }}</p>
          </section>

          <section class="ticket-files">
            <div class="feedback-section-heading"><div><h4>附件</h4><p>支持图片、PDF、Word 和 Excel，单个文件不超过 10 MB。</p></div><HelpMark anchor-id="feedback.attachment" /></div>
            <div v-if="selected.attachments.length" class="attachment-list">
              <div v-for="item in selected.attachments" :key="item.id" class="attachment-row">
                <div><strong>{{ item.originalName }}</strong><span>{{ formatBytes(item.fileSize) }} · {{ item.uploaderName }}</span></div>
                <div><button type="button" class="feedback-link" @click="downloadAttachment(item)">下载</button><button v-if="item.canDelete" type="button" class="feedback-link danger" @click="deleteAttachment(item)">删除</button></div>
              </div>
            </div>
            <p v-else class="feedback-empty-line">暂无附件</p>
            <label v-if="!['CLOSED','REJECTED','WITHDRAWN'].includes(selected.ticket.status)" class="upload-more">上传补充附件<input type="file" accept=".png,.jpg,.jpeg,.webp,.pdf,.doc,.docx,.xls,.xlsx" @change="uploadAdditional" /></label>
          </section>

          <section class="feedback-timeline">
            <div class="feedback-section-heading"><div><h4>处理时间线</h4><p>每次提交、采纳、补充和状态变化都会留下记录。</p></div><HelpMark anchor-id="feedback.timeline" /></div>
            <ol>
              <li v-for="event in selected.events" :key="event.id">
                <span class="timeline-dot"></span>
                <div><strong>{{ event.eventLabel }} <em v-if="event.internalOnly">内部记录</em></strong><p v-if="event.content">{{ event.content }}</p><small>{{ event.operatorName }} · {{ formatDate(event.createdAt) }}</small></div>
              </li>
            </ol>
          </section>

          <form v-if="!['CLOSED','REJECTED','WITHDRAWN'].includes(selected.ticket.status)" class="feedback-comment" @submit.prevent="submitComment">
            <label>补充说明 <HelpMark anchor-id="feedback.comment" /><textarea v-model="commentForm.content" rows="4" maxlength="4000" placeholder="补充复现信息、处理进展或沟通结论"></textarea></label>
            <label v-if="selected.canHandle" class="internal-check"><input v-model="commentForm.internalOnly" type="checkbox" />仅处理人员可见 <HelpMark anchor-id="feedback.internal-note" /></label>
            <button type="submit" class="feedback-secondary" :disabled="busy">记录到时间线</button>
          </form>
        </article>

        <div v-else class="feedback-empty">
          <strong>选择一条意见查看处理记录</strong>
          <p>也可以提交新意见。提交后系统会立即采纳并生成可追踪编号。</p>
          <button type="button" class="feedback-primary" @click="openCreate">提交第一条意见</button>
        </div>
      </main>

      <aside class="feedback-handling" aria-label="处理安排">
        <template v-if="selected">
          <div class="handling-heading"><h3>处理安排</h3><HelpMark anchor-id="feedback.handling" /></div>
          <dl class="handling-facts">
            <div><dt>当前状态</dt><dd><i :class="statusClass(selected.ticket.status)">{{ selected.ticket.statusLabel }}</i></dd></div>
            <div><dt>处理人</dt><dd>{{ selected.ticket.assigneeName || '待领取' }}</dd></div>
            <div><dt>优先级</dt><dd>{{ selected.ticket.priorityLabel }}</dd></div>
            <div><dt>计划完成</dt><dd>{{ selected.ticket.plannedCompletionDate || '未设置' }}</dd></div>
            <div><dt>计划版本</dt><dd>{{ selected.ticket.plannedVersion || '未设置' }}</dd></div>
          </dl>

          <form v-if="selected.canHandle && !['CLOSED','REJECTED','WITHDRAWN'].includes(selected.ticket.status)" class="handling-form" @submit.prevent="saveHandling(false)">
            <label>优先级<select v-model="handlingForm.priority"><option value="LOW">低</option><option value="NORMAL">普通</option><option value="HIGH">高</option><option value="URGENT">紧急</option></select></label>
            <label>计划完成日期<input v-model="handlingForm.plannedCompletionDate" type="date" /></label>
            <label>计划版本<input v-model="handlingForm.plannedVersion" maxlength="64" placeholder="例如 2026.9" /></label>
            <div class="handling-buttons"><button v-if="!selected.ticket.assigneeId" type="button" class="feedback-primary" @click="saveHandling(true)">领取处理</button><button type="submit" class="feedback-secondary">保存安排</button></div>
          </form>

          <section v-if="selected.allowedTransitions.length" class="transition-panel">
            <h4>下一步操作 <HelpMark anchor-id="feedback.transition" /></h4>
            <div class="transition-buttons">
              <button v-for="target in selected.allowedTransitions" :key="target" type="button" :class="{ active: transitionForm.targetStatus === target }" @click="prepareTransition(target)">{{ transitionLabels[target] }}</button>
            </div>
            <form v-if="transitionForm.targetStatus" @submit.prevent="submitTransition">
              <label v-if="actionNeedsResolution">处理结果 <span class="required">*</span><textarea v-model="transitionForm.resolutionSummary" rows="4" placeholder="说明已经完成的修改、验证结果和影响范围"></textarea></label>
              <label>操作说明 <span v-if="actionNeedsComment" class="required">*</span><textarea v-model="transitionForm.comment" rows="3" placeholder="记录本次状态变化的原因或说明"></textarea></label>
              <button type="submit" class="feedback-primary" :disabled="busy">确认{{ transitionLabels[transitionForm.targetStatus] }}</button>
            </form>
          </section>
          <p v-else class="handling-end">当前意见已结束，没有待执行的流转操作。</p>
        </template>
        <div v-else class="handling-placeholder"><strong>跟踪规则</strong><p>意见提交后自动采纳。处理人员可领取、安排版本并推进状态；提交人可补充、确认或要求重新处理。</p></div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.feedback-center { color: #102f3b; }
.feedback-toolbar { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.feedback-toolbar h2 { margin: 0 0 5px; font-size: 22px; }
.feedback-toolbar p, .feedback-section-heading p { margin: 0; color: #607986; line-height: 1.55; }
.feedback-primary, .feedback-secondary { min-height: 38px; border-radius: 8px; padding: 0 16px; font: inherit; font-weight: 700; cursor: pointer; }
.feedback-primary { border: 1px solid #087f78; background: #087f78; color: #fff; }
.feedback-secondary { border: 1px solid #a9bcc4; background: #fff; color: #123947; }
.feedback-primary:hover { background: #066c67; }
.feedback-secondary:hover { border-color: #087f78; color: #087f78; }
.feedback-primary:focus-visible, .feedback-secondary:focus-visible, .feedback-ticket-row:focus-visible, .feedback-summary button:focus-visible, .feedback-scope button:focus-visible { outline: 3px solid rgba(8, 127, 120, .24); outline-offset: 2px; }
.feedback-primary:disabled, .feedback-secondary:disabled { cursor: wait; opacity: .58; }
.feedback-message { margin: 0 0 14px; border-radius: 8px; padding: 10px 14px; border: 1px solid #b9cbd2; background: #eef5f7; }
.feedback-message.success { border-color: #9bc9b5; background: #eef8f2; color: #17623f; }
.feedback-message.error { border-color: #e2b1b1; background: #fff2f2; color: #9d2525; }
.feedback-summary { display: flex; align-items: stretch; gap: 1px; margin-bottom: 14px; overflow-x: auto; border: 1px solid #c5d4da; border-radius: 12px; background: #c5d4da; }
.feedback-summary button { min-width: 108px; flex: 1; border: 0; background: #fff; padding: 10px 14px; color: #536c77; cursor: pointer; }
.feedback-summary button strong { display: block; color: #113845; font-size: 18px; font-variant-numeric: tabular-nums; }
.feedback-summary button.active { background: #e4f3f1; color: #087f78; }
.feedback-summary button.active strong { color: #087f78; }
.feedback-unread { display: grid; place-items: center; min-width: 112px; padding: 0 14px; background: #f4f8f9; color: #345560; font-weight: 700; }
.feedback-layout { display: grid; grid-template-columns: 310px minmax(500px, 1fr) 285px; min-height: 650px; border: 1px solid #c5d4da; border-radius: 14px; overflow: hidden; background: #fff; }
.feedback-queue, .feedback-handling { background: #f5f8f9; }
.feedback-queue { border-right: 1px solid #cbd8dd; overflow: auto; max-height: calc(100vh - 270px); }
.feedback-handling { border-left: 1px solid #cbd8dd; padding: 20px; }
.feedback-main { min-width: 0; padding: 24px 28px; overflow: auto; max-height: calc(100vh - 270px); }
.feedback-scope { display: grid; grid-template-columns: repeat(3, 1fr); background: #dce6e9; gap: 1px; }
.feedback-scope button { min-height: 43px; border: 0; background: #fff; color: #536d78; cursor: pointer; font-weight: 700; }
.feedback-scope button.active { background: #e1f1ef; color: #087f78; }
.feedback-search { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 14px; }
.feedback-search input, .feedback-form input, .feedback-form select, .feedback-form textarea, .handling-form input, .handling-form select, .transition-panel textarea, .feedback-comment textarea { width: 100%; border: 1px solid #b9cbd2; border-radius: 8px; background: #fff; color: #173844; padding: 10px 11px; font: inherit; box-sizing: border-box; }
.feedback-search input:focus, .feedback-form input:focus, .feedback-form select:focus, .feedback-form textarea:focus, .handling-form input:focus, .handling-form select:focus, .transition-panel textarea:focus, .feedback-comment textarea:focus { outline: 3px solid rgba(8, 127, 120, .16); border-color: #087f78; }
.feedback-search input::placeholder, .feedback-form input::placeholder, .feedback-form textarea::placeholder, .transition-panel textarea::placeholder, .feedback-comment textarea::placeholder { color: #718892; opacity: 1; }
.feedback-queue-title { display: flex; justify-content: space-between; padding: 2px 15px 10px; color: #58727d; font-size: 13px; }
.feedback-ticket-row { display: block; width: calc(100% - 20px); margin: 0 10px 9px; padding: 14px; text-align: left; border: 1px solid #c6d4d9; border-radius: 10px; background: #fff; color: inherit; cursor: pointer; }
.feedback-ticket-row:hover { border-color: #78aaa8; }
.feedback-ticket-row.selected { border-color: #087f78; background: #edf7f6; box-shadow: 0 5px 14px rgba(15, 66, 75, .08); }
.feedback-ticket-row.unread .ticket-row-title::before { content: ''; display: inline-block; width: 7px; height: 7px; margin: 0 7px 2px 0; border-radius: 50%; background: #d37a19; }
.ticket-row-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ticket-row-top strong { font-size: 12px; color: #52707a; font-variant-numeric: tabular-nums; }
.ticket-row-title { display: block; margin: 10px 0 7px; font-weight: 800; line-height: 1.45; }
.ticket-row-meta { display: block; color: #667e88; font-size: 12px; line-height: 1.5; }
.feedback-list-state { padding: 56px 20px; text-align: center; color: #6a818b; }
.feedback-status { display: inline-flex; align-items: center; min-height: 24px; border-radius: 999px; padding: 0 9px; font-style: normal; font-size: 12px; font-weight: 800; white-space: nowrap; background: #edf2f3; color: #526a73; }
.feedback-status-accepted { background: #eaf5eb; color: #36703c; }
.feedback-status-in-progress { background: #e5f1f5; color: #20647b; }
.feedback-status-need-info { background: #fff2d9; color: #85550b; }
.feedback-status-pending-confirmation { background: #eee9f8; color: #624c83; }
.feedback-status-closed { background: #e9efef; color: #526568; }
.feedback-status-rejected, .feedback-status-withdrawn { background: #f7e9e9; color: #8e3d3d; }
.feedback-form { max-width: 900px; }
.feedback-section-heading, .ticket-detail-header, .handling-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.feedback-section-heading h3, .feedback-section-heading h4, .ticket-files h4, .feedback-timeline h4, .handling-heading h3 { margin: 0 0 5px; }
.feedback-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 18px; margin-top: 24px; }
.feedback-form-grid label, .handling-form label, .transition-panel label, .feedback-comment label { display: grid; gap: 7px; color: #334f5a; font-weight: 700; }
.feedback-form-grid label.full { grid-column: 1 / -1; }
.feedback-form textarea, .transition-panel textarea, .feedback-comment textarea { resize: vertical; line-height: 1.55; }
.feedback-form label small { justify-self: end; color: #71858d; font-weight: 400; }
.required { color: #b43b3b; }
.selected-files { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 12px; }
.selected-files span { border-radius: 999px; padding: 5px 10px; background: #edf3f4; color: #425f69; font-size: 12px; }
.feedback-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }
.ticket-detail-header { padding-bottom: 18px; border-bottom: 1px solid #d7e1e4; }
.ticket-detail-header h3 { margin: 8px 0 6px; font-size: 24px; line-height: 1.35; }
.ticket-detail-header p { margin: 0; color: #607984; }
.ticket-detail-code { display: flex; align-items: center; gap: 9px; color: #4a6873; font-size: 13px; font-variant-numeric: tabular-nums; }
.ticket-description, .ticket-files, .feedback-timeline, .feedback-comment { padding-top: 22px; }
.ticket-description h4, .resolution-block h4 { margin: 0 0 10px; }
.ticket-description > p, .resolution-block p { margin: 0; max-width: 75ch; white-space: pre-wrap; line-height: 1.75; color: #294954; }
.ticket-description dl { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin: 20px 0 0; }
.ticket-description dl div, .handling-facts div { display: grid; gap: 4px; }
.ticket-description dt, .handling-facts dt { color: #6c828b; font-size: 12px; }
.ticket-description dd, .handling-facts dd { margin: 0; font-weight: 700; overflow-wrap: anywhere; }
.resolution-block { margin-top: 20px; padding: 16px; border-radius: 10px; background: #edf7f3; }
.attachment-list { margin-top: 12px; border-top: 1px solid #d7e1e4; }
.attachment-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid #d7e1e4; }
.attachment-row strong, .attachment-row span { display: block; overflow-wrap: anywhere; }
.attachment-row span { margin-top: 4px; color: #6a8089; font-size: 12px; }
.feedback-link { border: 0; background: transparent; color: #087f78; cursor: pointer; font: inherit; font-weight: 700; }
.feedback-link.danger { color: #a63a3a; }
.upload-more { display: inline-flex; align-items: center; min-height: 36px; margin-top: 12px; border: 1px solid #a9bcc4; border-radius: 8px; padding: 0 12px; cursor: pointer; font-weight: 700; }
.upload-more input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.feedback-empty-line { color: #6c828b; }
.feedback-timeline ol { list-style: none; margin: 18px 0 0; padding: 0; }
.feedback-timeline li { position: relative; display: grid; grid-template-columns: 18px 1fr; gap: 10px; min-height: 64px; }
.feedback-timeline li:not(:last-child)::before { content: ''; position: absolute; left: 6px; top: 15px; bottom: 0; width: 1px; background: #c1d1d6; }
.timeline-dot { z-index: 1; width: 13px; height: 13px; margin-top: 3px; border-radius: 50%; background: #087f78; box-shadow: 0 0 0 4px #e3f2f0; }
.feedback-timeline strong em { margin-left: 5px; border-radius: 4px; padding: 2px 5px; background: #edf0f1; color: #5d7078; font-size: 11px; font-style: normal; }
.feedback-timeline p { margin: 5px 0; max-width: 75ch; white-space: pre-wrap; line-height: 1.55; color: #34525d; }
.feedback-timeline small { color: #70868f; }
.feedback-comment { border-top: 1px solid #d7e1e4; }
.feedback-comment button { margin-top: 10px; }
.internal-check { display: flex !important; align-items: center; grid-template-columns: auto 1fr; margin-top: 9px; font-size: 13px; }
.internal-check input { width: auto; }
.feedback-empty { min-height: 540px; display: grid; place-content: center; justify-items: center; padding: 24px; text-align: center; }
.feedback-empty strong { font-size: 20px; }
.feedback-empty p { max-width: 50ch; margin: 10px 0 18px; color: #677e88; line-height: 1.6; }
.handling-heading { padding-bottom: 13px; border-bottom: 1px solid #cfdbdf; }
.handling-heading h3 { font-size: 18px; }
.handling-facts { display: grid; gap: 13px; margin: 16px 0 20px; }
.handling-form { display: grid; gap: 12px; padding-top: 18px; border-top: 1px solid #cfdbdf; }
.handling-form input, .handling-form select { background: #fff; }
.handling-buttons { display: grid; gap: 8px; }
.transition-panel { margin-top: 20px; padding-top: 18px; border-top: 1px solid #cfdbdf; }
.transition-panel h4 { margin: 0 0 12px; }
.transition-buttons { display: grid; gap: 7px; }
.transition-buttons button { min-height: 36px; border: 1px solid #aec0c7; border-radius: 8px; background: #fff; color: #254853; cursor: pointer; font: inherit; font-weight: 700; }
.transition-buttons button.active { border-color: #087f78; background: #e4f3f1; color: #087f78; }
.transition-panel form { display: grid; gap: 12px; margin-top: 14px; }
.handling-end, .handling-placeholder p { color: #617982; line-height: 1.65; }
.handling-placeholder strong { font-size: 17px; }

@media (max-width: 1280px) {
  .feedback-layout { grid-template-columns: 280px minmax(460px, 1fr); }
  .feedback-handling { grid-column: 1 / -1; border-top: 1px solid #cbd8dd; border-left: 0; }
  .handling-facts { grid-template-columns: repeat(5, 1fr); }
  .handling-form { grid-template-columns: repeat(3, 1fr); }
  .handling-buttons { align-self: end; }
}

@media (max-width: 820px) {
  .feedback-toolbar { align-items: stretch; flex-direction: column; }
  .feedback-layout { display: block; }
  .feedback-queue, .feedback-main { max-height: none; }
  .feedback-queue { border-right: 0; border-bottom: 1px solid #cbd8dd; }
  .feedback-main { padding: 20px 16px; }
  .feedback-form-grid, .ticket-description dl, .handling-facts, .handling-form { grid-template-columns: 1fr; }
  .feedback-form-grid label.full { grid-column: auto; }
  .feedback-summary button { min-width: 94px; }
}
</style>
