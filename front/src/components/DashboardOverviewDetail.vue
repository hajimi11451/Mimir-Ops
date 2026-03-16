<template>
  <div class="h-full min-h-0 flex flex-col overflow-hidden">
    <div class="flex flex-1 min-h-0 flex-col gap-4 xl:flex-row">
      <el-card
        class="glass-card flex-1 min-h-0 rounded-[30px]"
        :body-style="{ padding: '24px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="mb-4 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="glass-chip px-3 py-1 text-xs font-medium" :class="healthTone.chip">
                  健康度 {{ healthState.score }}%
                </span>
                <span class="glass-chip px-3 py-1 text-xs font-medium border-white/24 bg-white/12 text-ui-text">
                  告警 {{ healthState.activeAlertCount }}
                </span>
                <span class="glass-chip px-3 py-1 text-xs font-medium border-white/24 bg-white/12 text-ui-text">
                  日志 {{ healthState.totalLogsCount }}
                </span>
                <span class="glass-chip px-3 py-1 text-xs font-medium border-white/24 bg-white/12 text-ui-text">
                  服务器 {{ serverList.length }}
                </span>
              </div>

              <h3 class="mt-4 text-lg font-bold text-ui-text">监控趋势</h3>
              <p class="mt-1 text-sm" :class="healthTone.softText">
                {{ healthState.description }}
              </p>
            </div>

            <div class="flex flex-wrap gap-2 lg:max-w-[260px] lg:justify-end">
              <span class="glass-chip px-2.5 py-1 text-xs font-medium" :class="getUsageBadgeClass(currentInfo.cpuUsage)">
                CPU: {{ toUsageValue(currentInfo.cpuUsage) }}%
              </span>
              <span class="glass-chip px-2.5 py-1 text-xs font-medium" :class="getUsageBadgeClass(currentInfo.memUsage)">
                Mem: {{ toUsageValue(currentInfo.memUsage) }}%
              </span>
            </div>
          </div>

          <div class="glass-subcard mb-4 grid grid-cols-1 gap-4 p-4 text-sm text-ui-subtext md:grid-cols-2">
            <div><span class="font-bold">OS:</span> {{ currentInfo.os || 'N/A' }}</div>
            <div><span class="font-bold">运行:</span> {{ currentInfo.upTime || 'N/A' }}</div>
            <div><span class="font-bold">CPU:</span> {{ currentInfo.processor || 'N/A' }}</div>
            <div><span class="font-bold">内存:</span> {{ currentInfo.availableMemory || 'N/A' }} / {{ currentInfo.totalMemory || 'N/A' }}</div>
          </div>

          <div class="glass-subcard flex-1 min-h-0 overflow-hidden p-3 lg:p-4">
            <div class="relative h-full min-h-[320px] w-full">
              <canvas v-if="hasChartData" ref="monitorChartRef" class="h-full w-full"></canvas>
              <div v-else class="flex h-full items-center justify-center text-sm text-ui-subtext">
                {{ loadingMonitor ? '加载监控数据...' : '暂无监控数据' }}
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card
        class="glass-card w-full min-h-0 rounded-[30px] xl:basis-[32%] xl:max-w-[32%]"
        :body-style="{ padding: '24px', height: '100%' }"
      >
        <div class="flex h-full min-h-0 flex-col">
          <div class="mb-4 flex items-center justify-between gap-3">
            <div class="min-w-0">
              <h3 class="text-lg font-bold text-ui-text">最新告警</h3>
              <p class="mt-1 text-xs text-ui-subtext">当前服务器相关告警与诊断记录</p>
            </div>
            <router-link to="/info-list" class="text-sm text-brand hover:underline">
              全部
            </router-link>
          </div>

          <div class="glass-subcard flex-1 min-h-0 overflow-hidden p-3">
            <div class="h-full min-h-0 space-y-2 overflow-y-auto pr-1 custom-scrollbar">
              <div v-if="loadingInfo" class="flex h-full items-center justify-center text-ui-subtext">加载中</div>

              <div v-else-if="infoList.length === 0" class="flex h-full items-center justify-center text-ui-subtext">暂无记录</div>

              <div
                v-else
                v-for="(info, index) in infoList"
                :key="info.id || index"
                class="glass-soft flex items-start gap-2.5 px-3 py-2.5"
                :class="getAlertClass(info.riskLevel)"
              >
                <span class="mt-1 inline-flex h-2 w-2 shrink-0 rounded-full" :class="getAlertDotClass(info.riskLevel)"></span>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center justify-between gap-3">
                    <h4 class="truncate text-[13px] font-semibold text-ui-text">
                      {{ getCompactAlertTitle(info) }}
                    </h4>
                    <span class="glass-chip shrink-0 px-2 py-0.5 text-[11px] font-medium" :class="getAlertChipClass(info.riskLevel)">
                      {{ normalizeRiskLevel(info.riskLevel) }}
                    </span>
                  </div>
                  <p class="mt-1 truncate text-xs leading-5 text-ui-subtext">
                    {{ getCompactAlertSummary(info.errorSummary) }}
                  </p>
                  <div class="mt-1 flex items-center justify-between gap-2 text-[11px] text-ui-subtext/75">
                    <span class="truncate">服务器: {{ info.serverIp || '未知' }}</span>
                    <span class="shrink-0">{{ formatDate(info.createdAt) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Chart from 'chart.js/auto'
import { formatDate, getAlertClass, normalizeRiskLevel } from '../utils/dashboardHealth'

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

const monitorChartRef = ref(null)
let monitorChartInstance = null

const healthTone = computed(() => {
  const toneMap = {
    success: {
      chip: 'border-emerald-200/30 bg-emerald-400/10 text-ui-success',
      softText: 'text-green-600',
    },
    warning: {
      chip: 'border-amber-200/30 bg-amber-400/10 text-ui-warning',
      softText: 'text-orange-600',
    },
    error: {
      chip: 'border-red-200/30 bg-red-400/10 text-ui-error',
      softText: 'text-red-600',
    },
  }

  return toneMap[props.healthState.level] || toneMap.success
})

const hasChartData = computed(() => props.historyData.length > 0)

const toUsageValue = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Number(parsed.toFixed(1)) : 0
}

const getUsageBadgeClass = value => {
  if (Number.isFinite(toUsageValue(value))) {
    return 'border-white/24 bg-white/12 text-ui-text'
  }

  return 'border-white/24 bg-white/12 text-ui-text'
}

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

const destroyChart = () => {
  if (monitorChartInstance) {
    monitorChartInstance.destroy()
    monitorChartInstance = null
  }
}

const renderMonitorChart = async () => {
  await nextTick()

  if (!monitorChartRef.value || !props.historyData.length) {
    destroyChart()
    return
  }

  const labels = props.historyData.map(item => item.time)
  const cpuData = props.historyData.map(item => item.cpuUsage)
  const memData = props.historyData.map(item => item.memUsage)

  if (monitorChartInstance) {
    monitorChartInstance.data.labels = labels
    monitorChartInstance.data.datasets[0].data = cpuData
    monitorChartInstance.data.datasets[1].data = memData
    monitorChartInstance.update()
    return
  }

  monitorChartInstance = new Chart(monitorChartRef.value, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'CPU使用率 (%)',
          data: cpuData,
          borderColor: '#3182ce',
          backgroundColor: 'rgba(49, 130, 206, 0.12)',
          fill: true,
          tension: 0.35,
          yAxisID: 'y',
        },
        {
          label: '内存使用率 (%)',
          data: memData,
          borderColor: '#38a169',
          backgroundColor: 'rgba(56, 161, 105, 0.12)',
          fill: true,
          tension: 0.35,
          yAxisID: 'y',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: {
          position: 'top',
          labels: {
            color: '#5f6f87',
            usePointStyle: true,
            boxWidth: 8,
          },
        },
        tooltip: {
          backgroundColor: 'rgba(15, 23, 42, 0.82)',
          borderColor: 'rgba(255, 255, 255, 0.12)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            label(context) {
              return `${context.dataset.label}: ${context.parsed.y.toFixed(1)}%`
            },
          },
        },
      },
      scales: {
        y: {
          beginAtZero: true,
          max: 100,
          grid: { color: 'rgba(148, 163, 184, 0.16)' },
          ticks: { color: '#5f6f87' },
          title: {
            display: true,
            text: '使用率 (%)',
            color: '#5f6f87',
          },
        },
        x: {
          grid: { display: false },
          ticks: {
            color: '#5f6f87',
            maxTicksLimit: 10,
          },
        },
      },
    },
  })
}

const handleResize = () => {
  if (monitorChartInstance) {
    monitorChartInstance.resize()
  }
}

watch(() => props.historyData, renderMonitorChart, { deep: true })

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
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e0;
  border-radius: 3px;
}
</style>
