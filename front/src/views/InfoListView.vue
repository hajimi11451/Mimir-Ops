<template>
  <div class="app-page alert-page">
    <section class="page-hero alert-hero">
      <div>
        <div class="section-eyebrow">Alert center</div>
        <h2>告警中心</h2>
        <p>集中查看诊断结果、风险等级和建议方案，并将需要处理的问题交给灵枢助手。</p>
      </div>
      <div class="hero-actions">
        <span class="status-pill" :class="overallTotal ? 'status-pill--warning' : 'status-pill--success'">{{ recordSummaryText }}</span>
        <el-button @click="goDashboard">回到总览</el-button>
        <el-button type="primary" :loading="loading || configLoading" @click="refreshPage">刷新告警</el-button>
      </div>
    </section>

    <section class="alert-summary-grid">
      <div class="summary-card summary-card--danger"><span>高风险</span><strong>{{ riskCount.high }}</strong><small>需要优先处理</small></div>
      <div class="summary-card summary-card--warning"><span>中风险</span><strong>{{ riskCount.medium }}</strong><small>建议持续关注</small></div>
      <div class="summary-card summary-card--info"><span>低风险</span><strong>{{ riskCount.low }}</strong><small>可计划处理</small></div>
      <div class="summary-card summary-card--safe"><span>全部记录</span><strong>{{ overallTotal }}</strong><small>历史诊断结果</small></div>
    </section>

    <section class="section-card filter-card">
      <div class="filter-grid">
        <el-select v-model="filters.serverIp" clearable filterable :loading="configLoading" placeholder="全部服务器" no-data-text="暂无可选服务器">
          <el-option v-for="serverIp in serverIpOptions" :key="serverIp" :label="serverIp" :value="serverIp" />
        </el-select>
        <el-select v-model="filters.riskLevel" clearable placeholder="全部风险等级">
          <el-option label="高风险" value="高" /><el-option label="中风险" value="中" /><el-option label="低风险" value="低" /><el-option label="无风险" value="无" />
        </el-select>
        <el-input v-model="filters.keyword" clearable placeholder="搜索组件、摘要或建议" />
        <el-button :disabled="!hasActiveFilters" @click="resetFilters">重置筛选</el-button>
      </div>
    </section>

    <section class="section-card alert-list-card">
      <div class="section-header">
        <div>
          <div class="section-eyebrow">Diagnosis results</div>
          <h3>告警记录</h3>
          <p>{{ recordSummaryText }}，点击记录进入完整分析与处置建议。</p>
        </div>
        <el-button
          v-if="filters.serverIp"
          plain
          type="danger"
          :loading="deletingServerIp"
          :disabled="deletingServerIp || loading || clearing"
          @click="handleDeleteServerIp"
        >
          清理当前服务器记录
        </el-button>
      </div>

      <div class="alert-table-wrap">
        <el-table
          :data="paginatedList"
          style="width: 100%"
          v-loading="loading"
          row-class-name="clickable-row"
          @row-click="goDetail"
        >
          <el-table-column prop="createdAt" label="时间" min-width="175">
            <template #default="{ row }">
              <div class="time-cell"><span class="time-dot"></span>{{ formatDate(row.createdAt) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="serverIp" label="服务器 IP" min-width="155">
            <template #default="{ row }"><span class="mono-text">{{ row.serverIp || '-' }}</span></template>
          </el-table-column>
          <el-table-column prop="component" label="组件" min-width="120" />
          <el-table-column prop="riskLevel" label="风险等级" min-width="120">
            <template #default="{ row }">
              <el-tag :type="getTagType(row.riskLevel)" effect="light" size="small">
                {{ formatRiskLevel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="errorSummary" label="问题摘要" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="alert-summary-cell">{{ compactAlertText(row.errorSummary, 42) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="suggestedActions" label="建议摘要" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="suggestion-cell">{{ compactAlertText(formatSuggestedActions(row.suggestedActions), 42) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="goDetail(row)">分析与处置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="total > 0" class="table-footer">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :pager-count="5"
          layout="total, prev, pager, next"
          background
        />
      </div>
      <div v-else-if="!loading" class="empty-state">
        <div class="empty-state__icon">✓</div><h4>当前没有匹配告警</h4><p>系统将持续诊断已配置的组件，新告警会自动出现在这里。</p>
      </div>
    </section>

    <section v-if="overallTotal" class="maintenance-row">
      <span>数据维护</span>
      <p>清空操作不可恢复，仅在确认不再需要历史诊断时使用。</p>
      <el-button type="danger" link :loading="clearing" :disabled="loading || clearing" @click="handleClearAll">清空全部告警记录</el-button>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectAllInfo, deleteAllInfo, deleteInfoByServerIp } from '../api/info'
import { listConfigs } from '../api/diagnosis'

const router = useRouter()
const infoList = ref([])
const loading = ref(false)
const clearing = ref(false)
const deletingServerIp = ref(false)
const configLoading = ref(false)
const pageSize = 10
const currentPage = ref(1)
const componentConfigs = ref([])
const filters = ref({
  serverIp: '',
  riskLevel: '',
  keyword: '',
})

const sortedList = computed(() => {
  const list = [...infoList.value]
  return list.sort((a, b) => parseDateToTime(b.createdAt) - parseDateToTime(a.createdAt))
})

const serverIpOptions = computed(() => {
  const uniqueServerIps = new Set()

  componentConfigs.value.forEach(item => {
    const serverIp = String(item?.serverIp || '').trim()
    if (serverIp) {
      uniqueServerIps.add(serverIp)
    }
  })

  return Array.from(uniqueServerIps).sort((a, b) => a.localeCompare(b))
})

const filteredList = computed(() => {
  const selectedServerIp = String(filters.value.serverIp || '').trim()
  const selectedRiskLevel = String(filters.value.riskLevel || '').trim()
  const keyword = String(filters.value.keyword || '').trim().toLowerCase()

  return sortedList.value.filter(item => {
    const matchesServer = !selectedServerIp || String(item?.serverIp || '').trim() === selectedServerIp
    const matchesRisk = !selectedRiskLevel || formatRiskLevel(item?.riskLevel) === selectedRiskLevel
    const haystack = [item?.serverIp, item?.component, item?.errorSummary, item?.analysisResult, formatSuggestedActions(item?.suggestedActions)].join(' ').toLowerCase()
    const matchesKeyword = !keyword || haystack.includes(keyword)
    return matchesServer && matchesRisk && matchesKeyword
  })
})

const total = computed(() => filteredList.value.length)

const overallTotal = computed(() => sortedList.value.length)
const hasActiveFilters = computed(() => Boolean(filters.value.serverIp || filters.value.riskLevel || filters.value.keyword))
const riskCount = computed(() => sortedList.value.reduce((acc, item) => {
  const level = formatRiskLevel(item?.riskLevel)
  if (level === '高') acc.high += 1
  else if (level === '中') acc.medium += 1
  else if (level === '低') acc.low += 1
  return acc
}, { high: 0, medium: 0, low: 0 }))

const recordSummaryText = computed(() => (
  hasActiveFilters.value
    ? `筛选后 ${total.value} / ${overallTotal.value} 条记录`
    : `共 ${overallTotal.value} 条记录`
))

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

function parseDateToTime(value) {
  if (value == null) return 0
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).getTime()
  }
  return new Date(value).getTime()
}

function formatDate(value) {
  if (value == null) return ''
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).toLocaleString()
  }
  return new Date(value).toLocaleString()
}

function getTagType(level) {
  const normalizedLevel = formatRiskLevel(level)
  if (normalizedLevel === '高') return 'danger'
  if (normalizedLevel === '中') return 'warning'
  if (normalizedLevel === '低') return 'info'
  return ''
}

function formatRiskLevel(level) {
  const value = String(level || '').trim()
  if (['高', '中', '低', '无'].includes(value)) return value
  const lowered = value.toLowerCase()
  if (lowered.includes('high') || lowered.includes('critical') || lowered.includes('error')) return '高'
  if (lowered.includes('medium') || lowered.includes('warning') || lowered.includes('warn')) return '中'
  if (lowered.includes('low') || lowered.includes('info')) return '低'
  return '无'
}

function formatSuggestedActions(value) {
  const text = String(value || '').trim()
  if (!text || text === '[]') return '-'
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed) && parsed.length > 0) {
      return parsed.join('；')
    }
  } catch {
  }
  return text.replace(/[\r\n]+/g, '；')
}

function compactAlertText(value, maxLength = 32) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  if (!text) return '-'
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

async function fetchAllInfo() {
  loading.value = true
  try {
    const res = await selectAllInfo()
    if (Array.isArray(res)) {
      infoList.value = res
    } else if (res && Array.isArray(res.data)) {
      infoList.value = res.data
    } else {
      infoList.value = []
    }
    currentPage.value = 1
  } catch (e) {
    console.error('Failed to fetch all info', e)
  } finally {
    loading.value = false
  }
}

async function fetchServerIpOptions() {
  configLoading.value = true
  try {
    const res = await listConfigs()
    if (Array.isArray(res)) {
      componentConfigs.value = res
    } else if (res && Array.isArray(res.data)) {
      componentConfigs.value = res.data
    } else {
      componentConfigs.value = []
    }
  } catch (error) {
    componentConfigs.value = []
    ElMessage.error(error?.message || '获取服务器 IP 筛选项失败')
  } finally {
    configLoading.value = false
  }
}

async function refreshPage() {
  await Promise.allSettled([
    fetchAllInfo(),
    fetchServerIpOptions(),
  ])
}

function resetFilters() {
  filters.value.serverIp = ''
  filters.value.riskLevel = ''
  filters.value.keyword = ''
  currentPage.value = 1
}

function goDashboard() {
  router.push('/dashboard')
}

function goDetail(row) {
  if (!row?.id) {
    ElMessage.warning('当前记录缺少详情 ID')
    return
  }
  router.push({ name: 'info-detail', params: { id: row.id } })
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm(
      '该操作将删除当前登录用户的全部历史告警与信息记录，是否继续？',
      '确认删除',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
        modalClass: 'keep-bright-overlay',
      }
    )
  } catch {
    return
  }

  clearing.value = true
  try {
    const deleted = await deleteAllInfo()
    ElMessage.success(`删除完成，共删除 ${deleted || 0} 条记录`)
    await fetchAllInfo()
  } catch (e) {
    console.error('Failed to delete all info', e)
    ElMessage.error(e?.message || '删除失败')
  } finally {
    clearing.value = false
  }
}

async function handleDeleteServerIp() {
  const selectedServerIp = String(filters.value.serverIp || '').trim()
  if (!selectedServerIp) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `该操作将删除服务器 ${selectedServerIp} 的全部告警记录，是否继续？`,
      '确认删除',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
        modalClass: 'keep-bright-overlay',
      }
    )
  } catch {
    return
  }

  deletingServerIp.value = true
  try {
    const deleted = await deleteInfoByServerIp(selectedServerIp)
    ElMessage.success(`已删除 ${selectedServerIp} 的 ${deleted || 0} 条告警记录`)
    await fetchAllInfo()
  } catch (error) {
    console.error('Failed to delete info by server ip', error)
    ElMessage.error(error?.message || '按服务器 IP 删除告警失败')
  } finally {
    deletingServerIp.value = false
  }
}

watch(
  [() => filters.value.serverIp, () => filters.value.riskLevel, () => filters.value.keyword],
  () => {
    currentPage.value = 1
  }
)

watch(serverIpOptions, options => {
  if (filters.value.serverIp && !options.includes(filters.value.serverIp)) {
    filters.value.serverIp = ''
  }
})

onMounted(() => {
  refreshPage()
})
</script>

<style scoped>
.alert-page { display: grid; gap: 1.25rem; }.alert-hero { background: linear-gradient(135deg, #fffdf6 0%, #fff1dc 100%); }.hero-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .7rem; }
.alert-summary-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 1rem; }.summary-card { display: grid; gap: .3rem; padding: 1rem 1.15rem; border: 1px solid var(--color-ui-border); border-radius: 20px; }.summary-card--danger { background:#fdebea; }.summary-card--warning{background:#fff4d6}.summary-card--info{background:#edf3fb}.summary-card--safe{background:#edf7e7}.summary-card span{color:var(--color-ui-subtext);font-size:.73rem;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.summary-card strong{color:var(--color-ui-text);font-size:1.7rem}.summary-card small{color:var(--color-ui-subtext);font-size:.74rem}
.filter-card { padding-block: 1rem; }.filter-grid { display: grid; grid-template-columns: minmax(12rem,1fr) minmax(10rem,.75fr) minmax(14rem,1.25fr) auto; gap: .8rem; }.alert-list-card{min-width:0}.alert-table-wrap{overflow-x:auto;border:1px solid var(--color-ui-border);border-radius:18px}.time-cell{display:flex;align-items:center;gap:.55rem}.time-dot{width:.48rem;height:.48rem;flex:0 0 auto;border-radius:50%;background:#e6ab20;box-shadow:0 0 0 4px rgba(230,171,32,.13)}.alert-summary-cell{font-weight:650;color:var(--color-ui-text)}.suggestion-cell{color:var(--color-ui-subtext)}.table-footer{display:flex;justify-content:flex-end;padding:1rem 0 0}.alert-table-wrap :deep(.clickable-row){cursor:pointer}.alert-table-wrap :deep(.clickable-row:hover td.el-table__cell){background:#f5f8ec!important}
.empty-state{display:grid;justify-items:center;gap:.6rem;padding:3rem 1rem;text-align:center;color:var(--color-ui-subtext)}.empty-state__icon{display:grid;width:3.5rem;height:3.5rem;place-items:center;border-radius:18px;background:#edf7e7;color:#69ad38;font-size:1.35rem;font-weight:900}.empty-state h4,.empty-state p{margin:0}.empty-state h4{color:var(--color-ui-text)}
.maintenance-row{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:.8rem;padding:.75rem 1rem;border:1px dashed #dfc7c2;border-radius:16px;background:#fff9f7}.maintenance-row span{color:#b84b4b;font-size:.76rem;font-weight:800;text-transform:uppercase}.maintenance-row p{margin:0;color:var(--color-ui-subtext);font-size:.78rem}
@media(max-width:1000px){.alert-summary-grid{grid-template-columns:1fr 1fr}.filter-grid{grid-template-columns:1fr 1fr}.maintenance-row{grid-template-columns:1fr}}
@media(max-width:640px){.hero-actions{justify-content:flex-start}.alert-summary-grid,.filter-grid{grid-template-columns:1fr}.table-footer{justify-content:center}.table-footer :deep(.el-pagination__total){display:none}}
</style>
