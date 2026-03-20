<template>
  <div class="workspace-cool-glass mx-auto max-w-6xl space-y-6">
    <div class="flex items-center justify-between gap-3">
      <div>
        <h2 class="text-xl font-bold text-ui-text">告警详情</h2>
        <p class="text-sm text-ui-subtext mt-1">查看诊断信息，并将处理方案交给助手执行。</p>
      </div>
      <div class="flex items-center gap-2">
        <el-button type="danger" plain :loading="deleting" @click="handleDeleteCurrent">
          删除
        </el-button>
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" plain class="alert-assistant-button" @click="goAssistant">助手</el-button>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated />

    <template v-else-if="info">
      <el-card class="alert-detail-card glass-card rounded-[34px]" :body-style="{ padding: '20px' }">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div class="text-lg font-semibold text-ui-text">{{ info.errorSummary || '未命名告警' }}</div>
          <el-tag :type="getTagType(info.riskLevel)" effect="light">{{ formattedRiskLevel }}</el-tag>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="记录 ID">{{ info.id || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发生时间">{{ formatDate(info.createdAt) || '-' }}</el-descriptions-item>
          <el-descriptions-item label="服务器 IP">{{ info.serverIp || '-' }}</el-descriptions-item>
          <el-descriptions-item label="组件">{{ info.component || '-' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ formattedRiskLevel }}</el-descriptions-item>
          <el-descriptions-item label="问题摘要">{{ info.errorSummary || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="alert-detail-card glass-card rounded-[34px]" :body-style="{ padding: '20px' }">
        <template #header>
          <div class="font-semibold text-ui-text">问题详情</div>
        </template>
        <pre class="glass-code-block detail-pre">{{ info.analysisResult || '-' }}</pre>
      </el-card>

      <el-card class="alert-detail-card glass-card rounded-[34px]" :body-style="{ padding: '20px' }">
        <template #header>
          <div class="font-semibold text-ui-text">处理建议</div>
        </template>

        <div v-if="actionList.length > 0" class="space-y-3">
          <div
            v-for="(action, index) in actionList"
            :key="`${index}-${action}`"
            class="alert-action-card glass-subcard flex flex-col gap-3 rounded-[24px] p-4 md:flex-row md:items-start md:justify-between"
          >
            <div class="min-w-0 flex-1 whitespace-pre-wrap break-all text-sm leading-6 text-ui-text md:pr-4">
              <span class="mr-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-brand/10 text-xs font-semibold text-brand">
                {{ index + 1 }}
              </span>
              {{ action }}
            </div>
            <el-button
              type="primary"
              class="shrink-0 self-start"
              :loading="submittingActionIndex === index"
              @click="handleSelectAction(action, index)"
            >
              交给助手执行
            </el-button>
          </div>
        </div>
        <el-empty v-else description="无处理建议" />
      </el-card>

      <el-card class="alert-detail-card glass-card rounded-[34px]" :body-style="{ padding: '20px' }">
        <template #header>
          <div class="font-semibold text-ui-text">原始日志</div>
        </template>
        <pre class="glass-code-block detail-pre raw-log">{{ info.rawLog || '-' }}</pre>
      </el-card>
    </template>

    <el-empty v-else description="未找到对应告警详情" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteInfo, insertProcess, selectInfoById } from '../api/info'

const PENDING_TASK_KEY = 'opsAssistantPendingTask'
const MAX_ACTIONS = 4

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const info = ref(null)
const submittingActionIndex = ref(-1)
const deleting = ref(false)

const actionList = computed(() => extractSuggestedActions(info.value?.suggestedActions))
const formattedRiskLevel = computed(() => formatRiskLevel(info.value?.riskLevel))

function formatDate(value) {
  if (value == null) return ''
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).toLocaleString()
  }
  return new Date(value).toLocaleString()
}

function formatRiskLevel(level) {
  const value = String(level || '').trim()
  if (['高', '中', '低', '无'].includes(value)) return value
  const lowered = value.toLowerCase()
  if (lowered.includes('high') || lowered.includes('critical') || lowered.includes('error')) return '高'
  if (lowered.includes('medium') || lowered.includes('warning') || lowered.includes('warn')) return '中'
  if (lowered.includes('low') || lowered.includes('info')) return '低'
  if (lowered.includes('normal') || lowered.includes('none') || lowered.includes('ok') || lowered.includes('safe')) return '无'
  return '无'
}

function getTagType(level) {
  const riskLevel = formatRiskLevel(level)
  if (riskLevel === '高') return 'danger'
  if (riskLevel === '中') return 'warning'
  if (riskLevel === '低') return 'info'
  return ''
}

function cleanAction(value) {
  return String(value || '')
    .replace(/^[\s]*([\-•*]|\d+[\.、）\)]|[一二三四五六七八九十]+、)\s*/, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function uniqueActions(actions) {
  return Array.from(new Set(actions.filter(Boolean))).slice(0, MAX_ACTIONS)
}

function extractSuggestedActions(value) {
  const text = String(value || '').trim()
  if (!text || ['-', '无', '暂无', '[]', 'null', 'undefined'].includes(text.toLowerCase())) {
    return []
  }

  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return uniqueActions(parsed.map(item => cleanAction(item)).filter(Boolean))
    }
  } catch {
  }

  const normalized = text
    .replace(/\r/g, '')
    .replace(/([；;])(?=\S)/g, '$1\n')
    .replace(/\s+(?=\d+[\.、）\)])/g, '\n')
    .replace(/\s+(?=[一二三四五六七八九十]+、)/g, '\n')

  const rawLines = normalized
    .split('\n')
    .map(item => item.trim())
    .filter(Boolean)

  const lines = []
  for (const rawLine of rawLines) {
    const cleaned = cleanAction(rawLine)
    if (!cleaned) continue

    if (lines.length > 0 && !/^[\-•*]|^\d+[\.、）\)]|^[一二三四五六七八九十]+、/.test(rawLine)) {
      lines[lines.length - 1] = `${lines[lines.length - 1]} ${cleaned}`.trim()
      continue
    }

    lines.push(cleaned)
  }

  if (lines.length <= 1) {
    return uniqueActions(
      text
        .split(/[；;]+/)
        .map(item => cleanAction(item))
        .filter(Boolean)
    )
  }

  return uniqueActions(lines)
}

function formatProcessTime() {
  const date = new Date()
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function buildAssistantPrompt(record, action) {
  return [
    `请在服务器 ${record?.serverIp || '目标服务器'} 上处理当前告警，并优先执行我选择的方案。`,
    record?.component ? `组件：${record.component}` : '',
    record?.errorSummary ? `问题摘要：${record.errorSummary}` : '',
    record?.analysisResult ? `问题详情：${record.analysisResult}` : '',
    `我选择的处理方式：${action}`,
    '请直接开始排查并执行必要命令，完成后返回执行结果、风险与回滚说明。',
  ].filter(Boolean).join('\n')
}

async function fetchDetail() {
  loading.value = true
  try {
    const result = await selectInfoById(route.params.id)
    if (Array.isArray(result) && result.length > 0) {
      info.value = result[0]
      return
    }
    if (result && Array.isArray(result.data) && result.data.length > 0) {
      info.value = result.data[0]
      return
    }
    if (result && typeof result === 'object' && result.id) {
      info.value = result
      return
    }
    info.value = null
  } catch (error) {
    console.error('Failed to fetch info detail', error)
    ElMessage.error(error?.message || '获取告警详情失败')
    info.value = null
  } finally {
    loading.value = false
  }
}

async function handleSelectAction(action, index) {
  if (!info.value) return
  submittingActionIndex.value = index
  try {
    await insertProcess({
      serverIp: info.value.serverIp || '',
      component: info.value.component || '',
      problemLog: info.value.rawLog || '',
      processMethod: action,
      processTime: formatProcessTime(),
    })

    sessionStorage.setItem(PENDING_TASK_KEY, JSON.stringify({
      query: buildAssistantPrompt(info.value, action),
      serverIp: info.value.serverIp || '',
      component: info.value.component || '',
      selectedAction: action,
      autoExecute: true,
      sourceInfoId: info.value.id || '',
    }))

    ElMessage.success('已记录处理方式，正在跳转灵枢助手执行')
    router.push({ name: 'ops-assistant', query: { autostart: '1' } })
  } catch (error) {
    console.error('Failed to insert process', error)
    ElMessage.error(error?.message || '记录处理方式失败')
  } finally {
    submittingActionIndex.value = -1
  }
}

async function handleDeleteCurrent() {
  if (!info.value?.id) {
    ElMessage.warning('当前告警缺少记录 ID，无法删除')
    return
  }

  try {
    await ElMessageBox.confirm(
      '删除后无法恢复，是否确认删除这条告警？',
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        modalClass: 'keep-bright-overlay',
      },
    )
  } catch {
    return
  }

  deleting.value = true
  try {
    const deleted = await deleteInfo(info.value.id)
    if (!deleted) {
      ElMessage.warning('未找到可删除的告警记录')
      return
    }

    ElMessage.success('告警已删除')
    router.push({ name: 'info-list' })
  } catch (error) {
    console.error('Failed to delete info', error)
    ElMessage.error(error?.message || '删除告警失败')
  } finally {
    deleting.value = false
  }
}

function goBack() {
  router.push({ name: 'info-list' })
}

function goAssistant() {
  router.push({ name: 'ops-assistant' })
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.alert-detail-card {
  border-radius: 34px !important;
}

.alert-detail-card :deep(.el-card__body),
.alert-detail-card :deep(.el-card__header) {
  border-radius: inherit;
}

.alert-action-card {
  background: linear-gradient(180deg, rgba(250, 253, 255, 0.36), rgba(238, 245, 253, 0.24));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    0 14px 24px -22px rgba(88, 110, 148, 0.14);
}

.alert-assistant-button {
  border-color: rgba(96, 165, 250, 0.28) !important;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.96), rgba(96, 165, 250, 0.94)) !important;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.18),
    0 18px 32px -24px rgba(37, 99, 235, 0.52) !important;
  color: #ffffff !important;
}

.alert-assistant-button :deep(span) {
  color: #ffffff !important;
}

.alert-assistant-button:hover,
.alert-assistant-button:focus-visible {
  color: #ffffff !important;
  border-color: rgba(96, 165, 250, 0.34) !important;
  background: linear-gradient(135deg, rgba(29, 78, 216, 0.98), rgba(96, 165, 250, 0.96)) !important;
}

.detail-pre {
  max-width: 100%;
  border-radius: 24px;
}

.raw-log {
  max-height: 360px;
  overflow: auto;
}
</style>
