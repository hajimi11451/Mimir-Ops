<template>
  <div class="app-page disposal-page">
    <section class="page-hero disposal-hero">
      <div>
        <div class="section-eyebrow">Remediation journal</div>
        <h2>处置方案记录</h2>
        <p>记录从告警详情选择并交给助手的处理方案；这里代表已发起，不等同于完整执行审计。</p>
      </div>
      <div class="hero-actions">
        <el-button :disabled="loading" @click="resetFilters">重置筛选</el-button>
        <el-button type="primary" :loading="loading" @click="fetchAllProcess">刷新记录</el-button>
      </div>
    </section>

    <section class="metric-strip">
      <div class="metric-tile metric-tile--mint"><span>当前记录</span><strong>{{ total }}</strong><small>条方案记录</small></div>
      <div class="metric-tile metric-tile--green"><span>关联服务器</span><strong>{{ serverCount }}</strong><small>个目标节点</small></div>
      <div class="metric-tile metric-tile--yellow"><span>涉及组件</span><strong>{{ componentCount }}</strong><small>类服务组件</small></div>
      <div class="metric-tile metric-tile--peach"><span>最新发起</span><strong class="metric-tile__date">{{ latestProcessLabel }}</strong><small>最近一条记录</small></div>
    </section>

    <section class="section-card filter-card">
      <div class="filter-grid">
        <el-input v-model="filters.serverIp" clearable placeholder="筛选服务器 IP" />
        <el-input v-model="filters.component" clearable placeholder="筛选组件" />
        <el-input v-model="filters.keyword" clearable placeholder="搜索问题或处理方案" />
      </div>
    </section>

    <section class="section-card disposal-list-card">
      <div class="section-header">
        <div>
          <div class="section-eyebrow">Selected actions</div>
          <h3>方案列表</h3>
          <p>筛选后共 {{ total }} 条，按发起时间倒序排列。</p>
        </div>
        <span class="status-pill status-pill--info">已发起记录</span>
      </div>

      <div class="disposal-table-wrap">
        <el-table
          :data="paginatedList"
          style="width: 100%"
          v-loading="loading"
        >
          <el-table-column prop="processTime" label="发起时间" min-width="175">
            <template #default="{ row }">
              <div class="time-cell"><span class="time-dot"></span>{{ formatDate(row.processTime) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="serverIp" label="服务器 IP" min-width="150">
            <template #default="{ row }">
              <span class="mono-text">{{ row.serverIp || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="component" label="组件" min-width="140">
            <template #default="{ row }">
              <span class="component-chip">{{ row.component || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="problemLog" label="问题摘要" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatMultilineText(row.problemLog) }}
            </template>
          </el-table-column>
          <el-table-column prop="processMethod" label="选择的方案" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatMultilineText(row.processMethod) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="140" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
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
        <div class="empty-state__icon">✓</div>
        <h4>暂无匹配记录</h4>
        <p>从告警详情选择处理方案并交给助手后，会在这里留下发起记录。</p>
      </div>
    </section>

    <el-dialog
      v-model="detailVisible"
      title="处置方案详情"
      width="min(58rem, calc(100vw - 2rem))"
      class="disposal-detail-dialog"
      append-to-body
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-if="selectedProcess" class="detail-layout">
        <div class="detail-notice">
          <span class="status-pill status-pill--info">方案已发起</span>
          <p>此记录保存用户选择的处理方案，不代表命令已经成功执行。</p>
        </div>
        <div class="detail-meta-grid">
          <div class="soft-panel detail-meta">
            <span>发起时间</span>
            <strong>{{ formatDate(selectedProcess.processTime) }}</strong>
          </div>
          <div class="soft-panel detail-meta">
            <span>服务器 IP</span>
            <strong class="mono-text">{{ selectedProcess.serverIp || '-' }}</strong>
          </div>
          <div class="soft-panel detail-meta">
            <span>组件</span>
            <strong>{{ selectedProcess.component || '-' }}</strong>
          </div>
        </div>
        <div class="detail-content-grid">
          <section>
            <h4>问题日志</h4>
            <pre class="code-panel detail-code">{{ selectedProcess.problemLog || '-' }}</pre>
          </section>
          <section>
            <h4>选择的处置方案</h4>
            <div class="soft-panel detail-method">{{ selectedProcess.processMethod || '-' }}</div>
          </section>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { selectAllProcess } from '../api/info'

const processList = ref([])
const loading = ref(false)
const pageSize = 10
const currentPage = ref(1)
const detailVisible = ref(false)
const selectedProcess = ref(null)

const filters = ref({
  serverIp: '',
  component: '',
  keyword: '',
})

const sortedList = computed(() => {
  const list = [...processList.value]
  return list.sort((a, b) => parseDateToTime(b?.processTime) - parseDateToTime(a?.processTime))
})

const filteredList = computed(() => {
  const serverIp = String(filters.value.serverIp || '').trim().toLowerCase()
  const component = String(filters.value.component || '').trim().toLowerCase()
  const keyword = String(filters.value.keyword || '').trim().toLowerCase()

  return sortedList.value.filter(item => {
    const itemServerIp = String(item?.serverIp || '').trim().toLowerCase()
    const itemComponent = String(item?.component || '').trim().toLowerCase()
    const itemProblemLog = String(item?.problemLog || '').trim().toLowerCase()
    const itemProcessMethod = String(item?.processMethod || '').trim().toLowerCase()

    const matchesServer = !serverIp || itemServerIp.includes(serverIp)
    const matchesComponent = !component || itemComponent.includes(component)
    const matchesKeyword = !keyword
      || itemProblemLog.includes(keyword)
      || itemProcessMethod.includes(keyword)
      || itemServerIp.includes(keyword)
      || itemComponent.includes(keyword)

    return matchesServer && matchesComponent && matchesKeyword
  })
})

const total = computed(() => filteredList.value.length)
const serverCount = computed(() => new Set(filteredList.value.map(item => String(item?.serverIp || '').trim()).filter(Boolean)).size)
const componentCount = computed(() => new Set(filteredList.value.map(item => String(item?.component || '').trim()).filter(Boolean)).size)
const latestProcessLabel = computed(() => {
  const value = filteredList.value[0]?.processTime
  if (!value) return '暂无'
  const text = formatDate(value)
  return text.length > 10 ? text.slice(0, 10) : text
})

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredList.value.slice(start, start + pageSize)
})

watch(
  [
    () => filters.value.serverIp,
    () => filters.value.component,
    () => filters.value.keyword,
  ],
  () => {
    currentPage.value = 1
  }
)

async function fetchAllProcess() {
  loading.value = true
  try {
    const res = await selectAllProcess()
    if (Array.isArray(res)) {
      processList.value = res
    } else if (res && Array.isArray(res.data)) {
      processList.value = res.data
    } else {
      processList.value = []
    }
    currentPage.value = 1
  } catch (error) {
    console.error('Failed to fetch all process', error)
    processList.value = []
    ElMessage.error(error?.message || '获取处置记录失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.value = {
    serverIp: '',
    component: '',
    keyword: '',
  }
  currentPage.value = 1
}

function openDetail(row) {
  selectedProcess.value = row || null
  detailVisible.value = true
}

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

function formatMultilineText(value) {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace(/[\r\n]+/g, ' ')
}

onMounted(() => {
  fetchAllProcess()
})
</script>

<style scoped>
.disposal-page { display: grid; gap: 1.25rem; }
.disposal-hero { background: linear-gradient(135deg, #fffdf6 0%, #fff3df 100%); }
.hero-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .75rem; }
.metric-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; }
.metric-tile { display: grid; gap: .3rem; min-height: 8rem; padding: 1.1rem 1.2rem; border: 1px solid var(--color-ui-border); border-radius: 20px; }
.metric-tile--mint { background: #e8f8f4; }.metric-tile--green { background: #eff7e5; }.metric-tile--yellow { background: #fff6d8; }.metric-tile--peach { background: #fff0e4; }
.metric-tile span { color: var(--color-ui-subtext); font-size: .74rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.metric-tile strong { color: var(--color-ui-text); font-size: 1.75rem; line-height: 1.1; }.metric-tile small { color: var(--color-ui-subtext); font-size: .75rem; }
.metric-tile__date { font-size: 1.1rem !important; }
.filter-card { padding-block: 1rem; }.filter-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1rem; }
.disposal-list-card { min-width: 0; }.disposal-table-wrap { overflow-x: auto; border: 1px solid var(--color-ui-border); border-radius: 18px; }
.time-cell { display: flex; align-items: center; gap: .55rem; }.time-dot { width: .48rem; height: .48rem; flex: 0 0 auto; border-radius: 50%; background: #19bfae; box-shadow: 0 0 0 4px rgba(25,191,174,.12); }
.component-chip { display: inline-flex; border-radius: 999px; background: #f3eddb; color: #725d42; padding: .28rem .65rem; font-size: .76rem; font-weight: 700; }
.table-footer { display: flex; justify-content: flex-end; padding: 1rem 0 0; }
.empty-state { display: grid; justify-items: center; gap: .6rem; padding: 3rem 1rem; text-align: center; color: var(--color-ui-subtext); }.empty-state__icon { display: grid; width: 3.5rem; height: 3.5rem; place-items: center; border-radius: 18px; background: #e8f8f4; color: #19bfae; font-size: 1.4rem; font-weight: 900; }.empty-state h4,.empty-state p { margin: 0; }.empty-state h4 { color: var(--color-ui-text); }
.detail-layout { display: grid; gap: 1rem; }.detail-notice { display: flex; align-items: center; gap: .75rem; padding: .85rem 1rem; border: 1px solid #d8e5ef; border-radius: 16px; background: #f1f6fb; }.detail-notice p { margin: 0; color: var(--color-ui-subtext); font-size: .82rem; }
.detail-meta-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: .75rem; }.detail-meta { display: grid; gap: .25rem; padding: .85rem 1rem; }.detail-meta span { color: var(--color-ui-subtext); font-size: .72rem; }.detail-meta strong { color: var(--color-ui-text); font-size: .88rem; }
.detail-content-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }.detail-content-grid section { min-width: 0; }.detail-content-grid h4 { margin: 0 0 .6rem; color: var(--color-ui-text); font-size: .88rem; }.detail-code { max-height: 19rem; margin: 0; overflow: auto; white-space: pre-wrap; }.detail-method { min-height: 9rem; padding: 1rem; color: var(--color-ui-text); font-size: .86rem; line-height: 1.7; white-space: pre-wrap; }
@media (max-width: 900px) { .metric-strip { grid-template-columns: 1fr 1fr; }.detail-content-grid { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .hero-actions { justify-content: flex-start; }.metric-strip,.filter-grid,.detail-meta-grid { grid-template-columns: 1fr; }.detail-notice { align-items: flex-start; flex-direction: column; }.table-footer { justify-content: center; }.table-footer :deep(.el-pagination__total) { display: none; } }
</style>
