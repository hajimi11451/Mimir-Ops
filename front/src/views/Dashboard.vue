<template>
  <div class="dashboard-view">
    <section class="dashboard-kpi-grid" aria-label="服务器健康概览">
      <article class="dashboard-kpi-card">
        <div class="dashboard-kpi-icon dashboard-kpi-icon--mint" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <rect x="4" y="4" width="16" height="6" rx="2" />
            <rect x="4" y="14" width="16" height="6" rx="2" />
            <path d="M8 7h.01M8 17h.01" />
          </svg>
        </div>
        <div>
          <p class="dashboard-kpi-label">服务器总数</p>
          <strong class="dashboard-kpi-value">{{ dashboardStats.total }}</strong>
          <p class="dashboard-kpi-note">已接入系统监控</p>
        </div>
      </article>

      <article class="dashboard-kpi-card">
        <div class="dashboard-kpi-icon dashboard-kpi-icon--success" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="m7 12 3 3 7-7" />
            <circle cx="12" cy="12" r="9" />
          </svg>
        </div>
        <div>
          <p class="dashboard-kpi-label">健康</p>
          <strong class="dashboard-kpi-value">{{ dashboardStats.success }}</strong>
          <p class="dashboard-kpi-note">核心资源运行稳定</p>
        </div>
      </article>

      <article class="dashboard-kpi-card">
        <div class="dashboard-kpi-icon dashboard-kpi-icon--warning" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M12 8v5m0 3h.01" />
            <path d="M10.3 4.5 3.4 17a2 2 0 0 0 1.75 3h13.7a2 2 0 0 0 1.75-3L13.7 4.5a2 2 0 0 0-3.4 0Z" />
          </svg>
        </div>
        <div>
          <p class="dashboard-kpi-label">需关注</p>
          <strong class="dashboard-kpi-value">{{ dashboardStats.warning }}</strong>
          <p class="dashboard-kpi-note">资源或告警存在波动</p>
        </div>
      </article>

      <article class="dashboard-kpi-card">
        <div class="dashboard-kpi-icon dashboard-kpi-icon--info" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M5 19V9m7 10V5m7 14v-7" />
          </svg>
        </div>
        <div>
          <p class="dashboard-kpi-label">平均健康度</p>
          <strong class="dashboard-kpi-value" :class="`dashboard-kpi-value--${averageHealthLevel}`">
            {{ dashboardStats.averageScore }}%
          </strong>
          <p class="dashboard-kpi-note">更新于 {{ lastUpdatedLabel }}</p>
        </div>
      </article>
    </section>

    <section ref="serverSectionRef" class="dashboard-section dashboard-server-section">
      <header class="dashboard-section-header">
        <div>
          <p class="dashboard-section-eyebrow">Infrastructure</p>
          <h2>服务器态势</h2>
          <p>横向比较健康度与关键资源，选择服务器后可在下方查看完整趋势。</p>
        </div>
        <div class="dashboard-section-actions">
          <el-button :loading="loadingMonitor" @click="handleRefreshSelectedServer">
            <svg class="button-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M20 12a8 8 0 1 1-2.34-5.66M20 4v6h-6" />
            </svg>
            刷新
          </el-button>
          <el-button type="primary" class="dashboard-primary-action" @click="openAddMonitorDialog">
            <svg class="button-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="M12 5v14M5 12h14" />
            </svg>
            添加服务器
          </el-button>
        </div>
      </header>

      <div v-if="loadingMonitor && !serverCards.length" class="dashboard-server-skeleton" aria-label="正在加载服务器">
        <el-skeleton v-for="index in 3" :key="index" :rows="5" animated />
      </div>

      <div v-else-if="!serverCards.length" class="dashboard-empty-state">
        <div class="dashboard-empty-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <rect x="4" y="5" width="16" height="14" rx="3" />
            <path d="M8 10h8M8 14h5" />
          </svg>
        </div>
        <h3>还没有接入服务器</h3>
        <p>添加一台 Linux 服务器后，系统会开始采集 CPU、内存、网络与磁盘数据。</p>
        <el-button type="primary" class="dashboard-primary-action" @click="openAddMonitorDialog">
          添加第一台服务器
        </el-button>
      </div>

      <div v-else class="dashboard-server-grid">
        <button
          v-for="item in serverCards"
          :key="item.key"
          :ref="element => setServerCardRef(element, item.serverIp)"
          type="button"
          class="dashboard-server-card"
          :class="[
            `dashboard-server-card--${item.health.level}`,
            { 'is-selected': selectedServer === item.serverIp },
          ]"
          :aria-pressed="selectedServer === item.serverIp"
          @click="openServerDetail(item.serverIp)"
        >
          <span class="dashboard-server-card__header">
            <span class="dashboard-server-card__identity">
              <span class="dashboard-server-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <rect x="4" y="4" width="16" height="6" rx="2" />
                  <rect x="4" y="14" width="16" height="6" rx="2" />
                  <path d="M8 7h.01M8 17h.01" />
                </svg>
              </span>
              <span class="dashboard-server-card__copy">
                <strong>{{ item.serverIp }}</strong>
                <small>{{ item.subtitle }}</small>
              </span>
            </span>
            <span class="dashboard-monitor-pill" :class="item.monitorEnabled ? 'is-running' : 'is-paused'">
              <span class="dashboard-status-dot" aria-hidden="true"></span>
              {{ item.monitorEnabled ? '监控中' : '已暂停' }}
            </span>
          </span>

          <span class="dashboard-health-row">
            <!-- ai辅助生成：Deepseek网页端+智谱网页端 2026年3月24 -->
            <span class="dashboard-health-score">
              <strong>{{ item.health.score }}%</strong>
              <small>健康度</small>
            </span>
            <span class="dashboard-health-copy">
              <span class="dashboard-health-label">
                <span class="dashboard-health-dot" :class="`is-${item.health.level}`" aria-hidden="true"></span>
                {{ item.health.label }}
              </span>
              <small>{{ item.health.description }}</small>
            </span>
          </span>

          <span class="dashboard-server-metrics">
            <span v-for="metric in item.metrics" :key="metric.key" class="dashboard-server-metric">
              <small>{{ metric.label }}</small>
              <strong :class="metric.valueClass">{{ metric.value }}</strong>
            </span>
          </span>

          <span class="dashboard-server-card__footer">
            <small>更新于 {{ item.lastUpdated }}</small>
            <span>查看详情 <span aria-hidden="true">→</span></span>
          </span>
        </button>
      </div>
    </section>

    <section v-if="selectedServer" ref="detailSectionRef" class="dashboard-section dashboard-detail-section">
      <header class="dashboard-detail-toolbar">
        <div class="dashboard-detail-heading">
          <button type="button" class="dashboard-back-button" @click="handleReturnToServerList">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path d="m15 18-6-6 6-6" />
            </svg>
            返回列表
          </button>
          <div>
            <p class="dashboard-section-eyebrow">Server detail</p>
            <h2>{{ selectedServer }}</h2>
            <p class="dashboard-detail-status" :class="selectedMonitorEnabled ? 'is-running' : 'is-paused'">
              <span class="dashboard-status-dot" aria-hidden="true"></span>
              {{ selectedMonitorEnabled ? '系统监控采集中' : '系统监控已暂停' }}
            </p>
          </div>
        </div>

        <div class="dashboard-detail-actions">
          <el-button :loading="stoppingMonitor" @click="handleStopServerMonitor">
            {{ selectedMonitorEnabled ? '暂停监控' : '恢复监控' }}
          </el-button>
          <el-button :loading="loadingMonitor" @click="handleRefreshSelectedServer">刷新数据</el-button>
          <el-button type="primary" class="dashboard-primary-action" @click="goToAssistant">
            进入 AI 助手
          </el-button>
        </div>
      </header>

      <DashboardOverviewDetail
        class="dashboard-detail-panel"
        :health-state="selectedHealthState"
        :info-list="selectedServerInfoList"
        :loading-info="loadingInfo"
        :server-list="serverList"
        :selected-server="selectedServer"
        :loading-monitor="loadingMonitor"
        :current-info="currentInfo"
        :history-data="historyData"
      />
    </section>

    <el-dialog
      v-model="addMonitorDialogVisible"
      width="34rem"
      class="add-server-dialog"
      modal-class="dashboard-dialog-overlay"
      append-to-body
      :close-on-click-modal="!addMonitorLoading"
      :close-on-press-escape="!addMonitorLoading"
      :show-close="!addMonitorLoading"
      destroy-on-close
      @closed="handleAddDialogClosed"
    >
      <template #header>
        <div class="add-server-dialog__header">
          <span class="add-server-dialog__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <rect x="4" y="5" width="16" height="14" rx="3" />
              <path d="M8 10h8M8 14h5" />
            </svg>
          </span>
          <div>
            <h3>添加服务器</h3>
            <p>填写 SSH 连接信息，保存后立即开始系统指标采集。</p>
          </div>
        </div>
      </template>

      <el-form class="add-server-form" label-position="top" @submit.prevent>
        <el-form-item label="服务器 IP">
          <el-input
            v-model="addMonitorForm.serverIp"
            placeholder="例如 192.168.1.10 或 192.168.1.10:22"
            clearable
            autocomplete="off"
          />
        </el-form-item>

        <div class="add-server-form__credentials">
          <el-form-item label="SSH 用户名">
            <el-input v-model="addMonitorForm.username" placeholder="例如 root" clearable autocomplete="username" />
          </el-form-item>
          <el-form-item label="SSH 密码">
            <el-input
              v-model="addMonitorForm.password"
              type="password"
              placeholder="请输入 SSH 密码"
              show-password
              autocomplete="new-password"
              @keyup.enter="submitAddServerMonitor"
            />
          </el-form-item>
        </div>

        <div class="add-server-collection-note">
          <div class="add-server-collection-note__title">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 11v5m0-8h.01" />
            </svg>
            默认采集范围
          </div>
          <p>系统型号、运行时间、处理器、内存、网卡 I/O 与磁盘 I/O。</p>
          <p>如需监控 Nginx、MySQL 等组件日志，可在“诊断配置”中继续添加。</p>
        </div>
      </el-form>

      <template #footer>
        <div class="add-server-dialog__footer">
          <p>{{ addMonitorLoading ? '正在连接并创建监控，请稍候…' : '连接失败时会保留当前输入，便于检查后重试。' }}</p>
          <div>
            <el-button :disabled="addMonitorLoading" @click="addMonitorDialogVisible = false">取消</el-button>
            <el-button type="primary" class="dashboard-primary-action" :loading="addMonitorLoading" @click="submitAddServerMonitor">
              保存并开始采集
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { addServerMonitor, resumeServerMonitor, stopServerMonitor } from '../api/diagnosis'
import { selectAllInfo } from '../api/info'
import { getSystemDashboard } from '../api/monitor'
import DashboardOverviewDetail from '../components/DashboardOverviewDetail.vue'
import {
  formatDate,
  getDateTimestamp,
  normalizeMonitorSettings,
  normalizeRiskLevel,
  resolveSystemHealth,
} from '../utils/dashboardHealth'

const router = useRouter()
const serverSectionRef = ref(null)
const detailSectionRef = ref(null)
const serverCardElements = ref({})

const infoList = ref([])
const loadingInfo = ref(false)

const currentInfo = ref({})
const historyData = ref([])
const serverList = ref([])
const selectedServer = ref('')
const serverMonitorMap = ref({})
const loadingMonitor = ref(false)
const lastUpdatedAt = ref('')

const addMonitorDialogVisible = ref(false)
const addMonitorLoading = ref(false)
const stoppingMonitor = ref(false)

const defaultMonitorSettings = () => ({
  cpuEnabled: true,
  memEnabled: true,
  netRxEnabled: true,
  netTxEnabled: true,
  diskReadEnabled: true,
  diskWriteEnabled: true,
})

const addMonitorForm = reactive({
  serverIp: '',
  username: '',
  password: '',
  ...defaultMonitorSettings(),
})

let refreshTimer = null

const selectedServerInfoList = computed(() => {
  if (!selectedServer.value) return infoList.value
  return infoList.value.filter(item => item.serverIp === selectedServer.value)
})

const lastUpdatedLabel = computed(() => lastUpdatedAt.value ? formatDate(lastUpdatedAt.value) : '等待刷新')

const buildUsageTone = usage => {
  if (usage >= 85) return { text: 'text-ui-error' }
  if (usage >= 70) return { text: 'text-ui-warning' }
  return { text: 'text-ui-success' }
}

// 通用服务器的经验阈值：绿色表示日常波动，橙色表示明显偏高，红色表示较少见的高占用。
const metricRateThresholds = {
  network: {
    warning: 5 * 1024 ** 2,
    error: 20 * 1024 ** 2,
  },
  disk: {
    warning: 12 * 1024 ** 2,
    error: 48 * 1024 ** 2,
  },
}

const buildRateTone = (value, thresholds) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return { text: 'text-ui-subtext' }
  if (parsed >= thresholds.error) return { text: 'text-ui-error' }
  if (parsed >= thresholds.warning) return { text: 'text-ui-warning' }
  return { text: 'text-ui-success' }
}

const formatCompactRate = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return '--'
  if (parsed >= 1024 ** 3) return `${(parsed / 1024 ** 3).toFixed(1)}G/s`
  if (parsed >= 1024 ** 2) return `${(parsed / 1024 ** 2).toFixed(1)}M/s`
  if (parsed >= 1024) return `${(parsed / 1024).toFixed(1)}K/s`
  return `${parsed.toFixed(0)}B/s`
}

const buildServerMetricItems = (snapshot, health) => {
  const current = snapshot?.current || {}
  const settings = normalizeMonitorSettings(current.monitorSettings)

  return [
    {
      key: 'cpu',
      label: 'CPU',
      value: settings.cpuEnabled ? `${health.cpuUsage}%` : 'OFF',
      valueClass: settings.cpuEnabled ? buildUsageTone(health.cpuUsage).text : 'text-ui-subtext',
    },
    {
      key: 'mem',
      label: 'MEM',
      value: settings.memEnabled ? `${health.memUsage}%` : 'OFF',
      valueClass: settings.memEnabled ? buildUsageTone(health.memUsage).text : 'text-ui-subtext',
    },
    {
      key: 'rx',
      label: 'RX',
      value: settings.netRxEnabled ? formatCompactRate(current.netRxBytesPerSec) : 'OFF',
      valueClass: settings.netRxEnabled
        ? buildRateTone(current.netRxBytesPerSec, metricRateThresholds.network).text
        : 'text-ui-subtext',
    },
    {
      key: 'tx',
      label: 'TX',
      value: settings.netTxEnabled ? formatCompactRate(current.netTxBytesPerSec) : 'OFF',
      valueClass: settings.netTxEnabled
        ? buildRateTone(current.netTxBytesPerSec, metricRateThresholds.network).text
        : 'text-ui-subtext',
    },
    {
      key: 'read',
      label: 'READ',
      value: settings.diskReadEnabled ? formatCompactRate(current.diskReadBytesPerSec) : 'OFF',
      valueClass: settings.diskReadEnabled
        ? buildRateTone(current.diskReadBytesPerSec, metricRateThresholds.disk).text
        : 'text-ui-subtext',
    },
    {
      key: 'write',
      label: 'WRITE',
      value: settings.diskWriteEnabled ? formatCompactRate(current.diskWriteBytesPerSec) : 'OFF',
      valueClass: settings.diskWriteEnabled
        ? buildRateTone(current.diskWriteBytesPerSec, metricRateThresholds.disk).text
        : 'text-ui-subtext',
    },
  ]
}

const getServerInfoList = serverIp => {
  if (!serverIp) return []
  return infoList.value.filter(item => item.serverIp === serverIp)
}

const buildPendingHealthState = infoItems => {
  const baseState = resolveSystemHealth({
    currentInfo: { cpuUsage: 0, memUsage: 0 },
    infoList: infoItems,
  })

  if (baseState.level !== 'success' || baseState.activeAlertCount > 0) {
    return {
      ...baseState,
      description: `${baseState.description} 当前监控数据暂未刷新，资源占用按 0% 暂存显示。`,
      reasons: [
        ...baseState.reasons,
        '当前服务器尚未拿到最新系统监控采样数据，将在下一次采样后刷新。',
      ],
    }
  }

  return {
    ...baseState,
    score: 0,
    label: '待采样',
    level: 'warning',
    description: '暂未获取到系统监控数据，请稍候等待首次采样。',
    reasons: ['当前服务器还没有最新监控数据，系统将继续采集已启用的资源指标。'],
  }
}

const hasBackendHealthState = healthState => healthState && typeof healthState === 'object' && Object.keys(healthState).length > 0

const resolveSnapshotHealthState = (snapshot, infoItems) => {
  if (hasBackendHealthState(snapshot?.healthState)) {
    return snapshot.healthState
  }

  const hasData = snapshot?.current && Object.keys(snapshot.current).length > 0
  if (!hasData) {
    return buildPendingHealthState(infoItems)
  }

  return resolveSystemHealth({
    currentInfo: snapshot.current || {},
    infoList: infoItems,
  })
}

const selectedHealthState = computed(() => {
  const snapshot = serverMonitorMap.value[selectedServer.value] || {}
  return resolveSnapshotHealthState(snapshot, selectedServerInfoList.value)
})

const selectedMonitorEnabled = computed(() => {
  const snapshot = serverMonitorMap.value[selectedServer.value] || {}
  return snapshot.monitorEnabled !== false
})

const serverCards = computed(() => serverList.value.map(serverIp => {
  const snapshot = serverMonitorMap.value[serverIp] || {}
  const serverInfoList = getServerInfoList(serverIp)
  const health = resolveSnapshotHealthState(snapshot, serverInfoList)

  return {
    key: serverIp,
    serverIp,
    subtitle: snapshot.monitorEnabled === false
      ? '检测已暂停，保留最近一次数据'
      : (snapshot.current?.os || '等待首次系统监控采样'),
    health,
    monitorEnabled: snapshot.monitorEnabled !== false,
    lastUpdated: snapshot.fetchedAt ? formatDate(snapshot.fetchedAt) : '等待刷新',
    metrics: buildServerMetricItems(snapshot, health),
  }
}))

const dashboardStats = computed(() => {
  const cards = serverCards.value
  const total = cards.length
  const success = cards.filter(card => card.health.level === 'success').length
  const warning = cards.filter(card => card.health.level === 'warning').length
  const averageScore = total
    ? Math.round(cards.reduce((sum, card) => sum + card.health.score, 0) / total)
    : 0

  return {
    total,
    success,
    warning,
    averageScore,
  }
})

const averageHealthLevel = computed(() => {
  if (dashboardStats.value.averageScore >= 80) return 'success'
  if (dashboardStats.value.averageScore >= 60) return 'warning'
  return 'error'
})

const setServerCardRef = (element, serverIp) => {
  if (element) {
    serverCardElements.value[serverIp] = element
    return
  }
  delete serverCardElements.value[serverIp]
}

const syncSelectedSnapshot = () => {
  const snapshot = serverMonitorMap.value[selectedServer.value]
  currentInfo.value = snapshot?.current || {}
  historyData.value = snapshot?.history || []
}

const applyServerSnapshot = (serverIp, payload) => {
  if (!serverIp) return

  serverMonitorMap.value = {
    ...serverMonitorMap.value,
    [serverIp]: {
      serverIp,
      current: payload?.current || {},
      history: Array.isArray(payload?.history) ? payload.history : [],
      healthState: hasBackendHealthState(payload?.healthState) ? payload.healthState : null,
      monitorEnabled: payload?.monitorEnabled !== false,
      fetchedAt: Date.now(),
    },
  }
}

const fetchInfo = async () => {
  loadingInfo.value = true

  try {
    const res = await selectAllInfo()

    if (Array.isArray(res)) {
      infoList.value = res
        .map(item => ({
          ...item,
          riskLevel: normalizeRiskLevel(item?.riskLevel),
        }))
        .sort((left, right) => getDateTimestamp(right.createdAt) - getDateTimestamp(left.createdAt))
    } else {
      infoList.value = []
    }
  } catch (error) {
    console.error('Failed to fetch info:', error)
  } finally {
    loadingInfo.value = false
  }
}

const fetchServerSnapshot = async serverIp => {
  if (!serverIp) return null

  try {
    const res = await getSystemDashboard(serverIp)
    applyServerSnapshot(serverIp, res)
    if (selectedServer.value === serverIp) {
      syncSelectedSnapshot()
    }
    return res
  } catch (error) {
    console.error(`Failed to fetch monitor data for ${serverIp}:`, error)
    return null
  }
}

const refreshDashboard = async preferredServer => {
  loadingMonitor.value = true

  try {
    const overview = await getSystemDashboard(preferredServer || selectedServer.value)
    const nextServerList = Array.from(new Set((overview?.servers || []).filter(Boolean)))

    serverList.value = nextServerList

    const targetServer = nextServerList.includes(preferredServer)
      ? preferredServer
      : (nextServerList.includes(overview?.selectedIp)
        ? overview.selectedIp
        : (nextServerList.includes(selectedServer.value) ? selectedServer.value : (nextServerList[0] || '')))
    selectedServer.value = targetServer

    if (!nextServerList.length) {
      serverMonitorMap.value = {}
      currentInfo.value = {}
      historyData.value = []
      return
    }

    const nextMonitorMap = {}
    nextServerList.forEach(serverIp => {
      if (serverMonitorMap.value[serverIp]) {
        nextMonitorMap[serverIp] = serverMonitorMap.value[serverIp]
      }
    })
    serverMonitorMap.value = nextMonitorMap

    if (overview?.selectedIp) {
      applyServerSnapshot(overview.selectedIp, overview)
    }

    await Promise.allSettled(
      nextServerList.map(serverIp => {
        if (serverIp === overview?.selectedIp) return Promise.resolve(overview)
        return fetchServerSnapshot(serverIp)
      }),
    )

    syncSelectedSnapshot()
    lastUpdatedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to refresh dashboard:', error)
  } finally {
    loadingMonitor.value = false
  }
}

const scrollCardIntoViewByIp = serverIp => {
  const element = serverCardElements.value[serverIp]
  element?.scrollIntoView({ behavior: getPreferredScrollBehavior(), block: 'nearest' })
}

const openServerDetail = async serverIp => {
  selectedServer.value = serverIp
  syncSelectedSnapshot()

  const snapshot = serverMonitorMap.value[serverIp]
  if (!snapshot?.current || !Object.keys(snapshot.current).length) {
    await fetchServerSnapshot(serverIp)
  }

  await nextTick()
  detailSectionRef.value?.scrollIntoView({ behavior: getPreferredScrollBehavior(), block: 'start' })
}

const getPreferredScrollBehavior = () => (
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
)

const handleReturnToServerList = () => {
  serverSectionRef.value?.scrollIntoView({ behavior: getPreferredScrollBehavior(), block: 'start' })
  scrollCardIntoViewByIp(selectedServer.value)
}

const handleRefreshSelectedServer = async () => {
  await Promise.allSettled([
    fetchInfo(),
    refreshDashboard(selectedServer.value),
  ])
}

const goToAssistant = () => {
  router.push('/ops-assistant')
}

const resetAddMonitorForm = () => {
  Object.assign(addMonitorForm, {
    serverIp: '',
    username: '',
    password: '',
    ...defaultMonitorSettings(),
  })
}

const openAddMonitorDialog = () => {
  addMonitorDialogVisible.value = true
}

const handleAddDialogClosed = () => {
  if (!addMonitorLoading.value) {
    resetAddMonitorForm()
  }
}

const submitAddServerMonitor = async () => {
  const serverIp = addMonitorForm.serverIp.trim()
  const username = addMonitorForm.username.trim()
  const password = addMonitorForm.password

  if (!serverIp || !username || !password) {
    ElMessage.warning('请完整填写服务器 IP、SSH 用户名和密码')
    return
  }

  addMonitorLoading.value = true

  try {
    await addServerMonitor({
      serverIp,
      username,
      password,
      ...defaultMonitorSettings(),
    })
    ElMessage.success('服务器监控已添加，默认开启全量采集')
    addMonitorDialogVisible.value = false
    resetAddMonitorForm()

    await refreshDashboard(serverIp)
    await nextTick()
    scrollCardIntoViewByIp(serverIp)
  } catch (error) {
    ElMessage.error(error?.message || '新增服务器失败')
  } finally {
    addMonitorLoading.value = false
  }
}

const handleStopServerMonitor = async () => {
  if (!selectedServer.value) {
    ElMessage.warning('当前没有可操作的服务器监控')
    return
  }

  const shouldDisable = selectedMonitorEnabled.value

  try {
    await ElMessageBox.confirm(
      shouldDisable
        ? `暂停后将暂时停止采集 ${selectedServer.value} 的 CPU 和内存数据，但状态盘仍会保留，是否继续？`
        : `恢复后将重新开始采集 ${selectedServer.value} 的 CPU 和内存数据，是否继续？`,
      shouldDisable ? '暂停检测' : '恢复检测',
      {
        confirmButtonText: shouldDisable ? '暂停检测' : '恢复检测',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  stoppingMonitor.value = true

  try {
    const targetServer = selectedServer.value
    if (shouldDisable) {
      await stopServerMonitor(targetServer)
      ElMessage.success(`已暂停 ${targetServer} 的服务器检测`)
    } else {
      await resumeServerMonitor(targetServer)
      ElMessage.success(`已恢复 ${targetServer} 的服务器检测`)
    }

    await refreshDashboard(targetServer)
  } catch (error) {
    ElMessage.error(error?.message || `${shouldDisable ? '暂停' : '恢复'}服务器监控失败`)
  } finally {
    stoppingMonitor.value = false
  }
}

onMounted(async () => {
  await Promise.allSettled([
    fetchInfo(),
    refreshDashboard(),
  ])
  refreshTimer = setInterval(async () => {
    await Promise.allSettled([
      fetchInfo(),
      refreshDashboard(selectedServer.value),
    ])
  }, 60000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.dashboard-view {
  display: flex;
  width: 100%;
  min-width: 0;
  max-width: 1600px;
  margin: 0 auto;
  flex-direction: column;
  gap: 20px;
  color: var(--app-text, #4f3b2b);
}

.dashboard-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-kpi-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--app-border, #ddd2b9);
  border-radius: var(--app-radius-card, 20px);
  background: var(--app-panel, #fffdf6);
  box-shadow: var(--app-shadow-soft, 0 10px 26px -24px rgba(79, 59, 43, 0.3));
}

.dashboard-kpi-icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 15px;
}

.dashboard-kpi-icon svg,
.dashboard-empty-icon svg,
.dashboard-server-icon svg,
.add-server-dialog__icon svg {
  width: 23px;
  height: 23px;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.dashboard-kpi-icon--mint {
  color: var(--app-primary-active, #109b8f);
  background: var(--app-primary-soft, #dff6f1);
}

.dashboard-kpi-icon--success {
  color: #4f8c2b;
  background: #e8f3dd;
}

.dashboard-kpi-icon--warning {
  color: #a97408;
  background: #fff1c7;
}

.dashboard-kpi-icon--info {
  color: #5578aa;
  background: #e8eef8;
}

.dashboard-kpi-label,
.dashboard-kpi-note,
.dashboard-section-eyebrow,
.dashboard-section-header p,
.dashboard-detail-heading p,
.add-server-dialog__header p,
.add-server-dialog__footer p,
.add-server-collection-note p {
  margin: 0;
}

.dashboard-kpi-label {
  color: var(--app-text-muted, #8f806d);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.dashboard-kpi-value {
  display: block;
  margin-top: 2px;
  color: var(--app-text, #4f3b2b);
  font-size: clamp(24px, 2.2vw, 32px);
  line-height: 1.15;
}

.dashboard-kpi-value--success {
  color: var(--app-success, #69ad38);
}

.dashboard-kpi-value--warning {
  color: #b47c08;
}

.dashboard-kpi-value--error {
  color: var(--app-danger, #d95656);
}

.dashboard-kpi-note {
  max-width: 100%;
  margin-top: 4px;
  overflow: hidden;
  color: var(--app-text-muted, #8f806d);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-section {
  min-width: 0;
  scroll-margin-top: 18px;
  border: 1px solid var(--app-border, #ddd2b9);
  border-radius: var(--app-radius-page, 24px);
  background: var(--app-panel, #fffdf6);
  box-shadow: var(--app-shadow-panel, 0 16px 40px -30px rgba(79, 59, 43, 0.36));
}

.dashboard-server-section {
  padding: clamp(18px, 2vw, 28px);
}

.dashboard-section-header,
.dashboard-detail-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.dashboard-section-header h2,
.dashboard-detail-toolbar h2 {
  margin: 3px 0 0;
  color: var(--app-text, #4f3b2b);
  font-size: clamp(20px, 2vw, 26px);
  font-weight: 800;
  letter-spacing: -0.02em;
}

.dashboard-section-eyebrow {
  color: var(--app-primary-active, #109b8f);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.dashboard-section-header > div:first-child > p:last-child {
  margin-top: 7px;
  color: var(--app-text-body, #725d42);
  font-size: 14px;
  line-height: 1.6;
}

.dashboard-section-actions,
.dashboard-detail-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.dashboard-section-actions :deep(.el-button),
.dashboard-detail-actions :deep(.el-button) {
  min-height: 40px;
  margin-left: 0;
}

.button-icon {
  width: 17px;
  height: 17px;
  margin-right: 4px;
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.dashboard-primary-action {
  font-weight: 800;
}

.dashboard-server-grid,
.dashboard-server-skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 290px), 1fr));
  gap: 16px;
  margin-top: 22px;
}

.dashboard-server-skeleton > * {
  min-height: 260px;
  padding: 18px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 18px;
  background: var(--app-panel-strong, #fffef9);
}

.dashboard-server-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 278px;
  appearance: none;
  flex-direction: column;
  gap: 16px;
  padding: 18px;
  overflow: hidden;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 20px;
  outline: none;
  background: var(--app-panel-strong, #fffef9);
  color: var(--app-text, #4f3b2b);
  box-shadow: 0 8px 20px -22px rgba(79, 59, 43, 0.32);
  cursor: pointer;
  text-align: left;
  transition:
    transform var(--app-motion-base, 220ms) var(--app-ease, ease),
    border-color var(--app-motion-base, 220ms) var(--app-ease, ease),
    box-shadow var(--app-motion-base, 220ms) var(--app-ease, ease);
}

.dashboard-server-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--app-info, #6f91c8);
  content: "";
}

.dashboard-server-card--success::before {
  background: var(--app-success, #69ad38);
}

.dashboard-server-card--warning::before {
  background: var(--app-warning, #e6ab20);
}

.dashboard-server-card--error::before {
  background: var(--app-danger, #d95656);
}

.dashboard-server-card:hover {
  transform: translateY(-2px);
  border-color: #c9bfa7;
  box-shadow: var(--app-shadow-raised, 0 18px 36px -24px rgba(79, 59, 43, 0.36));
}

.dashboard-server-card.is-selected {
  border-color: var(--app-primary, #19bfae);
  box-shadow: 0 0 0 3px rgba(25, 191, 174, 0.13);
}

.dashboard-server-card:focus-visible,
.dashboard-back-button:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--app-focus, #e8b62e) 70%, transparent);
  outline-offset: 3px;
}

.dashboard-server-card__header,
.dashboard-server-card__identity,
.dashboard-health-row,
.dashboard-health-label,
.dashboard-server-card__footer {
  display: flex;
}

.dashboard-server-card__header {
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.dashboard-server-card__identity {
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.dashboard-server-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 13px;
  background: var(--app-panel-soft, #f3eddb);
  color: var(--app-text-body, #725d42);
}

.dashboard-server-card__copy {
  display: block;
  min-width: 0;
}

.dashboard-server-card__copy strong {
  display: block;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-server-card__copy small {
  display: block;
  max-width: 190px;
  margin-top: 3px;
  overflow: hidden;
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-monitor-pill,
.dashboard-detail-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
  font-weight: 800;
}

.dashboard-monitor-pill {
  flex: 0 0 auto;
  padding: 6px 9px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 999px;
  background: var(--app-panel-soft, #f3eddb);
}

.dashboard-monitor-pill.is-running,
.dashboard-detail-status.is-running {
  color: #4f8c2b;
}

.dashboard-monitor-pill.is-paused,
.dashboard-detail-status.is-paused {
  color: #a97408;
}

.dashboard-status-dot,
.dashboard-health-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: currentColor;
}

.dashboard-health-row {
  align-items: center;
  gap: 14px;
  padding: 13px;
  border-radius: 16px;
  background: var(--app-panel-soft, #f3eddb);
}

.dashboard-health-score {
  display: flex;
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  border: 1px solid var(--app-border, #ddd2b9);
  border-radius: 50%;
  background: var(--app-panel-strong, #fffef9);
}

.dashboard-health-score strong {
  font-size: 18px;
  line-height: 1.1;
}

.dashboard-health-score small {
  margin-top: 3px;
  color: var(--app-text-muted, #8f806d);
  font-size: 10px;
}

.dashboard-health-copy {
  display: block;
  min-width: 0;
}

.dashboard-health-label {
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 800;
}

.dashboard-health-dot.is-success {
  color: var(--app-success, #69ad38);
}

.dashboard-health-dot.is-warning {
  color: var(--app-warning, #e6ab20);
}

.dashboard-health-dot.is-error {
  color: var(--app-danger, #d95656);
}

.dashboard-health-copy > small {
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: var(--app-text-body, #725d42);
  font-size: 11px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.dashboard-server-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.dashboard-server-metric {
  display: block;
  min-width: 0;
  padding: 9px 8px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 12px;
  background: #fbf8ed;
}

.dashboard-server-metric small,
.dashboard-server-metric strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-server-metric small {
  color: var(--app-text-muted, #8f806d);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.dashboard-server-metric strong {
  margin-top: 3px;
  font-size: 12px;
}

.dashboard-server-card__footer {
  margin-top: auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
}

.dashboard-server-card__footer small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-server-card__footer > span {
  flex: 0 0 auto;
  color: var(--app-primary-active, #109b8f);
  font-size: 12px;
  font-weight: 800;
}

.dashboard-empty-state {
  display: flex;
  min-height: 300px;
  margin-top: 22px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 32px;
  border: 1px dashed var(--app-border, #ddd2b9);
  border-radius: 20px;
  background: #fbf8ed;
  text-align: center;
}

.dashboard-empty-icon {
  display: inline-flex;
  width: 64px;
  height: 64px;
  align-items: center;
  justify-content: center;
  border-radius: 20px 20px 24px 16px;
  background: var(--app-primary-soft, #dff6f1);
  color: var(--app-primary-active, #109b8f);
  transform: rotate(-2deg);
}

.dashboard-empty-state h3 {
  margin: 16px 0 0;
  font-size: 18px;
}

.dashboard-empty-state p {
  max-width: 500px;
  margin: 8px 0 20px;
  color: var(--app-text-body, #725d42);
  font-size: 14px;
  line-height: 1.65;
}

.dashboard-detail-section {
  overflow: hidden;
}

.dashboard-detail-toolbar {
  padding: 18px clamp(18px, 2vw, 26px);
  border-bottom: 1px solid var(--app-border-soft, #e9e1cf);
  background: #fbf8ed;
}

.dashboard-detail-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.dashboard-detail-heading h2 {
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: clamp(18px, 2vw, 23px);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-detail-status {
  margin-top: 4px;
}

.dashboard-back-button {
  display: inline-flex;
  min-width: 42px;
  min-height: 42px;
  appearance: none;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 0 12px;
  border: 1px solid var(--app-border, #ddd2b9);
  border-radius: 13px;
  outline: none;
  background: var(--app-panel, #fffdf6);
  color: var(--app-text-body, #725d42);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  transition: border-color var(--app-motion-fast, 160ms) var(--app-ease, ease);
}

.dashboard-back-button:hover {
  border-color: var(--app-primary, #19bfae);
  color: var(--app-primary-active, #109b8f);
}

.dashboard-back-button svg {
  width: 17px;
  height: 17px;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.dashboard-detail-panel {
  min-width: 0;
  padding: clamp(16px, 2vw, 24px);
}

.add-server-dialog__header {
  display: flex;
  align-items: center;
  gap: 13px;
}

.add-server-dialog__header h3 {
  margin: 0;
  color: var(--app-text, #4f3b2b);
  font-size: 20px;
  font-weight: 800;
}

.add-server-dialog__header p {
  margin-top: 4px;
  color: var(--app-text-muted, #8f806d);
  font-size: 13px;
  line-height: 1.5;
}

.add-server-dialog__icon {
  display: inline-flex;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 15px;
  background: var(--app-primary-soft, #dff6f1);
  color: var(--app-primary-active, #109b8f);
}

.add-server-form__credentials {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.add-server-collection-note {
  margin-top: 4px;
  padding: 14px 16px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 16px;
  background: var(--app-panel-soft, #f3eddb);
}

.add-server-collection-note__title {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--app-text, #4f3b2b);
  font-size: 13px;
  font-weight: 800;
}

.add-server-collection-note__title svg {
  width: 17px;
  height: 17px;
  color: var(--app-info, #6f91c8);
  stroke-width: 1.9;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.add-server-collection-note p {
  margin-top: 7px;
  color: var(--app-text-body, #725d42);
  font-size: 12px;
  line-height: 1.55;
}

.add-server-dialog__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.add-server-dialog__footer p {
  max-width: 250px;
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
  line-height: 1.45;
  text-align: left;
}

.add-server-dialog__footer > div {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

:global(.add-server-dialog) {
  max-width: calc(100vw - 32px);
  margin-top: max(32px, 8vh);
  overflow: hidden;
  border: 1px solid var(--app-border, #ddd2b9);
  border-radius: 22px;
  background: var(--app-panel, #fffdf6);
  box-shadow: 0 24px 60px -30px rgba(79, 59, 43, 0.46);
  backdrop-filter: none;
}

:global(.add-server-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--app-border-soft, #e9e1cf);
}

:global(.add-server-dialog .el-dialog__headerbtn) {
  top: 13px;
  right: 13px;
}

:global(.add-server-dialog .el-dialog__body) {
  padding: 20px 22px 10px;
}

:global(.add-server-dialog .el-dialog__footer) {
  padding: 16px 22px 20px;
  border-top: 1px solid var(--app-border-soft, #e9e1cf);
  background: #fbf8ed;
}

:global(.dashboard-dialog-overlay) {
  background: rgba(79, 59, 43, 0.28);
}

@media (max-width: 1100px) {
  .dashboard-kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-server-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 767px) {
  .dashboard-view {
    gap: 14px;
  }

  .dashboard-kpi-grid {
    gap: 10px;
  }

  .dashboard-kpi-card {
    align-items: flex-start;
    gap: 9px;
    padding: 13px;
  }

  .dashboard-kpi-icon {
    width: 36px;
    height: 36px;
    border-radius: 12px;
  }

  .dashboard-kpi-icon svg {
    width: 19px;
    height: 19px;
  }

  .dashboard-kpi-value {
    font-size: 22px;
  }

  .dashboard-kpi-note {
    white-space: normal;
  }

  .dashboard-server-section {
    padding: 16px;
  }

  .dashboard-section-header,
  .dashboard-detail-toolbar {
    flex-direction: column;
  }

  .dashboard-section-actions,
  .dashboard-detail-actions {
    width: 100%;
    justify-content: stretch;
  }

  .dashboard-section-actions :deep(.el-button),
  .dashboard-detail-actions :deep(.el-button) {
    flex: 1 1 auto;
  }

  .dashboard-server-grid,
  .dashboard-server-skeleton {
    grid-template-columns: minmax(0, 1fr);
    margin-top: 18px;
  }

  .dashboard-server-card {
    min-height: 0;
  }

  .dashboard-server-card__copy small {
    max-width: 145px;
  }

  .dashboard-detail-heading {
    width: 100%;
  }

  .dashboard-back-button {
    padding: 0 9px;
  }

  .dashboard-detail-toolbar {
    padding: 16px;
  }

  .dashboard-detail-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-detail-panel {
    padding: 12px;
  }

  .add-server-form__credentials {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
  }

  .add-server-dialog__footer {
    align-items: stretch;
    flex-direction: column;
  }

  .add-server-dialog__footer p {
    max-width: none;
  }

  .add-server-dialog__footer > div {
    justify-content: flex-end;
  }

  :global(.add-server-dialog) {
    margin-top: 16px;
  }

  :global(.add-server-dialog .el-dialog__header) {
    padding: 17px 18px 14px;
  }

  :global(.add-server-dialog .el-dialog__body) {
    max-height: calc(100vh - 260px);
    overflow-y: auto;
    padding: 17px 18px 8px;
  }

  :global(.add-server-dialog .el-dialog__footer) {
    padding: 14px 18px 17px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-server-card,
  .dashboard-back-button {
    scroll-behavior: auto;
    transition: none;
  }

  .dashboard-server-card:hover {
    transform: none;
  }
}
</style>
