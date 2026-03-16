<template>
  <div class="workspace-cool-glass mx-auto max-w-7xl space-y-6">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3">
        <h2 class="text-xl font-bold text-ui-text">处置记录</h2>
        <span class="text-sm text-ui-subtext">共 {{ total }} 条记录</span>
      </div>
      <div class="flex items-center gap-2">
        <el-button @click="resetFilters" :disabled="loading">重置</el-button>
        <el-button type="primary" :loading="loading" @click="fetchAllProcess">
          刷新
        </el-button>
      </div>
    </div>

    <el-card
      class="glass-card mb-6 rounded-[30px]"
      :body-style="{ padding: '20px' }"
    >
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <el-input
          v-model="filters.serverIp"
          clearable
          placeholder="服务器 IP"
        />
        <el-input
          v-model="filters.component"
          clearable
          placeholder="组件"
        />
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索关键词"
        />
      </div>
    </el-card>

    <el-card
      class="glass-card rounded-[30px]"
      :body-style="{ padding: '0' }"
    >
      <el-table
        :data="paginatedList"
        style="width: 100%"
        border
        stripe
        v-loading="loading"
      >
        <el-table-column prop="processTime" label="时间" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.processTime) }}
          </template>
        </el-table-column>

        <el-table-column prop="serverIp" label="服务器 IP" min-width="150">
          <template #default="{ row }">
            {{ row.serverIp || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="component" label="组件" min-width="140">
          <template #default="{ row }">
            {{ row.component || '-' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="problemLog"
          label="问题"
          min-width="260"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ formatMultilineText(row.problemLog) }}
          </template>
        </el-table-column>

        <el-table-column
          prop="processMethod"
          label="处置"
          min-width="300"
          show-overflow-tooltip
        >
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

      <div v-if="total > 0" class="glass-table-footer flex justify-end rounded-none border-x-0 border-b-0 px-4 py-4">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="处置详情"
      width="760px"
      destroy-on-close
    >
      <div v-if="selectedProcess" class="space-y-5">
        <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
          <div class="glass-subcard px-4 py-3">
            <div class="text-xs text-ui-subtext mb-1">时间</div>
            <div class="text-sm text-ui-text">{{ formatDate(selectedProcess.processTime) }}</div>
          </div>
          <div class="glass-subcard px-4 py-3">
            <div class="text-xs text-ui-subtext mb-1">服务器 IP</div>
            <div class="text-sm text-ui-text">{{ selectedProcess.serverIp || '-' }}</div>
          </div>
          <div class="glass-subcard px-4 py-3">
            <div class="text-xs text-ui-subtext mb-1">组件</div>
            <div class="text-sm text-ui-text">{{ selectedProcess.component || '-' }}</div>
          </div>
        </div>

        <div>
          <div class="text-sm font-semibold text-ui-text mb-2">问题</div>
          <div class="glass-code-block px-4 py-3 whitespace-pre-wrap text-sm leading-6 text-ui-text">
            {{ selectedProcess.problemLog || '-' }}
          </div>
        </div>

        <div>
          <div class="text-sm font-semibold text-ui-text mb-2">处置</div>
          <div class="glass-code-block px-4 py-3 whitespace-pre-wrap text-sm leading-6 text-ui-text">
            {{ selectedProcess.processMethod || '-' }}
          </div>
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
