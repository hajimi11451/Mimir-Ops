<template>
  <div class="space-y-2">
    <div class="text-xs text-ui-subtext">{{ title }}</div>
    <div class="rounded-[18px] border border-white/22 bg-white/14 p-2 shadow-[0_18px_34px_-28px_rgba(15,23,42,0.22)] backdrop-blur-xl">
      <div ref="chartRef" class="h-[260px] w-full"></div>
    </div>
    <div v-if="template === 'health_overview'" class="grid grid-cols-2 gap-2 text-xs">
      <div class="rounded-[14px] border border-white/20 bg-white/12 p-2 backdrop-blur-xl">CPU 平均: {{ summary.avgCpu ?? 0 }}%</div>
      <div class="rounded-[14px] border border-white/20 bg-white/12 p-2 backdrop-blur-xl">CPU 峰值: {{ summary.maxCpu ?? 0 }}%</div>
      <div class="rounded-[14px] border border-white/20 bg-white/12 p-2 backdrop-blur-xl">内存平均: {{ summary.avgMem ?? 0 }}%</div>
      <div class="rounded-[14px] border border-white/20 bg-white/12 p-2 backdrop-blur-xl">内存峰值: {{ summary.maxMem ?? 0 }}%</div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: { type: String, default: '服务器监控图表' },
  chartData: { type: Object, default: () => ({}) },
})

const template = computed(() => props.chartData?.template || 'health_overview')
const summary = computed(() => props.chartData?.summary || {})
const option = computed(() => props.chartData?.option || {})

const chartRef = ref(null)
let chartInstance = null

const initChart = () => {
  if (!chartRef.value) return null
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  return chartInstance
}

const renderChart = async () => {
  await nextTick()
  const instance = initChart()
  if (!instance) return
  instance.setOption(option.value, true)
  instance.resize()
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

watch(() => props.chartData, renderChart, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>
