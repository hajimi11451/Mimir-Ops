<template>
  <div class="monitor-detail-layout">
    <section class="monitor-detail-panel monitor-trend-panel">
      <header class="monitor-panel-header">
        <div>
          <p class="monitor-panel-eyebrow">Live metrics</p>
          <h3>资源趋势</h3>
          <p :class="healthTone.softText">{{ healthState.description }}</p>
        </div>
        <span class="monitor-health-badge" :class="healthTone.chip">
          <span class="monitor-health-badge__icon" aria-hidden="true">{{ healthStatusSymbol }}</span>
          {{ healthState.label || '健康度' }} · {{ healthState.score }}%
        </span>
      </header>

      <div class="monitor-metric-grid" :aria-label="`已启用 ${enabledMetricLabels.length} 项监控指标`">
        <article
          v-for="badge in monitorMetricBadges"
          :key="badge.key"
          class="monitor-metric-card"
          :class="badge.badgeClass"
        >
          <span>{{ badge.label }}</span>
          <strong>{{ badge.value }}</strong>
        </article>
        <div v-if="!monitorMetricBadges.length" class="monitor-metric-empty">
          当前未启用任何系统指标采集
        </div>
      </div>

      <div class="monitor-chart-card" @wheel.shift.prevent="handleTrendSliderWheel">
        <div class="monitor-chart-header">
          <div>
            <strong>历史变化</strong>
            <span>{{ currentWindowTimeRange }}</span>
          </div>
          <span>保留时长 {{ totalHistoryDuration }}</span>
        </div>

        <div class="monitor-chart-frame" aria-live="polite">
          <canvas v-if="hasChartData" ref="monitorChartRef"></canvas>
          <div v-else class="monitor-chart-empty">
            <span class="monitor-chart-empty__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
                <path d="M5 19V9m7 10V5m7 14v-7" />
              </svg>
            </span>
            <span>{{ loadingMonitor ? '正在加载监控数据…' : emptyChartLabel }}</span>
          </div>
        </div>

        <div v-if="showTrendSlider" class="monitor-trend-control">
          <div>
            <span>浏览时间窗口</span>
            <span>按住 Shift 滚轮可快速移动</span>
          </div>
          <input
            v-model="trendWindowStart"
            class="trend-slider"
            type="range"
            min="0"
            :max="maxTrendWindowStart"
            step="1"
            aria-label="监控趋势时间窗口"
            :aria-valuetext="currentWindowTimeRange"
          />
        </div>
      </div>
    </section>

    <aside class="monitor-side-column">
      <section class="monitor-detail-panel monitor-system-panel">
        <header class="monitor-side-header">
          <div>
            <p class="monitor-panel-eyebrow">Machine</p>
            <h3>主机信息</h3>
          </div>
          <span>{{ selectedServer || '当前服务器' }}</span>
        </header>

        <dl class="monitor-system-list">
          <div>
            <dt>操作系统</dt>
            <dd>{{ currentInfo.os || 'N/A' }}</dd>
          </div>
          <div>
            <dt>运行时间</dt>
            <dd>{{ currentInfo.upTime || 'N/A' }}</dd>
          </div>
          <div>
            <dt>处理器</dt>
            <dd>{{ currentInfo.processor || 'N/A' }}</dd>
          </div>
          <div>
            <dt>可用 / 总内存</dt>
            <dd>{{ currentInfo.availableMemory || 'N/A' }} / {{ currentInfo.totalMemory || 'N/A' }}</dd>
          </div>
          <div>
            <dt>网络 RX / TX</dt>
            <dd>{{ formatRate(currentInfo.netRxBytesPerSec) }} / {{ formatRate(currentInfo.netTxBytesPerSec) }}</dd>
          </div>
          <div>
            <dt>磁盘读 / 写</dt>
            <dd>{{ formatRate(currentInfo.diskReadBytesPerSec) }} / {{ formatRate(currentInfo.diskWriteBytesPerSec) }}</dd>
          </div>
        </dl>
      </section>

      <section class="monitor-detail-panel monitor-alert-panel">
        <header class="monitor-side-header">
          <div>
            <p class="monitor-panel-eyebrow">Incidents</p>
            <h3>最新告警</h3>
          </div>
          <router-link to="/info-list">查看全部</router-link>
        </header>

        <div class="monitor-alert-list custom-scrollbar" aria-live="polite">
          <div v-if="loadingInfo" class="monitor-side-empty">正在加载告警…</div>
          <div v-else-if="infoList.length === 0" class="monitor-side-empty">当前服务器暂无告警记录</div>
          <router-link
            v-for="(info, index) in infoList"
            v-else
            :key="info.id || index"
            :to="info.id ? `/info-list/${info.id}` : '/info-list'"
            class="monitor-alert-item"
            :class="getAlertClass(info.riskLevel)"
          >
            <span class="monitor-alert-symbol" :class="getAlertDotClass(info.riskLevel)" aria-hidden="true">
              {{ getAlertSymbol(info.riskLevel) }}
            </span>
            <span class="monitor-alert-copy">
              <span class="monitor-alert-title">
                <strong>{{ getCompactAlertTitle(info) }}</strong>
                <span :class="getAlertChipClass(info.riskLevel)">{{ normalizeRiskLevel(info.riskLevel) }}</span>
              </span>
              <span class="monitor-alert-summary">{{ getCompactAlertSummary(info.errorSummary) }}</span>
              <span class="monitor-alert-meta">
                <span>{{ info.serverIp || '未知服务器' }}</span>
                <span>{{ formatDate(info.createdAt) }}</span>
              </span>
            </span>
          </router-link>
        </div>
      </section>
    </aside>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import {
  formatDate,
  getAlertClass,
  normalizeMonitorSettings,
  normalizeRiskLevel,
} from '../utils/dashboardHealth'

const props = defineProps({
  healthState: {
    type: Object,
    default: () => ({
      score: 100,
      description: '',
      activeAlertCount: 0,
      highRiskCount: 0,
      mediumRiskCount: 0,
      lowRiskCount: 0,
      normalCount: 0,
      totalLogsCount: 0,
      level: 'success',
    }),
  },
  infoList: {
    type: Array,
    default: () => [],
  },
  loadingInfo: {
    type: Boolean,
    default: false,
  },
  serverList: {
    type: Array,
    default: () => [],
  },
  selectedServer: {
    type: String,
    default: '',
  },
  loadingMonitor: {
    type: Boolean,
    default: false,
  },
  currentInfo: {
    type: Object,
    default: () => ({}),
  },
  historyData: {
    type: Array,
    default: () => [],
  },
})

const formatDurationFromSamples = sampleCount => {
  if (sampleCount <= 0) {
    return 'N/A'
  }

  const sampleIntervalSeconds = 30
  const totalSeconds = Math.max(sampleCount - 1, 0) * sampleIntervalSeconds
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (days > 0) return `${days} 天 ${hours} 小时`
  if (hours > 0) return `${hours} 小时 ${minutes} 分钟`
  if (minutes > 0) return `${minutes} 分钟 ${seconds} 秒`
  return `${seconds} 秒`
}

const formatTrendTimeLabel = value => {
  const text = String(value || '').trim()
  const matched = text.match(/(\d{1,2}:\d{2})(?::\d{2})?/)
  return matched ? matched[1] : (text || 'N/A')
}

const totalHistoryDuration = computed(() => formatDurationFromSamples(props.historyData.length))

const monitorChartRef = ref(null)
let monitorChartInstance = null
const trendWindowSize = 240
const trendWindowStart = ref(0)

const monitorSettings = computed(() => normalizeMonitorSettings(props.currentInfo?.monitorSettings))

const healthTone = computed(() => {
  const toneMap = {
    success: {
      chip: 'is-success',
      softText: 'monitor-health-copy is-success',
    },
    warning: {
      chip: 'is-warning',
      softText: 'monitor-health-copy is-warning',
    },
    error: {
      chip: 'is-error',
      softText: 'monitor-health-copy is-error',
    },
  }

  return toneMap[props.healthState.level] || toneMap.success
})

const healthStatusSymbol = computed(() => {
  if (props.healthState.level === 'error') return '×'
  if (props.healthState.level === 'warning') return '!'
  return '✓'
})

const enabledMetricLabels = computed(() => {
  const items = []
  if (monitorSettings.value.cpuEnabled) items.push('CPU 使用率')
  if (monitorSettings.value.memEnabled) items.push('内存使用率')
  if (monitorSettings.value.netRxEnabled) items.push('网卡接收速率')
  if (monitorSettings.value.netTxEnabled) items.push('网卡发送速率')
  if (monitorSettings.value.diskReadEnabled) items.push('磁盘读取速率')
  if (monitorSettings.value.diskWriteEnabled) items.push('磁盘写入速率')
  return items
})

const formatUsageValue = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? `${Number(parsed.toFixed(1))}%` : '--'
}

const formatRate = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return '--'

  if (parsed >= 1024 ** 3) return `${(parsed / 1024 ** 3).toFixed(1)} GB/s`
  if (parsed >= 1024 ** 2) return `${(parsed / 1024 ** 2).toFixed(1)} MB/s`
  if (parsed >= 1024) return `${(parsed / 1024).toFixed(1)} KB/s`
  return `${parsed.toFixed(1)} B/s`
}

const getMetricBadgeClass = value => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    return 'is-empty'
  }
  return 'is-ready'
}

const monitorMetricBadges = computed(() => {
  const badges = []

  if (monitorSettings.value.cpuEnabled) {
    badges.push({
      key: 'cpuUsage',
      label: 'CPU',
      value: formatUsageValue(props.currentInfo?.cpuUsage),
      badgeClass: getMetricBadgeClass(props.currentInfo?.cpuUsage),
    })
  }
  if (monitorSettings.value.memEnabled) {
    badges.push({
      key: 'memUsage',
      label: 'Mem',
      value: formatUsageValue(props.currentInfo?.memUsage),
      badgeClass: getMetricBadgeClass(props.currentInfo?.memUsage),
    })
  }
  if (monitorSettings.value.netRxEnabled) {
    badges.push({
      key: 'netRxBytesPerSec',
      label: 'RX',
      value: formatRate(props.currentInfo?.netRxBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.netRxBytesPerSec),
    })
  }
  if (monitorSettings.value.netTxEnabled) {
    badges.push({
      key: 'netTxBytesPerSec',
      label: 'TX',
      value: formatRate(props.currentInfo?.netTxBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.netTxBytesPerSec),
    })
  }
  if (monitorSettings.value.diskReadEnabled) {
    badges.push({
      key: 'diskReadBytesPerSec',
      label: 'Disk Read',
      value: formatRate(props.currentInfo?.diskReadBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.diskReadBytesPerSec),
    })
  }
  if (monitorSettings.value.diskWriteEnabled) {
    badges.push({
      key: 'diskWriteBytesPerSec',
      label: 'Disk Write',
      value: formatRate(props.currentInfo?.diskWriteBytesPerSec),
      badgeClass: getMetricBadgeClass(props.currentInfo?.diskWriteBytesPerSec),
    })
  }

  return badges
})

const datasetBlueprints = computed(() => {
  const blueprints = []

  if (monitorSettings.value.cpuEnabled) {
    blueprints.push({
      key: 'cpuUsage',
      label: 'CPU使用率 (%)',
      borderColor: '#19bfae',
      backgroundColor: 'rgba(25, 191, 174, 0.12)',
      yAxisID: 'usage',
      formatter: 'usage',
    })
  }
  if (monitorSettings.value.memEnabled) {
    blueprints.push({
      key: 'memUsage',
      label: '内存使用率 (%)',
      borderColor: '#69ad38',
      backgroundColor: 'rgba(105, 173, 56, 0.12)',
      yAxisID: 'usage',
      formatter: 'usage',
    })
  }
  if (monitorSettings.value.netRxEnabled) {
    blueprints.push({
      key: 'netRxBytesPerSec',
      label: '网卡接收速率',
      borderColor: '#6f91c8',
      backgroundColor: 'rgba(111, 145, 200, 0.1)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.netTxEnabled) {
    blueprints.push({
      key: 'netTxBytesPerSec',
      label: '网卡发送速率',
      borderColor: '#e6ab20',
      backgroundColor: 'rgba(230, 171, 32, 0.1)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.diskReadEnabled) {
    blueprints.push({
      key: 'diskReadBytesPerSec',
      label: '磁盘读取速率',
      borderColor: '#8b6e4b',
      backgroundColor: 'rgba(139, 110, 75, 0.1)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }
  if (monitorSettings.value.diskWriteEnabled) {
    blueprints.push({
      key: 'diskWriteBytesPerSec',
      label: '磁盘写入速率',
      borderColor: '#d95656',
      backgroundColor: 'rgba(217, 86, 86, 0.1)',
      yAxisID: 'rate',
      formatter: 'rate',
    })
  }

  return blueprints
})

const maxTrendWindowStart = computed(() => Math.max(props.historyData.length - trendWindowSize, 0))

const visibleHistoryData = computed(() => {
  const safeStart = Math.max(0, Math.min(Number(trendWindowStart.value) || 0, maxTrendWindowStart.value))
  return props.historyData.slice(safeStart, safeStart + trendWindowSize)
})

const showTrendSlider = computed(() => props.historyData.length > trendWindowSize)

const currentWindowTimeRange = computed(() => {
  if (!visibleHistoryData.value.length) {
    return 'N/A'
  }

  const startTime = formatTrendTimeLabel(visibleHistoryData.value[0]?.time)
  const endTime = formatTrendTimeLabel(visibleHistoryData.value[visibleHistoryData.value.length - 1]?.time)
  return `${startTime} - ${endTime}`
})


const chartDatasets = computed(() => datasetBlueprints.value
  .map(blueprint => ({
    ...blueprint,
    data: visibleHistoryData.value.map(item => {
      const parsed = Number(item?.[blueprint.key])
      return Number.isFinite(parsed) ? Number(parsed.toFixed(1)) : null
    }),
  }))
  .filter(dataset => dataset.data.some(value => value !== null)))

const hasChartData = computed(() => visibleHistoryData.value.length > 0 && chartDatasets.value.length > 0)

const emptyChartLabel = computed(() => {
  if (!enabledMetricLabels.value.length) return '当前未启用任何可绘制的监控项'
  return '暂无监控数据'
})

const getCompactAlertTitle = info => {
  const component = String(info?.component || '未命名组件').trim()
  const level = normalizeRiskLevel(info?.riskLevel)
  return `${component} · ${level}`
}

const getCompactAlertSummary = value => {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '暂无摘要'
  return text.length > 34 ? `${text.slice(0, 34)}...` : text
}

const getAlertChipClass = level => {
  const normalized = normalizeRiskLevel(level)

  if (normalized === '高') return 'border-red-200/30 bg-red-400/10 text-ui-error'
  if (normalized === '中') return 'border-amber-200/30 bg-amber-400/10 text-ui-warning'
  if (normalized === '低') return 'border-sky-200/30 bg-sky-400/10 text-brand'
  return 'border-white/22 bg-white/10 text-ui-subtext'
}

const getAlertDotClass = level => {
  const normalized = normalizeRiskLevel(level)

  if (normalized === '高') return 'bg-ui-error'
  if (normalized === '中') return 'bg-ui-warning'
  if (normalized === '低') return 'bg-brand'
  return 'bg-white/50'
}

const getAlertSymbol = level => {
  const normalized = normalizeRiskLevel(level)
  if (normalized === '高') return '×'
  if (normalized === '中') return '!'
  if (normalized === '低') return 'i'
  return '✓'
}

const destroyChart = () => {
  if (monitorChartInstance) {
    monitorChartInstance.destroy()
    monitorChartInstance = null
  }
}

const resolveXAxisLabelStep = labels => {
  const total = Array.isArray(labels) ? labels.length : 0
  if (total <= 8) return 1
  if (total <= 16) return 2
  if (total <= 24) return 3
  return Math.ceil(total / 8)
}

const buildMonitorChartConfig = labels => {
  const hasUsageAxis = chartDatasets.value.some(dataset => dataset.yAxisID === 'usage')
  const hasRateAxis = chartDatasets.value.some(dataset => dataset.yAxisID === 'rate')
  const xAxisLabelStep = resolveXAxisLabelStep(labels)
  const denseData = labels.length > 18

  return {
    type: 'line',
    data: {
      labels,
      datasets: chartDatasets.value.map(dataset => ({
        label: dataset.label,
        data: dataset.data,
        borderColor: dataset.borderColor,
        backgroundColor: dataset.backgroundColor,
        fill: false,
        tension: 0.35,
        borderWidth: 2,
        pointRadius: denseData ? 0 : 1.5,
        pointHoverRadius: 4,
        pointHitRadius: 10,
        yAxisID: dataset.yAxisID,
        metricFormatter: dataset.formatter,
      })),
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      //数据图刷新动画
      animation: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: '#725d42',
            usePointStyle: true,
            boxWidth: 8,
          },
        },
        tooltip: {
          backgroundColor: 'rgba(43, 33, 24, 0.94)',
          borderColor: 'rgba(221, 210, 185, 0.28)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            label(context) {
              const formatter = context.dataset.metricFormatter
              if (formatter === 'rate') {
                return `${context.dataset.label}: ${formatRate(context.parsed.y)}`
              }
              return `${context.dataset.label}: ${Number(context.parsed.y || 0).toFixed(1)}%`
            },
          },
        },
      },
      scales: {
        usage: {
          display: hasUsageAxis,
          beginAtZero: true,
          max: 100,
          grid: { color: 'rgba(221, 210, 185, 0.42)' },
          ticks: {
            color: '#8f806d',
            callback(value) {
              return `${value}%`
            },
          },
          title: {
            display: hasUsageAxis,
            text: '使用率 (%)',
            color: '#8f806d',
          },
        },
        rate: {
          display: hasRateAxis,
          position: 'right',
          beginAtZero: true,
          grid: { drawOnChartArea: false },
          ticks: {
            color: '#8f806d',
            callback(value) {
              return formatRate(Number(value))
            },
          },
          title: {
            display: hasRateAxis,
            text: '速率',
            color: '#8f806d',
          },
        },
        x: {
          grid: { display: false },
          ticks: {
            color: '#8f806d',
            autoSkip: false,
            maxRotation: 0,
            minRotation: 0,
            padding: 6,
            callback(value, index) {
              if (index === 0 || index === labels.length - 1 || index % xAxisLabelStep === 0) {
                return labels[index]
              }
              return ''
            },
          },
        },
      },
    },
  }
}

const renderMonitorChart = async () => {
  await nextTick()

  if (!monitorChartRef.value || !hasChartData.value) {
    destroyChart()
    return
  }

  const labels = visibleHistoryData.value.map(item => item.time)
  const config = buildMonitorChartConfig(labels)

  if (monitorChartInstance) {
    monitorChartInstance.data = config.data
    monitorChartInstance.options = config.options
    monitorChartInstance.update()
    return
  }

  monitorChartInstance = new Chart(monitorChartRef.value, config)
}

const handleTrendSliderWheel = event => {
  if (!showTrendSlider.value) return

  const direction = Number(event?.deltaY) || 0
  if (!direction) return

  const currentStart = Number(trendWindowStart.value) || 0
  //步长控制
  const nextStart = direction < 0 ? currentStart - 20 : currentStart + 20
  trendWindowStart.value = Math.max(0, Math.min(nextStart, maxTrendWindowStart.value))
}

const handleResize = () => {
  if (monitorChartInstance) {
    monitorChartInstance.resize()
  }
}

watch(
  () => props.historyData.length,
  (nextLength, previousLength) => {
    const previousMax = Math.max(previousLength - trendWindowSize, 0)
    const nextMax = Math.max(nextLength - trendWindowSize, 0)
    const currentStart = Number(trendWindowStart.value) || 0
    const pinnedToLatest = currentStart >= previousMax

    if (pinnedToLatest) {
      trendWindowStart.value = nextMax
      return
    }

    trendWindowStart.value = Math.max(0, Math.min(currentStart, nextMax))
  },
  { immediate: true }
)

watch(trendWindowStart, value => {
  const safeValue = Math.max(0, Math.min(Number(value) || 0, maxTrendWindowStart.value))
  if (safeValue !== value) {
    trendWindowStart.value = safeValue
  }
})

watch(() => props.historyData, renderMonitorChart, { deep: true })
watch(monitorSettings, renderMonitorChart, { deep: true })
watch(visibleHistoryData, renderMonitorChart, { deep: true })

onMounted(() => {
  renderMonitorChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  destroyChart()
})
</script>

<style scoped>
.monitor-detail-layout {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(0, 2fr) minmax(290px, 0.82fr);
  align-items: stretch;
  gap: 16px;
}

.monitor-detail-panel {
  min-width: 0;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 20px;
  background: var(--app-panel-strong, #fffef9);
  box-shadow: var(--app-shadow-soft, 0 10px 26px -24px rgba(79, 59, 43, 0.3));
}

.monitor-trend-panel {
  display: flex;
  min-height: 590px;
  flex-direction: column;
  padding: clamp(16px, 2vw, 22px);
}

.monitor-panel-header,
.monitor-side-header,
.monitor-chart-header,
.monitor-trend-control > div,
.monitor-alert-title,
.monitor-alert-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.monitor-panel-header {
  align-items: flex-start;
}

.monitor-panel-header h3,
.monitor-side-header h3 {
  margin: 2px 0 0;
  color: var(--app-text, #4f3b2b);
  font-size: 18px;
  font-weight: 800;
}

.monitor-panel-eyebrow {
  margin: 0;
  color: var(--app-primary-active, #109b8f);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.15em;
  text-transform: uppercase;
}

.monitor-health-copy {
  max-width: 720px;
  margin: 5px 0 0;
  color: var(--app-text-body, #725d42);
  font-size: 13px;
  line-height: 1.55;
}

.monitor-health-copy.is-success {
  color: #568a36;
}

.monitor-health-copy.is-warning {
  color: #9b700d;
}

.monitor-health-copy.is-error {
  color: var(--app-danger, #d95656);
}

.monitor-health-badge {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.monitor-health-badge.is-success {
  color: #568a36;
  background: #edf6e6;
}

.monitor-health-badge.is-warning {
  color: #9b700d;
  background: #fff2cd;
}

.monitor-health-badge.is-error {
  color: var(--app-danger, #d95656);
  background: #fce9e7;
}

.monitor-health-badge__icon {
  display: inline-flex;
  width: 17px;
  height: 17px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: currentColor;
  color: white;
  font-size: 11px;
  line-height: 1;
}

.monitor-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin-top: 16px;
}

.monitor-metric-card {
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 14px;
  background: #fbf8ed;
}

.monitor-metric-card span,
.monitor-metric-card strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-metric-card span {
  color: var(--app-text-muted, #8f806d);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.monitor-metric-card strong {
  margin-top: 4px;
  color: var(--app-text, #4f3b2b);
  font-size: 14px;
}

.monitor-metric-card.is-empty strong {
  color: var(--app-text-faint, #aa9c87);
}

.monitor-metric-empty {
  grid-column: 1 / -1;
  padding: 13px;
  border: 1px dashed var(--app-border, #ddd2b9);
  border-radius: 14px;
  color: var(--app-text-muted, #8f806d);
  font-size: 12px;
  text-align: center;
}

.monitor-chart-card {
  display: flex;
  min-height: 390px;
  flex: 1 1 auto;
  flex-direction: column;
  margin-top: 12px;
  padding: 14px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 16px;
  background: #fbf8ed;
}

.monitor-chart-header {
  flex: 0 0 auto;
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
}

.monitor-chart-header > div {
  display: flex;
  align-items: baseline;
  gap: 9px;
}

.monitor-chart-header strong {
  color: var(--app-text, #4f3b2b);
  font-size: 13px;
}

.monitor-chart-frame {
  position: relative;
  min-width: 0;
  min-height: 300px;
  flex: 1 1 auto;
  margin-top: 10px;
}

.monitor-chart-frame canvas {
  width: 100%;
  height: 100%;
}

.monitor-chart-empty {
  display: flex;
  height: 100%;
  min-height: 280px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: var(--app-text-muted, #8f806d);
  font-size: 13px;
}

.monitor-chart-empty__icon {
  display: inline-flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: var(--app-panel-soft, #f3eddb);
  color: var(--app-info, #6f91c8);
}

.monitor-chart-empty__icon svg {
  width: 22px;
  height: 22px;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.monitor-trend-control {
  margin-top: 10px;
  padding: 10px 12px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 13px;
  background: var(--app-panel, #fffdf6);
}

.monitor-trend-control > div {
  margin-bottom: 7px;
  color: var(--app-text-muted, #8f806d);
  font-size: 10px;
}

.trend-slider {
  width: 100%;
  height: 6px;
  appearance: none;
  border-radius: 999px;
  outline: none;
  background: #d8d0ba;
}

.trend-slider:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--app-focus, #e8b62e) 60%, transparent);
  outline-offset: 4px;
}

.trend-slider::-webkit-slider-thumb {
  width: 17px;
  height: 17px;
  appearance: none;
  border: 3px solid var(--app-panel, #fffdf6);
  border-radius: 50%;
  background: var(--app-primary, #19bfae);
  box-shadow: 0 2px 6px rgba(79, 59, 43, 0.22);
  cursor: ew-resize;
}

.trend-slider::-moz-range-thumb {
  width: 13px;
  height: 13px;
  border: 3px solid var(--app-panel, #fffdf6);
  border-radius: 50%;
  background: var(--app-primary, #19bfae);
  box-shadow: 0 2px 6px rgba(79, 59, 43, 0.22);
  cursor: ew-resize;
}

.monitor-side-column {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.monitor-system-panel,
.monitor-alert-panel {
  padding: 18px;
}

.monitor-system-panel {
  flex: 0 0 auto;
}

.monitor-alert-panel {
  display: flex;
  min-height: 330px;
  flex: 1 1 auto;
  flex-direction: column;
}

.monitor-side-header {
  align-items: flex-start;
}

.monitor-side-header > span {
  max-width: 160px;
  overflow: hidden;
  color: var(--app-text-muted, #8f806d);
  font-family: ui-monospace, SFMono-Regular, Consolas, "Liberation Mono", monospace;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-side-header a {
  color: var(--app-primary-active, #109b8f);
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
}

.monitor-side-header a:hover {
  text-decoration: underline;
}

.monitor-system-list {
  display: grid;
  gap: 0;
  margin: 13px 0 0;
}

.monitor-system-list > div {
  display: grid;
  grid-template-columns: minmax(90px, 0.75fr) minmax(0, 1.25fr);
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid var(--app-border-soft, #e9e1cf);
}

.monitor-system-list dt {
  color: var(--app-text-muted, #8f806d);
  font-size: 11px;
}

.monitor-system-list dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--app-text, #4f3b2b);
  font-size: 11px;
  font-weight: 700;
  line-height: 1.45;
  text-align: right;
}

.monitor-alert-list {
  display: flex;
  max-height: 420px;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 8px;
  margin-top: 13px;
  overflow-y: auto;
  padding-right: 2px;
}

.monitor-alert-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px;
  border: 1px solid var(--app-border-soft, #e9e1cf);
  border-radius: 14px;
  color: inherit;
  text-decoration: none;
  transition:
    transform var(--app-motion-fast, 160ms) var(--app-ease, ease),
    border-color var(--app-motion-fast, 160ms) var(--app-ease, ease);
}

.monitor-alert-item:hover {
  transform: translateY(-1px);
  border-color: var(--app-border, #ddd2b9);
}

.monitor-alert-item:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--app-focus, #e8b62e) 65%, transparent);
  outline-offset: 2px;
}

.monitor-alert-symbol {
  display: inline-flex;
  width: 22px;
  height: 22px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: white;
  font-size: 11px;
  font-weight: 900;
}

.monitor-alert-copy {
  display: block;
  min-width: 0;
  flex: 1 1 auto;
}

.monitor-alert-title strong {
  overflow: hidden;
  color: var(--app-text, #4f3b2b);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-alert-title > span {
  flex: 0 0 auto;
  padding: 2px 6px;
  border: 1px solid currentColor;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
}

.monitor-alert-summary {
  display: block;
  margin-top: 4px;
  overflow: hidden;
  color: var(--app-text-body, #725d42);
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-alert-meta {
  margin-top: 5px;
  color: var(--app-text-muted, #8f806d);
  font-size: 9px;
}

.monitor-alert-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.monitor-side-empty {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  padding: 20px;
  border: 1px dashed var(--app-border, #ddd2b9);
  border-radius: 14px;
  background: #fbf8ed;
  color: var(--app-text-muted, #8f806d);
  font-size: 12px;
  text-align: center;
}

.custom-scrollbar {
  scrollbar-color: #cfc5ad transparent;
  scrollbar-width: thin;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #cfc5ad;
}

@media (max-width: 1100px) {
  .monitor-detail-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .monitor-side-column {
    display: grid;
    grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  }

  .monitor-alert-list {
    max-height: 350px;
  }
}

@media (max-width: 767px) {
  .monitor-detail-layout,
  .monitor-side-column {
    display: flex;
    flex-direction: column;
  }

  .monitor-trend-panel {
    min-height: 0;
    padding: 14px;
  }

  .monitor-panel-header {
    flex-direction: column;
  }

  .monitor-health-badge {
    align-self: flex-start;
  }

  .monitor-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .monitor-chart-card {
    min-height: 360px;
    padding: 11px;
  }

  .monitor-chart-frame {
    min-height: 260px;
  }

  .monitor-chart-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .monitor-trend-control > div span:last-child {
    display: none;
  }

  .monitor-system-panel,
  .monitor-alert-panel {
    padding: 15px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .monitor-alert-item {
    transition: none;
  }

  .monitor-alert-item:hover {
    transform: none;
  }
}
</style>







