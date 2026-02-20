<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h2 class="text-xl font-bold text-ui-text">全部信息与告警</h2>
      <span class="text-sm text-ui-subtext">
        共 {{ total }} 条记录
      </span>
    </div>

    <el-card
      class="bg-white rounded-[8px] shadow-sm border border-ui-border"
      :body-style="{ padding: '0' }"
    >
      <el-table
        :data="paginatedList"
        style="width: 100%"
        border
        stripe
        v-loading="loading"
      >
        <!-- 时间：后端字段为 createdAt -->
        <el-table-column
          prop="createdAt"
          label="时间"
          min-width="180"
        >
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column
          prop="serverIp"
          label="服务器 IP"
          min-width="140"
        />

        <el-table-column
          prop="component"
          label="组件"
          min-width="120"
        />

        <el-table-column
          prop="riskLevel"
          label="风险等级"
          min-width="120"
        >
          <template #default="{ row }">
            <el-tag
              :type="getTagType(row.riskLevel)"
              effect="light"
              size="small"
            >
              {{ row.riskLevel || 'Normal' }}
            </el-tag>
          </template>
        </el-table-column>

        <!-- 问题摘要 -->
        <el-table-column
          prop="errorSummary"
          label="问题摘要"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.errorSummary || '-' }}
          </template>
        </el-table-column>
        <!-- 遇到的问题（与解决方法分开展示） -->
        <el-table-column
          prop="analysisResult"
          label="遇到的问题"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.analysisResult || '-' }}
          </template>
        </el-table-column>
        <!-- 建议处理方式 -->
        <el-table-column
          prop="suggestedActions"
          label="建议处理方式"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.suggestedActions || '-' }}
          </template>
        </el-table-column>
      </el-table>

      <div
        v-if="!loading && infoList.length === 0"
        class="py-10 text-center text-sm text-ui-subtext"
      >
        暂无告警或信息记录
      </div>

      <div v-if="total > 0" class="flex justify-end py-4 px-4 border-t border-ui-border">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { selectAllInfo } from '../api/info'

const infoList = ref([])
const loading = ref(false)
const pageSize = 10
const currentPage = ref(1)

// 按时间排序：越新越靠前（createdAt 降序）
const sortedList = computed(() => {
  const list = [...infoList.value]
  return list.sort((a, b) => {
    const timeA = parseDateToTime(a.createdAt)
    const timeB = parseDateToTime(b.createdAt)
    return timeB - timeA
  })
})

const total = computed(() => sortedList.value.length)

const paginatedList = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return sortedList.value.slice(start, start + pageSize)
})

function parseDateToTime(value) {
  if (value == null) return 0
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).getTime()
  }
  return new Date(value).getTime()
}

const formatDate = value => {
  if (value == null) return ''
  if (Array.isArray(value)) {
    const [y, m, d, h, min, s] = value
    return new Date(y, (m || 1) - 1, d || 1, h || 0, min || 0, s || 0).toLocaleString()
  }
  return new Date(value).toLocaleString()
}

const getTagType = level => {
  const L = (level || '').toString()
  if (['High', 'Error', '高'].includes(L)) return 'danger'
  if (['Medium', 'Warning', '中'].includes(L)) return 'warning'
  if (['Low', '低'].includes(L)) return 'info'
  return ''
}

const fetchAllInfo = async () => {
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

onMounted(() => {
  fetchAllInfo()
})
</script>

