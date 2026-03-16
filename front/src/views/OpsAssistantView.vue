<template>
  <div class="workspace-cool-glass mx-auto max-w-7xl space-y-5">
    <el-card class="glass-card rounded-[30px]" :body-style="{ padding: '20px' }">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="text-lg font-bold text-ui-text">运维助手</h2>
          <p class="text-xs text-ui-subtext mt-1">对话、规划、执行</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="glass-chip px-2.5 py-1 text-xs font-medium" :class="connected ? 'border-emerald-200/30 bg-emerald-400/10 text-ui-success' : 'border-slate-200/20 bg-slate-200/8 text-ui-subtext'">
            {{ connected ? '在线' : '离线' }}
          </span>
          <el-button size="small" @click="connectWs" :disabled="connected">连接</el-button>
          <el-button size="small" @click="disconnectWs" :disabled="!connected">断开</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="glass-card rounded-[30px]" :body-style="{ padding: '20px' }">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
        <el-select
          v-model="selectedSavedConnection"
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
      </div>
      <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
        <el-input v-model="serverIp" placeholder="服务器 IP" />
        <el-input v-model="username" placeholder="账号" />
        <el-input v-model="password" type="password" show-password placeholder="密码" />
        <el-checkbox v-model="execute">允许执行</el-checkbox>
        <el-input-number v-model="maxRounds" :min="1" :max="50" controls-position="right" placeholder="最大轮数" />
      </div>
      <p class="text-xs text-ui-subtext mt-3">默认只规划；勾选后通过 SSH 执行。</p>
    </el-card>

    <el-card class="glass-card rounded-[30px]" :body-style="{ padding: '20px' }">
      <div ref="chatBox" class="glass-subcard h-[460px] overflow-y-auto p-4 space-y-3">
        <div v-for="(msg, idx) in messages" :key="idx" class="flex" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
          <div
            class="max-w-[85%] rounded-[18px] px-3 py-2 text-sm whitespace-pre-wrap"
            :class="msg.role === 'user'
              ? 'bg-[linear-gradient(135deg,rgba(37,99,235,0.94),rgba(96,165,250,0.9))] text-white shadow-[0_18px_40px_-28px_rgba(37,99,235,0.7)]'
              : 'glass-soft border border-white/18 text-ui-text shadow-[0_18px_32px_-28px_rgba(15,23,42,0.22)]'"
          >
            <div class="font-semibold text-xs mb-1 opacity-80">{{ msg.role === 'user' ? '你' : '运维助手' }}</div>
            <template v-if="msg.type === 'confirm'">
              <div class="space-y-2">
                <div>命令：{{ msg.command }}</div>
                <div>风险：{{ msg.riskLevel }}</div>
                <div class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="confirmExecute(msg)">确定执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelExecute(msg)">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'timeout'">
              <div class="space-y-2">
                <div class="text-ui-warning font-semibold">任务达到最大轮数，已暂停</div>
                <div>是否继续执行？</div>
                <div class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="confirmContinue(msg)">继续执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelContinue(msg)">停止</el-button>
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'risk_confirm'">
              <div class="space-y-2">
                <div class="text-ui-warning font-semibold">检测到高风险命令，请确认是否执行</div>
                <div>命令：{{ msg.command }}</div>
                <div>风险等级：{{ msg.riskLevel }}</div>
                <div class="flex gap-2 pt-1">
                  <el-button size="small" type="danger" :disabled="msg.handled" @click="confirmRiskExecute(msg)">仍要执行</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelRiskExecute(msg)">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'chart_action'">
              <div class="space-y-2">
                <div class="text-ui-success font-semibold">AI 判断当前结果适合图表展示</div>
                <div>图表标题：{{ msg.chartTitle }}</div>
                <div>原因：{{ msg.reason }}</div>
                <div>建议范围：{{ msg.timeRange }}</div>
                <div>图表类型：{{ chartTemplateLabel(msg.chartTemplate) }}</div>
                <div class="flex gap-2 pt-1">
                  <el-button size="small" type="primary" :disabled="msg.handled" @click="generateChart(msg)">生成图表</el-button>
                  <el-button size="small" :disabled="msg.handled" @click="cancelChart(msg)">取消</el-button>
                </div>
              </div>
            </template>
            <template v-else-if="msg.type === 'chart_render'">
              <InlineMetricsTemplate :title="msg.title" :chart-data="msg.chartData" />
            </template>
            <template v-else>
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)" class="markdown-body"></div>
              <div v-else>{{ msg.content }}</div>
            </template>
          </div>
        </div>
      </div>

      <div class="mt-4 flex gap-3">
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
          class="h-auto px-6"
          :disabled="isAgentRunning ? !connected : !input.trim()"
          @click="isAgentRunning ? forceStop() : sendMessage()"
        >
          {{ isAgentRunning ? '强制停止' : '发送' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
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
const execute = ref(false)
const maxRounds = ref(15)
const isAgentRunning = ref(false)
const selectedSavedConnection = ref('')
const savedConnections = ref([])
const savedConnectionsLoaded = ref(false)
const pendingTaskHandled = ref(false)

const input = ref('')
const messages = ref([])
const chatBox = ref(null)

const appendMessage = async (role, content) => {
  const normalizedContent = role === 'assistant' ? toUserFriendlyText(content) : content
  if (role === 'assistant') {
    await humanLikeDelay()
  }
  messages.value.push({ role, content: normalizedContent, type: 'text' })
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
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
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const appendChartRenderMessage = async chartData => {
  messages.value.push({
    role: 'assistant',
    type: 'chart_render',
    title: chartData?.title || `服务器监控图（${chartData?.timeRange || '1h'}）`,
    chartData,
  })
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
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
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const appendTimeoutMessage = async () => {
  messages.value.push({
    role: 'assistant',
    type: 'timeout',
    handled: false,
  })
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
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
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const getWsUrl = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/server/connect`
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

const tryApplyPendingConnection = pendingTask => {
  const targetIp = String(pendingTask?.serverIp || '').trim()
  if (!targetIp) {
    return Boolean(serverIp.value && username.value && password.value)
  }

  serverIp.value = targetIp

  const matched = savedConnections.value.find(item => item.serverIp === targetIp)
  if (matched) {
    selectedSavedConnection.value = matched.id
    handleSavedConnectionChange(matched.id)
    return true
  }

  return serverIp.value === targetIp && Boolean(username.value && password.value)
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
  execute.value = Boolean(pendingTask.autoExecute)

  if (!input.value) {
    await clearPendingTask()
    return
  }

  if (!canExecute && execute.value) {
    await appendMessage('assistant', '已带入选中的处理方式，但未找到匹配的服务器连接，请先补全或选择连接后再发送。')
    await clearPendingTask()
    return
  }

  await sendMessage(input.value)
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
      if (data.type === 'ops_progress') {
        if (['agent_finish', 'agent_timeout', 'agent_stopped', 'finished'].includes(String(data.stage || ''))) {
          isAgentRunning.value = false
        }
        const progressText = formatProgressForUser(data)
        if (progressText) {
          await appendMessage('assistant', progressText)
        }
        return
      }
      if (data.type === 'ops_chat_result') {
        isAgentRunning.value = false
        const summary = formatOpsResult(data)
        await appendMessage('assistant', summary)

        if (data.needRiskConfirm && data.riskCommand) {
          await appendRiskConfirmMessage(data.riskCommand, data.riskLevel, data.reply)
        } else if (data.timeout) {
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
      if (data.type === 'ops_force_stop_result') {
        isAgentRunning.value = false
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
    await appendMessage('assistant', '连接已断开。')
  }

  socket.onerror = async () => {
    isAgentRunning.value = false
    await appendMessage('assistant', '连接异常，请检查后端服务。')
  }
}

const disconnectWs = () => {
  if (ws.value) {
    ws.value.close()
  }
}

const sendMessage = async presetText => {
  if (isAgentRunning.value) return
  const text = typeof presetText === 'string' ? presetText.trim() : input.value.trim()
  if (!text) return

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，正在自动连接...')
    connectWs()
    return
  }

  await appendMessage('user', text)
  await appendMessage('assistant', execute.value ? '收到，正在执行中...' : '收到，正在处理中...')

  const payload = {
    type: 'ops_chat',
    query: text,
    execute: execute.value,
    serverIp: serverIp.value,
    username: username.value,
    password: password.value,
    maxRounds: Math.min(50, Math.max(1, Number(maxRounds.value) || 15)),
  }

  ws.value.send(JSON.stringify(payload))
  if (execute.value) {
    isAgentRunning.value = true
  }
  input.value = ''
}

const executeByConfirmation = async query => {
  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法执行命令。')
    return
  }
  if (!serverIp.value || !username.value || !password.value) {
    await appendMessage('assistant', '请先填写服务器连接信息后再执行。')
    return
  }
  if (!query) {
    await appendMessage('assistant', '缺少原始指令，无法执行。')
    return
  }

  await appendMessage('assistant', '收到，正在执行中...')
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
}

const confirmExecute = async msg => {
  if (msg.handled) return
  msg.handled = true
  await executeByConfirmation(msg.query)
}

const cancelExecute = async msg => {
  if (msg.handled) return
  msg.handled = true
  await appendMessage('assistant', '已取消执行。')
}

const confirmRiskExecute = async msg => {
  if (msg.handled) return
  msg.handled = true

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
  msg.handled = true
  await appendMessage('assistant', '已取消高风险命令执行。')
}

const confirmContinue = async msg => {
  if (msg.handled) return
  msg.handled = true

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法继续执行。')
    return
  }

  await appendMessage('user', '继续')
  await appendMessage('assistant', '收到，正在继续执行中...')

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
  msg.handled = true
  await appendMessage('assistant', '已停止任务。')
}

const generateChart = async msg => {
  if (msg.handled) return
  msg.handled = true

  if (!connected.value || !ws.value) {
    await appendMessage('assistant', '当前未连接，无法生成图表。')
    return
  }
  if (!serverIp.value) {
    await appendMessage('assistant', '请先填写服务器 IP。')
    return
  }

  await appendMessage('assistant', '正在生成图表数据...')
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
  msg.handled = true
  await appendMessage('assistant', '已取消图表生成。')
}

const forceStop = async () => {
  if (!connected.value || !ws.value) {
    isAgentRunning.value = false
    await appendMessage('assistant', '连接未建立，无法强制停止。')
    return
  }
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

const formatOpsResult = data => {
  const lines = []
  const replySummary = toUserFriendlyText(data.reply)

  if (replySummary) {
    lines.push(replySummary)
  } else {
    lines.push(data.executed ? '处理完成。' : '我已整理好处理建议。')
  }

  if (data.executed) {
    lines.push(`执行结果：\n\`\`\`\n${data.execResult || '无返回'}\n\`\`\``)
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
    .trim()
}

const formatProgressForUser = data => {
  const stage = String(data?.stage || '')
  const message = toUserFriendlyText(data?.message || '')

  if (stage === 'cmd_exec_start') {
    return message || '正在执行命令...'
  }
  if (stage === 'cmd_exec_done') {
    return ''
  }
  if (stage === 'cmd_exec_fail') {
    return `命令 “${data.command || '未知'}” 执行失败。`
  }
  if (stage === 'risk_exec_start') {
    return '正在执行你确认的高风险命令...'
  }
  if (stage === 'risk_exec_done') {
    return '高风险命令执行完成。'
  }
  if (stage === 'agent_stopped') {
    return '任务已强制停止。'
  }

  return ''
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
:deep(.markdown-body ul) {
  list-style-type: disc;
  padding-left: 1.5em;
  margin-bottom: 0.75em;
}
:deep(.markdown-body ol) {
  list-style-type: decimal;
  padding-left: 1.5em;
  margin-bottom: 0.75em;
}
:deep(.markdown-body pre) {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.2), rgba(255, 255, 255, 0.08));
  padding: 1em;
  border-radius: 0.75rem;
  overflow-x: auto;
  margin-bottom: 0.75em;
  border: 1px solid rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(18px);
}
:deep(.markdown-body code) {
  background: rgba(255, 255, 255, 0.18);
  padding: 0.2em 0.4em;
  border-radius: 0.35rem;
  font-family: monospace;
}
:deep(.markdown-body p) {
  margin-bottom: 0.75em;
}
:deep(.markdown-body h1),
:deep(.markdown-body h2),
:deep(.markdown-body h3) {
  font-weight: bold;
  margin-bottom: 0.5em;
  margin-top: 1em;
}
</style>
