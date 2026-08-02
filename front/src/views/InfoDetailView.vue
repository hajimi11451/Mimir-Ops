<template>
  <div class="app-page alert-detail-page">
    <section class="page-hero detail-hero">
      <div>
        <div class="section-eyebrow">Alert investigation</div>
        <h2>告警详情</h2>
        <p>核对诊断结论和原始日志，选择方案后可直接交给灵枢助手继续排查。</p>
      </div>
      <div class="hero-actions">
        <el-button @click="goBack">返回告警中心</el-button>
        <el-button @click="goAssistant">打开助手</el-button>
      </div>
    </section>

    <section v-if="loading" class="section-card"><el-skeleton :rows="8" animated /></section>

    <template v-else-if="info">
      <section class="section-card incident-summary" :class="`incident-summary--${formattedRiskLevel}`">
        <div class="incident-heading">
          <div class="risk-mark" :class="`risk-mark--${formattedRiskLevel}`">{{ formattedRiskLevel }}</div>
          <div>
            <div class="section-eyebrow">Incident #{{ info.id || '-' }}</div>
            <h3>{{ info.errorSummary || '未命名告警' }}</h3>
            <p>{{ formatDate(info.createdAt) || '时间未知' }} · {{ info.component || '未知组件' }}</p>
          </div>
        </div>
        <div class="incident-meta-grid">
          <div class="soft-panel incident-meta"><span>服务器</span><strong class="mono-text">{{ info.serverIp || '-' }}</strong></div>
          <div class="soft-panel incident-meta"><span>组件</span><strong>{{ info.component || '-' }}</strong></div>
          <div class="soft-panel incident-meta"><span>风险等级</span><el-tag :type="getTagType(info.riskLevel)" effect="light">{{ formattedRiskLevel }}风险</el-tag></div>
        </div>
      </section>

      <section class="detail-grid">
        <article class="section-card analysis-card">
          <div class="section-header">
            <div><div class="section-eyebrow">AI analysis</div><h3>问题分析</h3><p>由诊断流程生成的原因与影响说明。</p></div>
          </div>
          <div class="analysis-content">{{ info.analysisResult || '-' }}</div>
        </article>

        <article class="section-card raw-log-card">
          <div class="section-header">
            <div><div class="section-eyebrow">Source evidence</div><h3>原始日志</h3><p>执行前请结合日志核对目标服务器与组件。</p></div>
          </div>
          <pre class="code-panel detail-pre raw-log">{{ info.rawLog || '-' }}</pre>
        </article>
      </section>

      <section class="section-card action-section">
        <div class="section-header">
          <div><div class="section-eyebrow">Suggested actions</div><h3>处理建议</h3><p>选择后会记录方案并跳转助手；实际执行仍受连接状态与高风险确认约束。</p></div>
          <span class="status-pill status-pill--info">{{ actionList.length }} 个可选方案</span>
        </div>

        <div v-if="actionList.length" class="action-list">
          <article v-for="(action, index) in actionList" :key="`${index}-${action}`" class="action-card">
            <span class="action-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <p>{{ action }}</p>
            <el-button type="primary" :loading="submittingActionIndex === index" @click="handleSelectAction(action, index)">交给助手执行</el-button>
          </article>
        </div>
        <el-empty v-else description="当前诊断未生成处理建议" />
      </section>

      <section class="danger-zone">
        <div><strong>删除这条告警</strong><p>删除后无法恢复，但不会影响服务器监控配置。</p></div>
        <el-button type="danger" plain :loading="deleting" @click="handleDeleteCurrent">删除告警</el-button>
      </section>
    </template>

    <section v-else class="section-card"><el-empty description="未找到对应告警详情"><el-button type="primary" @click="goBack">返回告警中心</el-button></el-empty></section>
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
.alert-detail-page{display:grid;gap:1.25rem}.detail-hero{background:linear-gradient(135deg,#fffdf6 0%,#fff0e2 100%)}.hero-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:.7rem}
.incident-summary{display:grid;gap:1rem;border-left:5px solid #e6ab20}.incident-summary--高{border-left-color:#d95656}.incident-summary--中{border-left-color:#e6ab20}.incident-summary--低{border-left-color:#6f91c8}.incident-summary--无{border-left-color:#69ad38}.incident-heading{display:flex;align-items:center;gap:1rem}.incident-heading>div:last-child{min-width:0}.incident-heading h3{margin:.2rem 0;overflow-wrap:anywhere;color:var(--color-ui-text);font-size:1.25rem}.incident-heading p{margin:0;color:var(--color-ui-subtext);font-size:.82rem}.risk-mark{display:grid;width:3.75rem;height:3.75rem;flex:0 0 auto;place-items:center;border-radius:20px;background:#fff4d6;color:#b98200;font-size:1.15rem;font-weight:900}.risk-mark--高{background:#fdebea;color:#c94444}.risk-mark--低{background:#edf3fb;color:#5679b0}.risk-mark--无{background:#edf7e7;color:#548f2c}.incident-meta-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.75rem}.incident-meta{display:grid;align-content:center;gap:.2rem;min-height:4.2rem;padding:.8rem 1rem}.incident-meta span{color:var(--color-ui-subtext);font-size:.72rem}.incident-meta strong{overflow-wrap:anywhere;color:var(--color-ui-text);font-size:.88rem}
.detail-grid{display:grid;grid-template-columns:minmax(0,1.05fr) minmax(0,.95fr);gap:1.25rem}.analysis-content{overflow-wrap:anywhere;color:var(--color-ui-text);font-size:.9rem;line-height:1.85;white-space:pre-wrap}.detail-pre{max-width:100%;margin:0;white-space:pre-wrap}.raw-log{max-height:24rem;overflow:auto}
.action-list{display:grid;gap:.75rem}.action-card{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:1rem;padding:1rem;border:1px solid var(--color-ui-border);border-radius:18px;background:#fffdf7;transition:transform .2s ease,border-color .2s ease}.action-card:hover{transform:translateY(-1px);border-color:rgba(25,191,174,.38)}.action-index{display:grid;width:2.35rem;height:2.35rem;place-items:center;border-radius:14px;background:#e4f7f3;color:#158f84;font-size:.76rem;font-weight:900}.action-card p{min-width:0;margin:0;overflow-wrap:anywhere;color:var(--color-ui-text);font-size:.87rem;line-height:1.7;white-space:pre-wrap}.danger-zone{display:flex;align-items:center;justify-content:space-between;gap:1rem;padding:1rem 1.1rem;border:1px dashed #e7b7b2;border-radius:18px;background:#fff8f6}.danger-zone strong{color:#b94a4a}.danger-zone p{margin:.2rem 0 0;color:var(--color-ui-subtext);font-size:.78rem}
@media(max-width:850px){.detail-grid{grid-template-columns:1fr}.incident-meta-grid{grid-template-columns:1fr 1fr}.action-card{grid-template-columns:auto 1fr}.action-card .el-button{grid-column:2;justify-self:start}}
@media(max-width:600px){.hero-actions{justify-content:flex-start}.incident-heading{align-items:flex-start}.incident-meta-grid{grid-template-columns:1fr}.action-card{grid-template-columns:1fr}.action-card .el-button{grid-column:auto}.danger-zone{align-items:flex-start;flex-direction:column}}
</style>
