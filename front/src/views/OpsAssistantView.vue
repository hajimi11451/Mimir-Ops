<template>
  <div class="ops-workbench">
    <section class="assistant-context-bar" aria-label="助手运行上下文">
      <div class="assistant-context-bar__heading">
        <span class="assistant-kicker">AI OPERATIONS</span>
        <div>
          <h2>灵枢运维助手</h2>
          <p>先判断咨询或执行意图，再对已连接的目标服务器采取操作。</p>
        </div>
      </div>

      <div class="assistant-context-metrics">
        <div class="context-metric">
          <span class="context-metric__label">WebSocket</span>
          <span class="context-status" :class="connected ? 'is-online' : 'is-offline'">
            <span class="context-status__dot"></span>
            {{ connected ? '已连接' : '未连接' }}
          </span>
        </div>
        <div class="context-metric context-metric--target">
          <span class="context-metric__label">目标服务器</span>
          <strong class="context-metric__mono" :title="serverIp || '未选择'">{{ serverIp || '未选择' }}</strong>
        </div>
        <div class="context-metric">
          <span class="context-metric__label">执行能力</span>
          <span class="capability-badge" :class="hasExecutionCapability ? 'is-ready' : 'is-advisory'">
            {{ hasExecutionCapability ? '可执行' : '仅咨询' }}
          </span>
        </div>
        <div class="context-metric">
          <span class="context-metric__label">最大轮次</span>
          <strong>{{ maxRounds }} 轮</strong>
        </div>
      </div>

      <div class="assistant-context-actions">
        <el-button v-if="!connected" type="primary" @click="connectWs">连接助手</el-button>
        <el-button v-else plain @click="disconnectWs">断开连接</el-button>
      </div>
    </section>

    <div class="assistant-workbench-grid">
      <aside class="connection-panel assistant-panel" aria-label="连接上下文">
        <div class="assistant-panel__header">
          <div>
            <span class="assistant-panel__eyebrow">CONNECTION</span>
            <h3>连接上下文</h3>
          </div>
          <span class="panel-state-dot" :class="hasExecutionCapability ? 'is-ready' : ''"></span>
        </div>

        <div class="connection-panel__body">
          <label class="assistant-field-label" for="saved-connection-select">保存的连接</label>
          <el-select
            id="saved-connection-select"
            v-model="selectedSavedConnection"
            class="connection-select"
            filterable
            clearable
            placeholder="选择已保存连接"
            @change="handleSavedConnectionChange"
          >
            <el-option
              v-for="item in savedConnections"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>

          <div class="target-summary">
            <div class="target-summary__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <rect x="4" y="4" width="16" height="6" rx="2" />
                <rect x="4" y="14" width="16" height="6" rx="2" />
                <path d="M8 7h.01M8 17h.01M12 7h4M12 17h4" />
              </svg>
            </div>
            <div class="target-summary__copy">
              <span>当前目标</span>
              <strong>{{ serverIp || '尚未选择服务器' }}</strong>
              <small>{{ username ? `SSH 用户：${username}` : '选择保存连接，或展开连接信息手动填写' }}</small>
            </div>
          </div>

          <details class="credentials-disclosure">
            <summary>
              <span>
                <strong>连接信息</strong>
                <small>凭据默认折叠</small>
              </span>
              <span class="credentials-disclosure__chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="credentials-fields">
              <label class="assistant-field-label">服务器 IP</label>
              <el-input v-model="serverIp" placeholder="192.168.1.10 或 IP:Port" />

              <label class="assistant-field-label">SSH 用户名</label>
              <el-input v-model="username" placeholder="请输入账号" />

              <label class="assistant-field-label">SSH 密码</label>
              <el-input v-model="password" type="password" show-password placeholder="请输入密码" />

              <label class="assistant-field-label">最大执行轮次</label>
              <el-input-number
                v-model="maxRounds"
                class="rounds-input"
                :min="1"
                :max="50"
                controls-position="right"
              />
            </div>
          </details>

          <div class="intent-note">
            <span class="intent-note__icon" aria-hidden="true">i</span>
            <p>连接完整不等于立即执行。助手仍会先识别咨询或执行意图，只有明确的执行请求才会操作服务器。</p>
          </div>
        </div>
      </aside>

      <section class="conversation-panel assistant-panel" aria-label="对话与输入">
        <div class="assistant-panel__header conversation-panel__header">
          <div>
            <span class="assistant-panel__eyebrow">CONVERSATION</span>
            <h3>对话与执行</h3>
          </div>
          <span class="activity-badge" :class="executionStateMeta.className">
            <span class="activity-badge__dot"></span>
            {{ executionStateMeta.label }}
          </span>
        </div>

        <div ref="chatBox" class="assistant-chat custom-scrollbar" aria-live="polite">
          <div v-if="messages.length === 0" class="assistant-empty-state">
            <div class="assistant-empty-state__mark">AI</div>
            <h4>从一次清晰的运维请求开始</h4>
            <p>你可以先咨询原因，也可以选择服务器后要求检查、修复或生成监控图表。</p>
          </div>

          <article
            v-for="(msg, idx) in messages"
            :key="idx"
            class="assistant-message"
            :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <div class="assistant-message__meta">
              <span class="assistant-message__avatar">{{ msg.role === 'user' ? '你' : 'AI' }}</span>
              <span>{{ msg.role === 'user' ? '你' : '运维助手' }}</span>
            </div>

            <div
              class="assistant-message__bubble"
              :class="[
                msg.role === 'user' ? 'assistant-user-bubble' : 'assistant-agent-bubble',
                `message-type-${msg.type || 'text'}`,
                isMarkdownMessage(msg) ? 'is-markdown' : 'is-plain',
              ]"
            >
              <template v-if="msg.type === 'confirm'">
                <div class="confirm-card confirm-card--standard">
                  <div class="confirm-card__heading">
                    <span class="confirm-card__icon">?</span>
                    <div>
                      <strong>等待执行确认</strong>
                      <small>风险等级：{{ msg.riskLevel }}</small>
                    </div>
                  </div>
                  <pre class="assistant-command-block"><code>{{ msg.command }}</code></pre>
                  <div v-if="!msg.handled" class="confirm-card__actions">
                    <el-button size="small" @click="cancelExecute(msg)">取消</el-button>
                    <el-button size="small" type="primary" @click="confirmExecute(msg)">确定执行</el-button>
                  </div>
                  <div v-else :class="interactiveStatusClass(msg.statusType)">
                    {{ msg.statusText || '已处理' }}
                  </div>
                </div>
              </template>

              <template v-else-if="msg.type === 'timeout'">
                <div class="confirm-card confirm-card--timeout">
                  <div class="confirm-card__heading">
                    <span class="confirm-card__icon">Ⅱ</span>
                    <div>
                      <strong>任务达到最大轮数，已暂停</strong>
                      <small>当前上下文仍保留在本次 WebSocket 会话中</small>
                    </div>
                  </div>
                  <p>是否继续执行？</p>
                  <div v-if="!msg.handled" class="confirm-card__actions">
                    <el-button size="small" @click="cancelContinue(msg)">停止</el-button>
                    <el-button size="small" type="primary" @click="confirmContinue(msg)">继续执行</el-button>
                  </div>
                  <div v-else :class="interactiveStatusClass(msg.statusType)">
                    {{ msg.statusText || '已处理' }}
                  </div>
                </div>
              </template>

              <template v-else-if="msg.type === 'risk_confirm'">
                <div class="confirm-card confirm-card--danger" role="alert">
                  <div class="confirm-card__heading">
                    <span class="confirm-card__icon">!</span>
                    <div>
                      <strong>高风险命令需要明确确认</strong>
                      <small>风险等级：{{ msg.riskLevel || 'high' }}</small>
                    </div>
                  </div>
                  <p v-if="msg.reason" class="confirm-card__reason">{{ msg.reason }}</p>
                  <pre class="assistant-command-block assistant-command-block--danger"><code>{{ msg.command }}</code></pre>
                  <div v-if="!msg.handled" class="confirm-card__actions">
                    <el-button size="small" @click="cancelRiskExecute(msg)">取消，保持安全</el-button>
                    <el-button size="small" type="danger" @click="confirmRiskExecute(msg)">确认风险并执行</el-button>
                  </div>
                  <div v-else :class="interactiveStatusClass(msg.statusType)">
                    {{ msg.statusText || '已处理' }}
                  </div>
                </div>
              </template>

              <template v-else-if="msg.type === 'chart_action'">
                <div class="chart-action-card">
                  <div class="chart-action-card__heading">
                    <span class="chart-action-card__icon">↗</span>
                    <div>
                      <strong>建议生成监控图表</strong>
                      <small>{{ msg.chartTitle }}</small>
                    </div>
                  </div>
                  <dl class="chart-action-card__details">
                    <div><dt>原因</dt><dd>{{ msg.reason }}</dd></div>
                    <div><dt>范围</dt><dd>{{ msg.timeRange }}</dd></div>
                    <div><dt>类型</dt><dd>{{ chartTemplateLabel(msg.chartTemplate) }}</dd></div>
                  </dl>
                  <div v-if="!msg.handled" class="confirm-card__actions">
                    <el-button size="small" @click="cancelChart(msg)">暂不生成</el-button>
                    <el-button size="small" type="primary" @click="generateChart(msg)">生成图表</el-button>
                  </div>
                  <div v-else :class="interactiveStatusClass(msg.statusType)">
                    {{ msg.statusText || '已处理' }}
                  </div>
                </div>
              </template>

              <template v-else-if="msg.type === 'chart_render'">
                <div class="chart-render-card">
                  <InlineMetricsTemplate :title="msg.title" :chart-data="msg.chartData" />
                </div>
              </template>

              <template v-else-if="msg.type === 'rich_markdown'">
                <div class="result-card">
                  <div class="result-card-head">
                    <div>
                      <span v-if="msg.eyebrow" class="result-card__eyebrow">{{ msg.eyebrow }}</span>
                      <strong>{{ msg.title }}</strong>
                      <small v-if="msg.subtitle">{{ msg.subtitle }}</small>
                    </div>
                    <span :class="resultToneClass(msg.tone)">{{ resultToneLabel(msg.tone) }}</span>
                  </div>
                  <div v-if="msg.content" v-html="renderMarkdown(msg.content)" class="markdown-body markdown-result"></div>
                </div>
              </template>

              <template v-else-if="msg.type === 'progress'">
                <div class="execution-feed-message">
                  <span class="execution-feed-message__dot"></span>
                  <div v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
                </div>
              </template>

              <template v-else>
                <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
                <div v-else>{{ msg.content }}</div>
              </template>
            </div>
          </article>
        </div>

        <div class="assistant-composer" :class="{ 'is-running': isAgentRunning }">
          <div v-if="isAgentRunning" class="assistant-running-banner" role="status">
            <span class="assistant-running-banner__pulse"></span>
            <div>
              <strong>任务正在运行</strong>
              <small>输入已锁定，可随时发送中断请求</small>
            </div>
          </div>

          <div class="assistant-composer__row">
            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              resize="none"
              :disabled="isAgentRunning"
              placeholder="例如：检查 nginx 状态并修复"
              @keydown.enter.exact.prevent="sendMessage()"
            />
            <el-button
              :type="isAgentRunning ? 'danger' : 'primary'"
              class="assistant-send-button"
              :class="{ 'assistant-stop-button': isAgentRunning }"
              :disabled="isAgentRunning ? !connected : !input.trim()"
              @click="isAgentRunning ? forceStop() : sendMessage()"
            >
              {{ isAgentRunning ? '中断执行' : '发送请求' }}
            </el-button>
          </div>
          <p v-if="!isAgentRunning" class="assistant-composer__hint">Enter 发送 · Shift + Enter 换行 · 高风险命令会再次请求确认</p>
        </div>
      </section>

      <aside class="execution-panel assistant-panel" aria-label="本次会话执行反馈">
        <div class="assistant-panel__header">
          <div>
            <span class="assistant-panel__eyebrow">LIVE FEEDBACK</span>
            <h3>执行反馈</h3>
          </div>
          <span class="activity-badge activity-badge--compact" :class="executionStateMeta.className">
            {{ executionStateMeta.label }}
          </span>
        </div>

        <div class="execution-panel__body custom-scrollbar">
          <div v-if="executionPlan.length" class="execution-plan">
            <div class="execution-section-title">
              <span>当前计划</span>
              <small>{{ executionPlan.length }} 步</small>
            </div>
            <ol class="execution-plan__list">
              <li
                v-for="(step, index) in executionPlan"
                :key="`${index}-${step.description}`"
                :class="`is-${step.status}`"
              >
                <span class="execution-plan__index">{{ index + 1 }}</span>
                <div>
                  <strong>{{ step.description }}</strong>
                  <div class="execution-plan__tags">
                    <span v-if="step.isRisky" class="is-risk">高风险</span>
                    <span v-if="step.hasRollback" class="is-rollback">可回滚</span>
                    <span>{{ executionStepStatusLabel(step.status) }}</span>
                  </div>
                </div>
              </li>
            </ol>
          </div>

          <div class="execution-events">
            <div class="execution-section-title">
              <span>会话动态</span>
              <small>实时</small>
            </div>
            <div v-if="executionEvents.length" class="execution-events__list">
              <div
                v-for="event in executionEvents"
                :key="event.id"
                class="execution-event"
                :class="`is-${event.tone}`"
              >
                <span class="execution-event__dot"></span>
                <div>
                  <div class="execution-event__heading">
                    <strong>{{ event.title }}</strong>
                    <time>{{ event.time }}</time>
                  </div>
                  <p v-if="event.detail">{{ event.detail }}</p>
                </div>
              </div>
            </div>
            <div v-else class="execution-events__empty">
              <span>◎</span>
              <p>任务开始后，这里会显示计划与实时步骤。</p>
            </div>
          </div>

          <div class="session-boundary-note">
            <strong>本次会话反馈</strong>
            <p>此处用于展示当前 WebSocket 会话进度，不代表持久化执行审计；刷新或断开后，暂停任务可能无法继续。</p>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listConfigs } from '../api/diagnosis'
import InlineMetricsTemplate from '../components/InlineMetricsTemplate.vue'
import MarkdownIt from 'markdown-it'

const PENDING_TASK_KEY = 'opsAssistantPendingTask'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
})

const route = useRoute()
const router = useRouter()

const ws = ref(null)
const connected = ref(false)

const serverIp = ref('')
const username = ref('')
const password = ref('')
const execute = ref(true)
const maxRounds = ref(15)
const isAgentRunning = ref(false)
const selectedSavedConnection = ref('')
const savedConnections = ref([])
const savedConnectionsLoaded = ref(false)
const pendingTaskHandled = ref(false)

const input = ref('')
const messages = ref([])
const chatBox = ref(null)
const lastTaskDoneDigest = ref('')
const lastTaskFailedDigest = ref('')
const stepTitleMap = ref({})
const executionPlan = ref([])
const executionEvents = ref([])
const executionState = ref('idle')

let executionEventSequence = 0

const hasExecutionCapability = computed(() => Boolean(
  String(serverIp.value || '').trim()
  && String(username.value || '').trim()
  && String(password.value || ''),
))

const executionStateMeta = computed(() => {
  const stateMap = {
    idle: { label: '等待请求', className: 'is-idle' },
    analyzing: { label: '识别意图', className: 'is-running' },
    running: { label: '执行中', className: 'is-running' },
    paused: { label: '等待确认', className: 'is-paused' },
    stopping: { label: '正在中断', className: 'is-stopping' },
    stopped: { label: '已中断', className: 'is-stopped' },
    cancelled: { label: '已取消', className: 'is-stopped' },
    done: { label: '已完成', className: 'is-done' },
    failed: { label: '执行失败', className: 'is-failed' },
    offline: { label: '连接断开', className: 'is-offline' },
  }
  return stateMap[executionState.value] || stateMap.idle
})

const compactExecutionText = (value, maxLength = 120) => {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text || text.length <= maxLength) return text
  return `${text.slice(0, maxLength)}...`
}

const resetExecutionFeedback = (state = 'analyzing') => {
  executionPlan.value = []
  executionEvents.value = []
  executionState.value = state
}

const pushExecutionEvent = (title, detail = '', tone = 'info') => {
  executionEventSequence += 1
  executionEvents.value.push({
    id: `${Date.now()}-${executionEventSequence}`,
    title: String(title || '状态更新'),
    detail: compactExecutionText(detail),
    tone,
    time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  })
  if (executionEvents.value.length > 10) {
    executionEvents.value = executionEvents.value.slice(-10)
  }
}

const applyExecutionPlan = plan => {
  executionPlan.value = Array.isArray(plan)
    ? plan.map(item => ({
      description: String(item?.description || '未命名步骤'),
      isRisky: Boolean(item?.isRisky),
      hasRollback: Boolean(item?.hasRollback),
      status: 'pending',
    }))
    : []
}

const updateExecutionPlanStep = (stepIndex, status) => {
  const index = Math.max(0, Number(stepIndex || 1) - 1)
  if (!executionPlan.value[index]) return
  executionPlan.value = executionPlan.value.map((step, currentIndex) => (
    currentIndex === index ? { ...step, status } : step
  ))
}

const updateExecutionPlanStepByDescription = (description, status) => {
  const target = String(description || '').trim()
  if (!target) return
  const index = executionPlan.value.findIndex(step => step.description === target)
  if (index < 0) return
  executionPlan.value = executionPlan.value.map((step, currentIndex) => (
    currentIndex === index ? { ...step, status } : step
  ))
}

const executionStepStatusLabel = status => {
  if (status === 'running') return '进行中'
  if (status === 'done') return '已完成'
  if (status === 'failed') return '异常'
  if (status === 'skipped') return '已跳过'
  return '等待中'
}

const scrollChatToBottom = async () => {
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const appendMessage = async (role, content, type = 'text') => {
  const normalizedContent = role === 'assistant' ? toUserFriendlyText(content) : content
  if (role === 'assistant') {
    await humanLikeDelay()
  }
  messages.value.push({ role, content: normalizedContent, type })
  await scrollChatToBottom()
}

const appendRichMarkdownMessage = async ({
  title,
  content = '',
  subtitle = '',
  tone = 'info',
  eyebrow = '',
}) => {
  messages.value.push({
    role: 'assistant',
    type: 'rich_markdown',
    title: title || '处理结果',
    content,
    subtitle,
    tone,
    eyebrow,
  })
  await scrollChatToBottom()
}

const appendChartActionMessage = async (reason, timeRange, chartTemplate, chartTitle) => {
  messages.value.push({
    role: 'assistant',
    type: 'chart_action',
    reason: reason || '结果包含可视化分析价值',
    timeRange: timeRange || '1h',
    chartTemplate: chartTemplate || 'health_overview',
    chartTitle: chartTitle || '服务器健康总览',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendChartRenderMessage = async chartData => {
  messages.value.push({
    role: 'assistant',
    type: 'chart_render',
    title: chartData?.title || `服务器监控图（${chartData?.timeRange || '1h'}）`,
    chartData,
  })
  await scrollChatToBottom()
}

const appendConfirmMessage = async (query, command, riskLevel) => {
  messages.value.push({
    role: 'assistant',
    type: 'confirm',
    query,
    command,
    riskLevel: riskLevel || 'medium',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendTimeoutMessage = async () => {
  messages.value.push({
    role: 'assistant',
    type: 'timeout',
    handled: false,
  })
  await scrollChatToBottom()
}

const appendRiskConfirmMessage = async (command, riskLevel, reason) => {
  messages.value.push({
    role: 'assistant',
    type: 'risk_confirm',
    command,
    riskLevel: riskLevel || 'high',
    reason: reason || '',
    handled: false,
  })
  await scrollChatToBottom()
}

const markInteractiveHandled = (msg, statusText, statusType = 'info') => {
  msg.handled = true
  msg.statusText = statusText
  msg.statusType = statusType
}

const interactiveStatusClass = type => {
  if (type === 'success') {
    return 'interactive-status is-success'
  }
  if (type === 'danger') {
    return 'interactive-status is-danger'
  }
  return 'interactive-status'
}

const getWsUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/server/connect`
}

const parseServerEndpoint = value => {
  const text = String(value || '').trim()
  if (!text) {
    return { raw: '', host: '', port: '' }
  }

  if (text.startsWith('[')) {
    const endBracket = text.indexOf(']')
    if (endBracket > -1) {
      const host = text.slice(1, endBracket).trim().toLowerCase()
      const suffix = text.slice(endBracket + 1)
      const port = suffix.startsWith(':') ? suffix.slice(1).trim() : ''
      return { raw: text, host, port }
    }
  }

  const lastColonIndex = text.lastIndexOf(':')
  if (lastColonIndex > -1 && text.indexOf(':') === lastColonIndex) {
    const host = text.slice(0, lastColonIndex).trim().toLowerCase()
    const port = text.slice(lastColonIndex + 1).trim()
    if (host && /^\d+$/.test(port)) {
      return { raw: text, host, port }
    }
  }

  return {
    raw: text,
    host: text.toLowerCase(),
    port: '',
  }
}

const isSameServerEndpoint = (left, right) => {
  const a = parseServerEndpoint(left)
  const b = parseServerEndpoint(right)

  if (!a.host || !b.host || a.host !== b.host) {
    return false
  }
  if (a.port === b.port) {
    return true
  }

  // Bare IP and IP:22 should be treated as the same target.
  return (!a.port && b.port === '22') || (!b.port && a.port === '22')
}

const readPendingTask = () => {
  if (pendingTaskHandled.value || route.query.autostart !== '1') return null
  try {
    const raw = sessionStorage.getItem(PENDING_TASK_KEY)
    if (!raw) return null
    return JSON.parse(raw)
  } catch (error) {
    console.error('Failed to parse pending ops task', error)
    return null
  }
}

const clearPendingTask = async () => {
  pendingTaskHandled.value = true
  sessionStorage.removeItem(PENDING_TASK_KEY)
  if (route.query.autostart === '1') {
    await router.replace({ name: 'ops-assistant' })
  }
}

const hasCompleteConnection = () => {
  return Boolean(serverIp.value && username.value && password.value)
}

const tryApplyPendingConnection = pendingTask => {
  const targetIp = String(pendingTask?.serverIp || '').trim()
  if (!targetIp) {
    return hasCompleteConnection()
  }

  const matched = savedConnections.value.find(item => isSameServerEndpoint(item.serverIp, targetIp))
  if (matched) {
    selectedSavedConnection.value = matched.id
    handleSavedConnectionChange(matched.id)
    return true
  }

  serverIp.value = targetIp
  return isSameServerEndpoint(serverIp.value, targetIp) && Boolean(username.value && password.value)
}

const maybeRunPendingTask = async () => {
  const pendingTask = readPendingTask()
  if (!connected.value || !ws.value || !savedConnectionsLoaded.value) {
    return
  }

  if (!pendingTask) {
    if (route.query.autostart === '1') {
      await clearPendingTask()
    }
    return
  }

  const canExecute = tryApplyPendingConnection(pendingTask)
  input.value = String(pendingTask.query || '').trim()

  if (!input.value) {
    await clearPendingTask()
    return
  }

  if (!canExecute) {
    await appendMessage('assistant', '未找到与该告警匹配的服务器连接，请先选择或补全连接信息后再执行。')
    return
  }

  const started = await sendMessage(input.value)
  if (started === false) {
    return
  }
  await clearPendingTask()
}

const connectWs = () => {
  if (connected.value) {
    maybeRunPendingTask()
    return
  }
  const socket = new WebSocket(getWsUrl())

  socket.onopen = async () => {
    ws.value = socket
    connected.value = true
    if (executionState.value === 'offline') {
      executionState.value = 'idle'
    }
    await appendMessage('assistant', '连接已建立，可以开始对话。')
    await maybeRunPendingTask()
  }

  socket.onmessage = async event => {
    try {
      const data = JSON.parse(event.data)
      if (data.type === 'welcome') {
        await appendMessage('assistant', data.message || '欢迎使用运维助手。')
        return
      }
      if (data.type === 'status') {
        const status = String(data.status || '').toUpperCase()
        if (status === 'PLANNING') executionState.value = 'analyzing'
        if (status === 'RUNNING') executionState.value = 'running'
        if (status === 'PAUSED') executionState.value = 'paused'
        if (status === 'DONE') executionState.value = 'done'
        if (status === 'FAILED') executionState.value = 'failed'
        return
      }
      if (data.type === 'plan') {
        executionState.value = 'running'
        applyExecutionPlan(data.plan)
        pushExecutionEvent('执行计划已生成', `共 ${executionPlan.value.length} 个步骤`, 'info')
        await appendRichMarkdownMessage({
          eyebrow: '执行计划',
          title: '我先整理了一份处理步骤',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'info',
          content: formatPlanMessage(data.plan),
        })
        return
      }
      if (data.type === 'step_start') {
        const stepIndex = Number(data.index || 0)
        executionState.value = 'running'
        updateExecutionPlanStep(stepIndex, 'running')
        if (stepIndex > 0 && data.description) {
          stepTitleMap.value = {
            ...stepTitleMap.value,
            [stepIndex]: String(data.description),
          }
        }
        const stepMessage = formatStepStartMessage(data)
        pushExecutionEvent(
          `开始步骤 ${stepIndex || ''}`.trim(),
          data.description || stepMessage,
          'info',
        )
        if (stepMessage) {
          await appendMessage('assistant', stepMessage, 'progress')
        }
        return
      }
      if (data.type === 'step_done') {
        updateExecutionPlanStep(data.index, 'done')
        const stepMessage = formatStepDoneMessage(data)
        pushExecutionEvent(`步骤 ${data.index || ''} 已完成`.trim(), data.result || stepMessage, 'success')
        if (stepMessage) {
          await appendMessage('assistant', stepMessage, 'progress')
        }
        return
      }
      if (data.type === 'step_failed') {
        updateExecutionPlanStepByDescription(data.description, 'failed')
        pushExecutionEvent('步骤执行异常', data.error || data.reason, 'danger')
        await appendRichMarkdownMessage({
          eyebrow: '执行异常',
          title: '某个步骤没有顺利完成',
          tone: 'danger',
          content: formatStepFailedMessage(data),
        })
        return
      }
      if (data.type === 'task_done') {
        isAgentRunning.value = false
        executionState.value = 'done'
        pushExecutionEvent('任务执行完成', data.summary, 'success')
        lastTaskDoneDigest.value = normalizeComparableText(data.summary)
        lastTaskFailedDigest.value = ''
        await appendRichMarkdownMessage({
          eyebrow: '执行完成',
          title: '处理结果已经整理好了',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'success',
          content: formatTaskDoneMessage(data),
        })
        return
      }
      if (data.type === 'task_failed') {
        isAgentRunning.value = false
        executionState.value = 'failed'
        pushExecutionEvent('任务执行失败', data.error, 'danger')
        lastTaskFailedDigest.value = normalizeComparableText(data.error)
        lastTaskDoneDigest.value = ''
        await appendRichMarkdownMessage({
          eyebrow: '任务失败',
          title: '这次处理没有顺利完成',
          subtitle: serverIp.value ? `目标服务器：${serverIp.value}` : '',
          tone: 'danger',
          content: formatTaskFailedMessage(data),
        })
        return
      }
      if (data.type === 'tool_call') {
        return
      }
      if (data.type === 'ops_progress') {
        const stage = String(data.stage || '')
        if (['agent_finish', 'agent_timeout', 'agent_stopped', 'agent_stop', 'finished'].includes(stage)) {
          isAgentRunning.value = false
        }
        if (stage === 'agent_timeout') executionState.value = 'paused'
        if (stage === 'agent_stopped' || stage === 'agent_stop') executionState.value = 'stopped'

        const progressText = formatProgressForUser(data)
        const eventStageMap = {
          intent_detecting: ['正在识别请求意图', 'info'],
          intent_result: ['意图识别完成', 'info'],
          cmd_exec_start: ['开始执行命令', 'warning'],
          cmd_exec_done: ['命令执行完成', 'success'],
          cmd_exec_fail: ['命令执行失败', 'danger'],
          log_read_start: ['开始读取日志', 'info'],
          log_read_done: ['日志读取完成', 'success'],
          metrics_fetch_start: ['开始获取监控数据', 'info'],
          metrics_fetch_done: ['监控数据获取完成', 'success'],
          step_retry: ['正在重试步骤', 'warning'],
          step_skip: ['已跳过当前步骤', 'warning'],
          agent_timeout: ['任务达到最大轮次', 'warning'],
          agent_stopped: ['任务已中断', 'danger'],
          agent_stop: ['任务已中断', 'danger'],
        }
        const eventMeta = eventStageMap[stage]
        if (eventMeta) {
          pushExecutionEvent(eventMeta[0], progressText || data.message, eventMeta[1])
        }
        if (progressText) {
          await appendMessage('assistant', progressText, 'progress')
        }
        return
      }
      if (data.type === 'ops_chat_result') {
        isAgentRunning.value = false
        if (data.needRiskConfirm || data.timeout) {
          executionState.value = 'paused'
        } else if (data.stopped) {
          executionState.value = 'stopped'
        } else if (data.executed || data.chatOnly) {
          executionState.value = 'done'
        } else if (data.execResult) {
          executionState.value = 'failed'
        }
        const summary = formatOpsResult(data)
        if (shouldAppendOpsResultSummary(data, summary)) {
          await appendMessage('assistant', summary)
        }

        if (data.needRiskConfirm && data.riskCommand) {
          pushExecutionEvent('等待高风险确认', data.riskCommand, 'danger')
          await appendRiskConfirmMessage(data.riskCommand, data.riskLevel, data.reply)
        } else if (data.timeout) {
          pushExecutionEvent('任务已暂停', '达到最大执行轮次，可在当前会话中继续。', 'warning')
          await appendTimeoutMessage()
        }

        if (!execute.value && !data.executed && data.hasCommand && data.command) {
          await appendConfirmMessage(data.query, data.command, data.riskLevel)
        }
        if (data.chartSuggest) {
          await appendChartActionMessage(data.chartReason, data.chartTimeRange, data.chartTemplate, data.chartTitle)
        }
        return
      }
      if (data.type === 'ops_force_stop_result' || data.type === 'ops_stop_ack') {
        isAgentRunning.value = false
        executionState.value = 'stopped'
        pushExecutionEvent('任务已中断', data.message, 'danger')
        await appendMessage('assistant', data.message || '已发送强制停止请求。')
        return
      }
      if (data.type === 'chart_data_result') {
        if (!data.success) {
          await appendMessage('assistant', data.message || '生成图表失败。')
          return
        }
        await appendMessage('assistant', '图表数据已生成，正在渲染...')
        await appendChartRenderMessage(data.chartData || {})
        return
      }
      await appendMessage('assistant', typeof event.data === 'string' ? event.data : JSON.stringify(data))
    } catch (error) {
      await appendMessage('assistant', String(event.data || '收到未知响应'))
    }
  }

  socket.onclose = async () => {
    connected.value = false
    isAgentRunning.value = false
    ws.value = null
    executionState.value = 'offline'
    pushExecutionEvent('WebSocket 已断开', '暂停任务的会话上下文可能已失效。', 'warning')
    await appendMessage('assistant', '连接已断开。')
  }

  socket.onerror = async () => {
    isAgentRunning.value = false
    executionState.value = 'offline'
    pushExecutionEvent('连接发生异常', '请检查后端服务或网络状态。', 'danger')
    await appendMessage('assistant', '连接异常，请检查后端服务。')
  }
}

const disconnectWs = () => {
  if (ws.value) {
    ws.value.close()
  }
}

const HIGH_RISK_KEYWORDS = [
  'rm -rf /', 'rm -rf /*', 'rm -rf ~', 'rm -rf /root', 'rm -rf /home',
  'mkfs', 'mkfs.ext', 'mkfs.xfs', 'mkfs.ntfs',
  'shutdown', 'shutdown -h', 'shutdown -r', 'poweroff', 'halt', 'reboot',
  'dd if=/dev/zero', 'dd if=/dev/random', 'dd if=/dev/urandom',
  'chmod -R 777 /', 'chmod 777 /',
  'userdel -r root', 'userdel root',
  '> /etc/passwd', '> /etc/shadow', '> /etc/hosts',
  ':(){ :|:& };:', 'fork bomb'
]

const containsHighRiskCommand = (text) => {
  const lowerText = text.toLowerCase()
  return HIGH_RISK_KEYWORDS.some(keyword => lowerText.includes(keyword.toLowerCase()))
}

const sendMessage = async presetText => {
  if (isAgentRunning.value) return false
  const text = typeof presetText === 'string' ? presetText.trim() : input.value.trim()
  if (!text) return false

  if (containsHighRiskCommand(text)) {
    await appendMessage('assistant', '**安全拦截**：检测到输入包含高风险系统命令（如 rm -rf、shutdown、mkfs 等），该请求已被前端安全层拦截。如需执行服务器维护操作，请使用自然语言描述需求，由 AI 生成安全的处置方案。')
    input.value = ''
    return false
  }

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，正在自动连接...')
    connectWs()
    return false
  }

  isAgentRunning.value = true
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  const shouldExecute = hasCompleteConnection()
  execute.value = shouldExecute
  resetExecutionFeedback('analyzing')
  pushExecutionEvent(
    shouldExecute ? '执行请求已提交' : '咨询请求已提交',
    shouldExecute ? `目标服务器：${serverIp.value}` : '当前未提供完整连接，将只返回分析建议。',
    'info',
  )

  const payload = {
    type: 'ops_chat',
    query: text,
    execute: shouldExecute,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }

  try {
    await appendMessage('user', text)
    await appendMessage('assistant', '收到，我先分析一下你的请求。')
    ws.value.send(JSON.stringify(payload))
    input.value = ''
    return true
  } catch (error) {
    isAgentRunning.value = false
    executionState.value = 'failed'
    pushExecutionEvent('请求发送失败', error?.message, 'danger')
    await appendMessage('assistant', error?.message || '发送失败，请稍后重试。')
    return false
  }
}

const executeByConfirmation = async query => {
  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法执行命令。')
    return false
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写服务器连接信息后再执行。')
    return false
  }
  if (!query) {
    await appendMessage('assistant', '缺少原始指令，无法执行。')
    return false
  }

  await appendMessage('assistant', '收到，正在执行中...')
  resetExecutionFeedback('analyzing')
  pushExecutionEvent('已确认执行请求', `目标服务器：${serverIp.value}`, 'info')
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  ws.value.send(JSON.stringify({
    type: 'ops_chat',
    query,
    execute: true,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }))
  isAgentRunning.value = true
  return true
}

const confirmExecute = async msg => {
  if (msg.handled) return
  const started = await executeByConfirmation(msg.query)
  if (started) {
    markInteractiveHandled(msg, '已确认，正在执行。', 'success')
  }
}

const cancelExecute = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消执行。', 'info')
  executionState.value = 'cancelled'
  pushExecutionEvent('执行已取消', '用户未确认该执行请求。', 'warning')
  await appendMessage('assistant', '已取消执行。')
}

const confirmRiskExecute = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法执行高风险命令。')
    return
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写服务器连接信息后再执行。')
    return
  }

  await appendMessage('assistant', '收到高风险执行确认，正在执行中...')
  isAgentRunning.value = true
  executionState.value = 'running'
  pushExecutionEvent('高风险命令已获确认', msg.command, 'danger')
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''
  stepTitleMap.value = {}
  markInteractiveHandled(msg, '已确认高风险执行，正在处理。', 'danger')
  ws.value.send(JSON.stringify({
    type: 'risk_execute',
    command: msg.command,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
  }))
}

const cancelRiskExecute = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消高风险命令执行。', 'info')
  executionState.value = 'cancelled'
  pushExecutionEvent('高风险命令已取消', msg.command, 'warning')
  await appendMessage('assistant', '已取消高风险命令执行。')
}

const confirmContinue = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法继续执行。')
    return
  }

  await appendMessage('user', '继续')
  await appendMessage('assistant', '收到，正在继续执行中...')
  markInteractiveHandled(msg, '已确认继续执行。', 'success')
  executionState.value = 'running'
  pushExecutionEvent('任务继续执行', `本轮最多 ${Math.min(50, Math.max(1, Number(maxRounds.value) || 15))} 轮`, 'info')
  lastTaskDoneDigest.value = ''
  lastTaskFailedDigest.value = ''

  const payload = {
    type: 'ops_chat',
    query: '继续',
    execute: true,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }

  ws.value.send(JSON.stringify(payload))
  isAgentRunning.value = true
}

const cancelContinue = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已停止任务。', 'info')
  executionState.value = 'stopped'
  pushExecutionEvent('暂停任务已停止', '用户选择不再继续。', 'warning')
  await appendMessage('assistant', '已停止任务。')
}

const generateChart = async msg => {
  if (msg.handled) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法生成图表。')
    return
  }
  if (!serverIp.value) {
    await appendMessage('assistant', '请先填写服务器 IP。')
    return
  }

  await appendMessage('assistant', '正在生成图表数据...')
  markInteractiveHandled(msg, '图表生成请求已提交。', 'success')
  ws.value.send(JSON.stringify({
    type: 'chart_data_request',
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    timeRange: msg.timeRange || '1h',
    chartTemplate: msg.chartTemplate || 'health_overview',
    chartTitle: msg.chartTitle || '服务器健康总览',
  }))
}

const cancelChart = async msg => {
  if (msg.handled) return
  markInteractiveHandled(msg, '已取消图表生成。', 'info')
  await appendMessage('assistant', '已取消图表生成。')
}

const forceStop = async () => {
  if (!connected.value || !ws.value) {
    isAgentRunning.value = false
    executionState.value = 'offline'
    await appendMessage('assistant', '连接未建立，无法强制停止。')
    return
  }
  executionState.value = 'stopping'
  pushExecutionEvent('正在中断任务', '强制停止请求已发送。', 'danger')
  ws.value.send(JSON.stringify({ type: 'ops_force_stop' }))
  await appendMessage('assistant', '已发送强制停止请求...')
}

const loadSavedConnections = async () => {
  try {
    const configs = await listConfigs()
    if (!Array.isArray(configs)) return

    const unique = new Map()
    configs.forEach(item => {
      const ip = item?.serverIp || ''
      const user = item?.username || ''
      const pass = item?.password || ''
      if (!ip || !user || !pass) return
      const key = `${ip}__${user}__${pass}`
      if (!unique.has(key)) {
        unique.set(key, {
          id: key,
          serverIp: ip,
          username: user,
          password: pass,
          label: `${ip} | ${user}`,
        })
      }
    })
    savedConnections.value = Array.from(unique.values())
  } catch (error) {
    console.error('Failed to load saved connections', error)
  } finally {
    savedConnectionsLoaded.value = true
    await maybeRunPendingTask()
  }
}

const handleSavedConnectionChange = value => {
  const target = savedConnections.value.find(item => item.id === value)
  if (!target) return
  serverIp.value = target.serverIp
  username.value = target.username
  password.value = target.password
}

const renderMarkdown = content => {
  if (!content) return ''
  return md.render(content)
}

const isMarkdownMessage = msg => {
  return msg?.type === 'rich_markdown' || (msg?.role === 'assistant' && msg?.type === 'text')
}

const formatOpsResult = data => {
  const lines = []
  const replySummary = toUserFriendlyText(data.reply)

  if (replySummary) {
    lines.push(replySummary)
  } else {
    lines.push(data.executed ? '处理完成。' : '我已整理好处理建议。')
  }

  if (data.chatOnly) {
    return lines.join('\n\n')
  }

  if (data.executed) {
    const execResult = toUserFriendlyText(data.execResult)
    if (execResult && !isGenericExecResult(execResult)) {
      lines.push(`> ${execResult}`)
    }
  } else {
    if (data.needRiskConfirm) {
      lines.push('涉及高风险操作，需要你确认后我再继续。')
      lines.push(`待确认命令：\`${data.riskCommand || ''}\``)
      return lines.join('\n\n')
    }
    if (data.hasCommand && data.command) {
      lines.push(`建议命令：\`${data.command}\``)
    }
    lines.push(`风险等级：${data.riskLevel || 'medium'}`)
  }
  return lines.join('\n\n')
}

const formatPlanMessage = plan => {
  if (!Array.isArray(plan) || plan.length === 0) {
    return '我会先检查现状，再根据结果决定是否需要执行变更。'
  }

  const lines = ['我会按下面的顺序推进：', '']
  plan.forEach((item, index) => {
    const description = String(item?.description || `步骤 ${index + 1}`).trim()
    const tags = []
    if (item?.isRisky) {
      tags.push('高风险')
    }
    if (item?.hasRollback) {
      tags.push('可回滚')
    }
    const tagSuffix = tags.length ? `  _(${tags.join(' / ')})_` : ''
    lines.push(`${index + 1}. ${description}${tagSuffix}`)
  })
  return lines.join('\n')
}

const formatTaskDoneMessage = data => {
  const summary = String(data?.summary || '').trim()
  if (!summary) {
    return '本次处理已经完成，当前没有更多补充信息。'
  }
  return summary
}

const formatTaskFailedMessage = data => {
  const error = toUserFriendlyText(data?.error || '')
  const lines = ['这次执行在处理中断了。']
  if (error) {
    lines.push(`**原因**：${error}`)
  }
  lines.push('你可以调整指令后重新执行，或者让我继续针对失败点做进一步排查。')
  return lines.join('\n\n')
}

const formatStepStartMessage = data => {
  const description = String(data?.description || '').trim()
  const index = Number(data?.index || 0)
  const total = Number(data?.total || 0)

  if (description && index > 0 && total > 0) {
    return `正在处理第 ${index}/${total} 步：**${description}**`
  }
  if (description) {
    return `正在处理：**${description}**`
  }
  return ''
}

const formatStepDoneMessage = data => {
  const index = Number(data?.index || 0)
  const description = stepTitleMap.value[index]
  const result = toUserFriendlyText(data?.result || '')

  if (description && result) {
    return `已完成：**${description}**\n\n> ${truncateText(result, 140)}`
  }
  if (description) {
    return `已完成：**${description}**`
  }
  if (result) {
    return `已完成一个步骤。\n\n> ${truncateText(result, 140)}`
  }
  return '已完成一个步骤。'
}

const formatStepFailedMessage = data => {
  const lines = []
  const description = String(data?.description || '').trim()
  const error = toUserFriendlyText(data?.error || '')
  const action = actionLabel(data?.action)
  const reason = toUserFriendlyText(data?.reason || '')

  if (description) {
    lines.push(`**出问题的步骤**：${description}`)
  }
  if (error) {
    lines.push(`**错误信息**：${error}`)
  }
  if (action) {
    lines.push(`**系统处理**：${action}`)
  }
  if (reason) {
    lines.push(`**原因说明**：${reason}`)
  }

  return lines.join('\n\n')
}

const chartTemplateLabel = template => {
  switch (template) {
    case 'cpu_mem_trend':
      return 'CPU / 内存趋势图'
    case 'anomaly_timeline':
      return '异常时序图'
    case 'health_score_radar':
      return '健康评分雷达图'
    default:
      return '服务器健康总览'
  }
}

const toUserFriendlyText = text => {
  return String(text || '')
    .replace(/第\s*\d+\s*轮[:：]?\s*/g, '')
    .replace(/（\s*\d+\s*ms\s*）/gi, '')
    .replace(/\(\s*\d+\s*ms\s*\)/gi, '')
    .replace(/\[进度\]\s*/g, '')
    .replace(/^收到，正在执行中\.\.\.$/g, '收到，我开始处理了。')
    .replace(/^收到，正在处理中\.\.\.$/g, '收到，我先帮你分析一下。')
    .replace(/^Agent 循环已完成[。.]?/g, '处理已经完成。')
    .trim()
}

const formatProgressForUser = data => {
  const stage = String(data?.stage || '')
  const message = toUserFriendlyText(data?.message || '')

  if (['plan_ready', 'step_start', 'step_done', 'task_done', 'task_failed', 'tool_call', 'agent_think', 'finished'].includes(stage)) {
    return ''
  }
  if (stage === 'start') {
    return '已经收到你的请求，开始处理。'
  }
  if (stage === 'planning') {
    return '我先分析目标，整理执行计划。'
  }
  if (stage === 'intent_detecting' || stage === 'intent_result') {
    return ''
  }
  if (stage === 'cmd_exec_start') {
    if (!message) {
      return '正在执行命令...'
    }
    return `正在执行命令：\`${truncateText(message.replace(/^执行命令[:：]\s*/g, ''), 120)}\``
  }
  if (stage === 'cmd_exec_done') {
    return ''
  }
  if (stage === 'cmd_exec_fail') {
    return `命令执行失败：\`${truncateText(data.command || '未知命令', 120)}\``
  }
  if (stage === 'metrics_fetch_start') {
    return '正在读取服务器监控数据...'
  }
  if (stage === 'metrics_fetch_done') {
    return ''
  }
  if (stage === 'log_read_start') {
    return '正在检查相关日志...'
  }
  if (stage === 'log_read_done') {
    return ''
  }
  if (stage === 'risk_exec_start') {
    return '正在执行你确认的高风险命令...'
  }
  if (stage === 'risk_exec_done') {
    return '高风险命令执行完成。'
  }
  if (stage === 'step_retry') {
    return '刚才的步骤没有成功，我正在自动重试。'
  }
  if (stage === 'step_skip') {
    return '有一个步骤已跳过，我会继续处理后续内容。'
  }
  if (stage === 'agent_timeout') {
    return '这次处理超出了当前轮数限制，已经先暂停。'
  }
  if (stage === 'agent_stopped' || stage === 'agent_stop') {
    return '任务已强制停止。'
  }

  return ''
}

const isGenericExecResult = text => {
  const normalized = String(text || '').trim()
  return [
    'Agent 循环已完成。',
    'Agent 循环已完成，请参考上方实时进度日志。',
    '高风险命令执行并后续流程已完成。',
    '任务已被强制停止。',
    '达到最大轮数，任务暂停。你可以发送“继续”来恢复执行。',
  ].includes(normalized)
}

const normalizeComparableText = text => {
  return String(text || '')
    .replace(/\s+/g, ' ')
    .trim()
}

const shouldAppendOpsResultSummary = (data, summary) => {
  const currentDigest = normalizeComparableText(data?.reply || summary)
  if (!currentDigest) {
    return false
  }

  if (data?.executed && lastTaskDoneDigest.value && currentDigest === lastTaskDoneDigest.value) {
    return false
  }
  if (!data?.executed && lastTaskFailedDigest.value && currentDigest === lastTaskFailedDigest.value) {
    return false
  }
  return true
}

const truncateText = (text, maxLength = 120) => {
  const value = String(text || '').trim()
  if (!value || value.length <= maxLength) {
    return value
  }
  return `${value.slice(0, maxLength)}...`
}

const actionLabel = action => {
  switch (String(action || '').toLowerCase()) {
    case 'retry':
      return '系统决定重试当前步骤'
    case 'skip':
      return '系统决定跳过当前步骤'
    case 'abort':
      return '系统决定终止本次任务'
    default:
      return ''
  }
}

const resultToneClass = tone => {
  if (tone === 'success') {
    return 'result-tone result-tone-success'
  }
  if (tone === 'danger') {
    return 'result-tone result-tone-danger'
  }
  return 'result-tone result-tone-info'
}

const resultToneLabel = tone => {
  if (tone === 'success') {
    return '已完成'
  }
  if (tone === 'danger') {
    return '需处理'
  }
  return '进行中'
}

const humanLikeDelay = () => {
  const delay = Math.floor(Math.random() * 401) + 100
  return new Promise(resolve => setTimeout(resolve, delay))
}

onMounted(() => {
  connectWs()
  loadSavedConnections()
})

onUnmounted(() => {
  disconnectWs()
})
</script>

<style scoped>
.ops-workbench {
  --assistant-canvas: #f7f4e8;
  --assistant-panel: #fffdf6;
  --assistant-panel-soft: #f3eddb;
  --assistant-ink: #4f3b2b;
  --assistant-copy: #725d42;
  --assistant-muted: #8f806d;
  --assistant-border: #ddd2b9;
  --assistant-brand: #19bfae;
  --assistant-brand-hover: #31cdbc;
  --assistant-brand-active: #109b8f;
  --assistant-success: #69ad38;
  --assistant-warning: #e6ab20;
  --assistant-danger: #d95656;
  --assistant-info: #6f91c8;
  --assistant-code: #2b2118;

  display: grid;
  min-width: 0;
  gap: 1rem;
  color: var(--assistant-copy);
  font-family: Nunito, "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.assistant-context-bar,
.assistant-panel {
  border: 1px solid var(--assistant-border);
  background: var(--assistant-panel);
  box-shadow: 0 0.75rem 2.25rem rgba(92, 68, 39, 0.08);
}

.assistant-context-bar {
  display: grid;
  grid-template-columns: minmax(14rem, 1fr) auto auto;
  align-items: center;
  gap: 1.25rem;
  border-radius: 1.5rem;
  padding: 1rem 1.125rem;
}

.assistant-context-bar__heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.875rem;
}

.assistant-context-bar__heading h2,
.assistant-panel__header h3,
.assistant-empty-state h4 {
  margin: 0;
  color: var(--assistant-ink);
}

.assistant-context-bar__heading h2 {
  font-size: clamp(1.25rem, 2vw, 1.65rem);
  font-weight: 800;
  letter-spacing: -0.025em;
}

.assistant-context-bar__heading p {
  margin: 0.2rem 0 0;
  color: var(--assistant-muted);
  font-size: 0.78rem;
  line-height: 1.5;
}

.assistant-kicker {
  display: inline-grid;
  min-width: 3rem;
  height: 3rem;
  place-items: center;
  border-radius: 1rem 1rem 1rem 0.35rem;
  background: #d8f3ea;
  color: #0b7c72;
  font-size: 0.62rem;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.assistant-context-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(6.5rem, auto));
  align-items: stretch;
  border: 1px solid #e8dfca;
  border-radius: 1rem;
  background: #faf6e9;
}

.context-metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  gap: 0.25rem;
  padding: 0.68rem 0.9rem;
}

.context-metric + .context-metric {
  border-left: 1px solid #e8dfca;
}

.context-metric__label {
  color: var(--assistant-muted);
  font-size: 0.68rem;
  font-weight: 700;
}

.context-metric strong,
.context-status,
.capability-badge {
  color: var(--assistant-ink);
  font-size: 0.78rem;
  font-weight: 800;
}

.context-metric__mono {
  max-width: 10rem;
  overflow: hidden;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-status,
.capability-badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 0.4rem;
}

.context-status__dot,
.activity-badge__dot,
.panel-state-dot {
  width: 0.52rem;
  height: 0.52rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #b9ad98;
}

.context-status.is-online {
  color: #4f8d2e;
}

.context-status.is-online .context-status__dot {
  background: var(--assistant-success);
  box-shadow: 0 0 0 0.22rem rgba(105, 173, 56, 0.14);
}

.context-status.is-offline {
  color: var(--assistant-muted);
}

.capability-badge.is-ready {
  color: #087a6f;
}

.capability-badge.is-advisory {
  color: #a27309;
}

.assistant-context-actions {
  display: flex;
  justify-content: flex-end;
}

.assistant-workbench-grid {
  display: grid;
  min-width: 0;
  gap: 1rem;
}

.assistant-panel {
  min-width: 0;
  overflow: hidden;
  border-radius: 1.5rem;
}

.assistant-panel__header {
  display: flex;
  min-height: 4.65rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  border-bottom: 1px solid #e8dfca;
  padding: 1rem 1.1rem;
}

.assistant-panel__header h3 {
  margin-top: 0.18rem;
  font-size: 1rem;
  font-weight: 800;
}

.assistant-panel__eyebrow,
.result-card__eyebrow {
  display: block;
  color: var(--assistant-muted);
  font-size: 0.63rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.panel-state-dot.is-ready {
  background: var(--assistant-brand);
  box-shadow: 0 0 0 0.24rem rgba(25, 191, 174, 0.14);
}

.connection-panel__body,
.execution-panel__body {
  padding: 1.1rem;
}

.connection-panel__body {
  display: grid;
  align-content: start;
  gap: 0.85rem;
}

.assistant-field-label {
  color: var(--assistant-copy);
  font-size: 0.74rem;
  font-weight: 800;
}

.connection-select,
.rounds-input {
  width: 100%;
}

.target-summary {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  border: 1px solid #d9d9bd;
  border-radius: 1.1rem;
  background: #f5f5df;
  padding: 0.85rem;
}

.target-summary__icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 0.85rem;
  background: #d8f3ea;
  color: #0b7c72;
}

.target-summary__icon svg {
  width: 1.3rem;
  height: 1.3rem;
  stroke-width: 1.8;
}

.target-summary__copy {
  display: grid;
  min-width: 0;
  gap: 0.18rem;
}

.target-summary__copy span,
.target-summary__copy small,
.credentials-disclosure small {
  color: var(--assistant-muted);
  font-size: 0.68rem;
}

.target-summary__copy strong {
  overflow-wrap: anywhere;
  color: var(--assistant-ink);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 0.82rem;
}

.credentials-disclosure {
  overflow: hidden;
  border: 1px solid var(--assistant-border);
  border-radius: 1rem;
  background: #fffaf0;
}

.credentials-disclosure summary {
  display: flex;
  min-height: 3rem;
  cursor: pointer;
  list-style: none;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 0.9rem;
}

.credentials-disclosure summary::-webkit-details-marker {
  display: none;
}

.credentials-disclosure summary > span:first-child {
  display: grid;
  gap: 0.1rem;
}

.credentials-disclosure summary strong {
  color: var(--assistant-ink);
  font-size: 0.78rem;
}

.credentials-disclosure__chevron {
  color: var(--assistant-muted);
  font-size: 1.1rem;
  transition: transform 180ms cubic-bezier(0.4, 0, 0.2, 1);
}

.credentials-disclosure[open] .credentials-disclosure__chevron {
  transform: rotate(180deg);
}

.credentials-fields {
  display: grid;
  gap: 0.55rem;
  border-top: 1px solid #e8dfca;
  padding: 0.9rem;
}

.credentials-fields .assistant-field-label:not(:first-child) {
  margin-top: 0.3rem;
}

.intent-note,
.session-boundary-note {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  border: 1px solid #e7d7a8;
  border-radius: 1rem;
  background: #fff7d9;
  padding: 0.78rem;
}

.intent-note__icon {
  display: grid;
  width: 1.25rem;
  height: 1.25rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: var(--assistant-warning);
  color: #fffdf6;
  font-size: 0.68rem;
  font-weight: 900;
}

.intent-note p,
.session-boundary-note p {
  margin: 0;
  color: #806729;
  font-size: 0.7rem;
  line-height: 1.55;
}

.conversation-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.conversation-panel__header {
  flex: 0 0 auto;
}

.activity-badge {
  display: inline-flex;
  min-height: 1.75rem;
  align-items: center;
  gap: 0.42rem;
  border: 1px solid #dfd6c2;
  border-radius: 999px;
  background: #f6f1e5;
  padding: 0.3rem 0.62rem;
  color: var(--assistant-muted);
  font-size: 0.68rem;
  font-weight: 800;
  white-space: nowrap;
}

.activity-badge--compact {
  min-height: 1.55rem;
  padding-inline: 0.5rem;
  font-size: 0.62rem;
}

.activity-badge.is-running {
  border-color: rgba(25, 191, 174, 0.32);
  background: rgba(25, 191, 174, 0.1);
  color: #087a6f;
}

.activity-badge.is-running .activity-badge__dot {
  background: var(--assistant-brand);
  animation: assistant-pulse 1.4s ease-in-out infinite;
}

.activity-badge.is-paused,
.activity-badge.is-stopping {
  border-color: rgba(230, 171, 32, 0.35);
  background: rgba(230, 171, 32, 0.1);
  color: #9a6c00;
}

.activity-badge.is-done {
  border-color: rgba(105, 173, 56, 0.35);
  background: rgba(105, 173, 56, 0.1);
  color: #4f8d2e;
}

.activity-badge.is-failed,
.activity-badge.is-stopped {
  border-color: rgba(217, 86, 86, 0.3);
  background: rgba(217, 86, 86, 0.08);
  color: #b63d3d;
}

.assistant-chat {
  display: flex;
  min-height: 30rem;
  height: clamp(30rem, calc(100vh - 22rem), 44rem);
  flex-direction: column;
  gap: 1rem;
  overflow-x: hidden;
  overflow-y: auto;
  background: var(--assistant-panel-soft);
  padding: 1.1rem;
  scroll-behavior: smooth;
}

.assistant-empty-state {
  display: grid;
  max-width: 28rem;
  margin: auto;
  justify-items: center;
  gap: 0.55rem;
  padding: 2rem;
  text-align: center;
}

.assistant-empty-state__mark {
  display: grid;
  width: 3.4rem;
  height: 3.4rem;
  place-items: center;
  border-radius: 1.2rem 1.2rem 1.2rem 0.4rem;
  background: #d8f3ea;
  color: #0b7c72;
  font-weight: 900;
}

.assistant-empty-state h4 {
  font-size: 1rem;
}

.assistant-empty-state p {
  margin: 0;
  color: var(--assistant-muted);
  font-size: 0.78rem;
  line-height: 1.65;
}

.assistant-message {
  display: flex;
  width: 100%;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.32rem;
}

.assistant-message.is-user {
  align-items: flex-end;
}

.assistant-message__meta {
  display: flex;
  align-items: center;
  gap: 0.38rem;
  color: var(--assistant-muted);
  font-size: 0.66rem;
  font-weight: 800;
}

.assistant-message.is-user .assistant-message__meta {
  flex-direction: row-reverse;
}

.assistant-message__avatar {
  display: grid;
  width: 1.45rem;
  height: 1.45rem;
  place-items: center;
  border: 1px solid #d6cbb4;
  border-radius: 50%;
  background: var(--assistant-panel);
  color: var(--assistant-copy);
  font-size: 0.58rem;
}

.assistant-message__bubble {
  max-width: min(88%, 48rem);
  border: 1px solid #ded3bb;
  border-radius: 1.15rem;
  padding: 0.8rem 0.95rem;
  font-size: 0.82rem;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.assistant-message__bubble.is-plain {
  white-space: pre-wrap;
}

.assistant-user-bubble {
  border-color: #109b8f;
  border-bottom-right-radius: 0.35rem;
  background: var(--assistant-brand);
  box-shadow: 0 0.22rem 0 #0f968b;
  color: #fff;
}

.assistant-agent-bubble {
  border-bottom-left-radius: 0.35rem;
  background: var(--assistant-panel);
  color: var(--assistant-copy);
}

.message-type-risk_confirm,
.message-type-chart_render,
.message-type-rich_markdown,
.message-type-confirm,
.message-type-timeout,
.message-type-chart_action {
  width: min(100%, 48rem);
  max-width: 100%;
}

.message-type-progress {
  border-color: #e1d7c1;
  background: #f8f3e8;
  padding-block: 0.62rem;
}

.confirm-card,
.chart-action-card,
.result-card {
  display: grid;
  gap: 0.78rem;
}

.confirm-card__heading,
.chart-action-card__heading {
  display: flex;
  align-items: flex-start;
  gap: 0.68rem;
}

.confirm-card__heading > div,
.chart-action-card__heading > div,
.result-card-head > div {
  display: grid;
  min-width: 0;
  gap: 0.12rem;
}

.confirm-card__heading strong,
.chart-action-card__heading strong,
.result-card-head strong {
  color: var(--assistant-ink);
  font-size: 0.86rem;
}

.confirm-card__heading small,
.chart-action-card__heading small,
.result-card-head small {
  color: var(--assistant-muted);
  font-size: 0.68rem;
}

.confirm-card__icon,
.chart-action-card__icon {
  display: grid;
  width: 1.7rem;
  height: 1.7rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  background: #e5eee9;
  color: #3f776f;
  font-size: 0.74rem;
  font-weight: 900;
}

.confirm-card--timeout .confirm-card__icon {
  background: rgba(230, 171, 32, 0.16);
  color: #9a6c00;
}

.confirm-card--danger {
  margin: -0.8rem -0.95rem;
  border: 1px solid rgba(217, 86, 86, 0.45);
  border-radius: 1.1rem;
  background: #fff2ef;
  padding: 1rem;
}

.confirm-card--danger .confirm-card__icon {
  background: var(--assistant-danger);
  color: #fff;
}

.confirm-card--danger .confirm-card__heading strong {
  color: #a82f2f;
}

.confirm-card__reason,
.confirm-card > p {
  margin: 0;
}

.confirm-card__reason {
  color: #8f3c35;
  font-size: 0.76rem;
}

.confirm-card__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.55rem;
}

.assistant-command-block,
:deep(.markdown-body pre) {
  max-width: 100%;
  margin: 0;
  overflow-x: auto;
  border: 1px solid #44352a;
  border-radius: 0.82rem;
  background: var(--assistant-code);
  padding: 0.85rem 0.9rem;
  color: #f7eedb;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 0.76rem;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-command-block--danger {
  border-color: rgba(217, 86, 86, 0.8);
  box-shadow: inset 0.22rem 0 0 var(--assistant-danger);
}

.assistant-command-block code,
:deep(.markdown-body pre code) {
  border: 0;
  background: transparent;
  padding: 0;
  color: inherit;
}

.chart-action-card__icon {
  background: #d8f3ea;
  color: #087a6f;
}

.chart-action-card__details {
  display: grid;
  gap: 0.42rem;
  margin: 0;
  border-radius: 0.9rem;
  background: #f7f2e4;
  padding: 0.75rem;
}

.chart-action-card__details > div {
  display: grid;
  grid-template-columns: 3.2rem minmax(0, 1fr);
  gap: 0.5rem;
}

.chart-action-card__details dt {
  color: var(--assistant-muted);
  font-size: 0.68rem;
  font-weight: 800;
}

.chart-action-card__details dd {
  margin: 0;
  color: var(--assistant-copy);
  font-size: 0.75rem;
}

.chart-render-card {
  overflow: hidden;
  border-radius: 0.9rem;
}

.result-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  border-bottom: 1px solid #e7ddc7;
  padding-bottom: 0.72rem;
}

.result-tone {
  flex: 0 0 auto;
  border: 1px solid transparent;
  border-radius: 999px;
  padding: 0.3rem 0.58rem;
  font-size: 0.62rem;
  font-weight: 800;
  line-height: 1;
}

.result-tone-success {
  border-color: rgba(105, 173, 56, 0.28);
  background: rgba(105, 173, 56, 0.1);
  color: #4f8d2e;
}

.result-tone-danger {
  border-color: rgba(217, 86, 86, 0.28);
  background: rgba(217, 86, 86, 0.09);
  color: #b63d3d;
}

.result-tone-info {
  border-color: rgba(111, 145, 200, 0.28);
  background: rgba(111, 145, 200, 0.1);
  color: #5373aa;
}

.execution-feed-message {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
}

.execution-feed-message__dot {
  width: 0.48rem;
  height: 0.48rem;
  flex: 0 0 auto;
  margin-top: 0.48rem;
  border-radius: 50%;
  background: var(--assistant-info);
}

.assistant-composer {
  display: grid;
  flex: 0 0 auto;
  gap: 0.65rem;
  border-top: 1px solid #e8dfca;
  background: var(--assistant-panel);
  padding: 0.95rem 1.1rem 1.05rem;
}

.assistant-composer.is-running {
  background: #fff8ef;
}

.assistant-running-banner {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  border: 1px solid rgba(217, 86, 86, 0.28);
  border-radius: 0.9rem;
  background: rgba(217, 86, 86, 0.06);
  padding: 0.62rem 0.75rem;
}

.assistant-running-banner__pulse {
  width: 0.65rem;
  height: 0.65rem;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--assistant-danger);
  animation: assistant-pulse 1.2s ease-in-out infinite;
}

.assistant-running-banner > div {
  display: grid;
  gap: 0.05rem;
}

.assistant-running-banner strong {
  color: #a83d37;
  font-size: 0.78rem;
}

.assistant-running-banner small {
  color: #9a7068;
  font-size: 0.66rem;
}

.assistant-composer__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.75rem;
}

.assistant-send-button {
  min-width: 7.5rem;
  height: auto;
  border-radius: 0.9rem;
  padding-inline: 1.25rem;
}

.assistant-stop-button {
  min-width: 8.5rem;
  font-weight: 900;
}

.assistant-composer__hint {
  margin: 0;
  color: var(--assistant-muted);
  font-size: 0.66rem;
}

.execution-panel {
  min-height: 0;
}

.execution-panel__body {
  display: grid;
  max-height: clamp(32rem, calc(100vh - 17rem), 50rem);
  align-content: start;
  gap: 1.15rem;
  overflow-y: auto;
}

.execution-plan,
.execution-events {
  display: grid;
  gap: 0.72rem;
}

.execution-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--assistant-ink);
  font-size: 0.76rem;
  font-weight: 900;
}

.execution-section-title small {
  color: var(--assistant-muted);
  font-size: 0.62rem;
  font-weight: 700;
}

.execution-plan__list,
.execution-events__list {
  display: grid;
  gap: 0.62rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.execution-plan__list li {
  display: grid;
  grid-template-columns: 1.6rem minmax(0, 1fr);
  align-items: start;
  gap: 0.6rem;
  border: 1px solid #e5dbc5;
  border-radius: 0.9rem;
  background: #faf6eb;
  padding: 0.68rem;
}

.execution-plan__index {
  display: grid;
  width: 1.55rem;
  height: 1.55rem;
  place-items: center;
  border-radius: 50%;
  background: #e9e0cc;
  color: var(--assistant-copy);
  font-size: 0.65rem;
  font-weight: 900;
}

.execution-plan__list li strong {
  display: block;
  color: var(--assistant-copy);
  font-size: 0.72rem;
  line-height: 1.45;
}

.execution-plan__list li.is-running {
  border-color: rgba(25, 191, 174, 0.4);
  background: rgba(25, 191, 174, 0.07);
}

.execution-plan__list li.is-running .execution-plan__index {
  background: var(--assistant-brand);
  color: #fff;
}

.execution-plan__list li.is-done .execution-plan__index {
  background: var(--assistant-success);
  color: #fff;
}

.execution-plan__list li.is-failed {
  border-color: rgba(217, 86, 86, 0.35);
  background: rgba(217, 86, 86, 0.06);
}

.execution-plan__list li.is-failed .execution-plan__index {
  background: var(--assistant-danger);
  color: #fff;
}

.execution-plan__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.38rem;
}

.execution-plan__tags span {
  border-radius: 999px;
  background: #ece4d2;
  padding: 0.18rem 0.42rem;
  color: var(--assistant-muted);
  font-size: 0.56rem;
  font-weight: 800;
}

.execution-plan__tags .is-risk {
  background: rgba(217, 86, 86, 0.12);
  color: #b63d3d;
}

.execution-plan__tags .is-rollback {
  background: rgba(111, 145, 200, 0.13);
  color: #5373aa;
}

.execution-event {
  display: grid;
  grid-template-columns: 0.55rem minmax(0, 1fr);
  gap: 0.5rem;
}

.execution-event__dot {
  width: 0.5rem;
  height: 0.5rem;
  margin-top: 0.28rem;
  border-radius: 50%;
  background: var(--assistant-info);
}

.execution-event.is-success .execution-event__dot {
  background: var(--assistant-success);
}

.execution-event.is-warning .execution-event__dot {
  background: var(--assistant-warning);
}

.execution-event.is-danger .execution-event__dot {
  background: var(--assistant-danger);
}

.execution-event__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.5rem;
}

.execution-event__heading strong {
  color: var(--assistant-copy);
  font-size: 0.69rem;
}

.execution-event__heading time {
  flex: 0 0 auto;
  color: var(--assistant-muted);
  font-size: 0.57rem;
}

.execution-event p {
  margin: 0.18rem 0 0;
  color: var(--assistant-muted);
  font-size: 0.64rem;
  line-height: 1.45;
}

.execution-events__empty {
  display: grid;
  justify-items: center;
  gap: 0.35rem;
  border: 1px dashed #d9ceb7;
  border-radius: 0.95rem;
  padding: 1.2rem 0.8rem;
  text-align: center;
}

.execution-events__empty span {
  color: var(--assistant-brand);
  font-size: 1.2rem;
}

.execution-events__empty p {
  max-width: 12rem;
  margin: 0;
  color: var(--assistant-muted);
  font-size: 0.68rem;
  line-height: 1.5;
}

.session-boundary-note {
  display: grid;
  gap: 0.3rem;
}

.session-boundary-note strong {
  color: #806729;
  font-size: 0.7rem;
}

.interactive-status {
  border: 1px solid #dfd6c2;
  border-radius: 0.78rem;
  background: #f7f2e6;
  padding: 0.55rem 0.68rem;
  color: var(--assistant-copy);
  font-size: 0.68rem;
  font-weight: 800;
}

.interactive-status.is-success {
  border-color: rgba(105, 173, 56, 0.28);
  background: rgba(105, 173, 56, 0.08);
  color: #4f8d2e;
}

.interactive-status.is-danger {
  border-color: rgba(217, 86, 86, 0.28);
  background: rgba(217, 86, 86, 0.08);
  color: #b63d3d;
}

:deep(.markdown-body) {
  min-width: 0;
}

:deep(.markdown-body > :first-child) {
  margin-top: 0;
}

:deep(.markdown-body > :last-child) {
  margin-bottom: 0;
}

:deep(.markdown-body p) {
  margin: 0 0 0.72em;
}

:deep(.markdown-body ul),
:deep(.markdown-body ol) {
  margin: 0 0 0.72em;
  padding-left: 1.35rem;
}

:deep(.markdown-body ul) {
  list-style: disc;
}

:deep(.markdown-body ol) {
  list-style: decimal;
}

:deep(.markdown-body li + li) {
  margin-top: 0.28em;
}

:deep(.markdown-body code) {
  border-radius: 0.35rem;
  background: var(--assistant-code);
  padding: 0.16em 0.36em;
  color: #f7eedb;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 0.92em;
}

:deep(.markdown-body pre) {
  margin-bottom: 0.75em;
}

:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  margin: 0.9em 0 0.42em;
  color: var(--assistant-ink);
  font-weight: 900;
}

:deep(.markdown-body blockquote) {
  margin: 0 0 0.78em;
  border-left: 0.2rem solid var(--assistant-brand);
  border-radius: 0.65rem;
  background: #f4f0e3;
  padding: 0.65rem 0.75rem;
  color: var(--assistant-muted);
}

:deep(.markdown-body a) {
  color: #087a6f;
  font-weight: 800;
}

:deep(.markdown-result strong) {
  color: var(--assistant-ink);
}

.ops-workbench :deep(.el-input__wrapper),
.ops-workbench :deep(.el-select__wrapper),
.ops-workbench :deep(.el-textarea__inner),
.ops-workbench :deep(.el-input-number) {
  border-radius: 0.82rem;
}

.ops-workbench :deep(.el-input__wrapper),
.ops-workbench :deep(.el-select__wrapper),
.ops-workbench :deep(.el-textarea__inner) {
  background: #fffdf7;
  box-shadow: 0 0 0 1px #d9ceb7 inset;
}

.ops-workbench :deep(.el-input__wrapper.is-focus),
.ops-workbench :deep(.el-select__wrapper.is-focused),
.ops-workbench :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 2px rgba(25, 191, 174, 0.36) inset;
}

.ops-workbench :deep(.el-button) {
  min-height: 2.5rem;
  border-radius: 0.8rem;
  font-weight: 800;
  transition: transform 180ms cubic-bezier(0.4, 0, 0.2, 1), box-shadow 180ms cubic-bezier(0.4, 0, 0.2, 1);
}

.ops-workbench :deep(.el-button--primary) {
  border-color: var(--assistant-brand);
  background: var(--assistant-brand);
  box-shadow: 0 0.18rem 0 var(--assistant-brand-active);
}

.ops-workbench :deep(.el-button--primary:hover) {
  border-color: var(--assistant-brand-hover);
  background: var(--assistant-brand-hover);
}

.ops-workbench :deep(.el-button--primary:active),
.ops-workbench :deep(.el-button--danger:active) {
  transform: translateY(0.12rem);
  box-shadow: none;
}

.ops-workbench :deep(.el-button--danger) {
  border-color: var(--assistant-danger);
  background: var(--assistant-danger);
  box-shadow: 0 0.18rem 0 #b63d3d;
}

.custom-scrollbar {
  scrollbar-color: #c2b69f transparent;
  scrollbar-width: thin;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 0.42rem;
  height: 0.42rem;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c2b69f;
}

@keyframes assistant-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.55;
    transform: scale(0.82);
  }
}

@media (min-width: 64rem) {
  .assistant-workbench-grid {
    grid-template-columns: minmax(16rem, 18rem) minmax(0, 1fr);
    align-items: start;
  }

  .execution-panel {
    grid-column: 1 / -1;
  }
}

@media (min-width: 90rem) {
  .assistant-workbench-grid {
    grid-template-columns: minmax(16rem, 18rem) minmax(32rem, 1fr) minmax(16rem, 18rem);
  }

  .execution-panel {
    grid-column: auto;
  }
}

@media (max-width: 75rem) {
  .assistant-context-bar {
    grid-template-columns: minmax(14rem, 1fr) auto;
  }

  .assistant-context-metrics {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .assistant-context-actions {
    grid-column: 2;
    grid-row: 1;
  }
}

@media (max-width: 47.99rem) {
  .assistant-context-bar {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.85rem;
    border-radius: 1.2rem;
  }

  .assistant-context-bar__heading {
    align-items: flex-start;
  }

  .assistant-context-metrics {
    grid-column: auto;
    grid-row: auto;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .context-metric + .context-metric {
    border-left: 0;
  }

  .context-metric:nth-child(even) {
    border-left: 1px solid #e8dfca;
  }

  .context-metric:nth-child(n + 3) {
    border-top: 1px solid #e8dfca;
  }

  .assistant-context-actions {
    grid-column: auto;
    grid-row: auto;
    justify-content: stretch;
  }

  .assistant-context-actions :deep(.el-button) {
    width: 100%;
  }

  .assistant-panel {
    border-radius: 1.2rem;
  }

  .connection-panel {
    order: 1;
  }

  .conversation-panel {
    order: 2;
  }

  .execution-panel {
    order: 3;
  }

  .assistant-chat {
    min-height: 26rem;
    height: 58vh;
    padding: 0.85rem;
  }

  .assistant-message__bubble {
    max-width: 95%;
  }

  .assistant-composer__row {
    grid-template-columns: minmax(0, 1fr);
  }

  .assistant-send-button,
  .assistant-stop-button {
    width: 100%;
    min-height: 2.8rem;
  }

  .confirm-card__actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
  }

  .confirm-card__actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .execution-panel__body {
    max-height: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .ops-workbench *,
  .ops-workbench *::before,
  .ops-workbench *::after {
    scroll-behavior: auto;
    animation-duration: 0.01ms;
    animation-iteration-count: 1;
    transition-duration: 0.01ms;
  }
}
</style>
