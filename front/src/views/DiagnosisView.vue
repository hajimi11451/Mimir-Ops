<template>
  <div class="space-y-8">
    <!-- 1. 添加监控配置区 -->
    <el-card
      class="bg-white rounded-[8px] shadow-sm border border-ui-border"
      :body-style="{ padding: '24px' }"
    >
      <h2 class="text-lg font-bold mb-4 text-ui-text flex items-center">
        <svg
          class="w-5 h-5 mr-2 text-brand"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 6v6m0 0v6m0-6h6m-6 0H6"
          />
        </svg>
        添加监控任务 (自动检测)
      </h2>

      <!-- SSH认证失败提示 -->
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="true"
        @close="errorMessage = ''"
        class="mb-4"
      >
        <template #default>
          <div class="text-sm">
            {{ errorMessage }}
            <div class="mt-2 text-xs text-gray-600">
              请检查服务器IP、SSH用户名和密码是否正确，然后重新提交。
            </div>
          </div>
        </template>
      </el-alert>

      <el-form :model="config" label-position="top">
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <!-- 服务器 IP -->
          <el-form-item
            class="mb-0"
            label="服务器 IP"
            prop="serverIp"
          >
            <template #label>
              <span class="block text-sm font-medium text-ui-subtext mb-1">
                服务器 IP
                <span class="text-xs text-ui-subtext font-normal ml-2">
                  (支持 IP:Port 格式，默认端口 22)
                </span>
              </span>
            </template>
            <el-input
              v-model="config.serverIp"
              placeholder="192.168.1.10 或 192.168.1.10:2222"
              class="w-full border-ui-border rounded-lg shadow-sm focus:ring-brand focus:border-brand bg-white transition-all"
              clearable
            />
          </el-form-item>

          <!-- SSH 用户名 -->
          <el-form-item
            class="mb-0"
            label="SSH 用户名 (可选)"
          >
            <el-input
              v-model="config.username"
              placeholder="root"
              class="w-full border-ui-border rounded-lg shadow-sm focus:ring-brand focus:border-brand bg-white transition-all"
              clearable
            />
          </el-form-item>

          <!-- SSH 密码 -->
          <el-form-item
            class="mb-0"
            label="SSH 密码 (可选)"
          >
            <el-input
              v-model="config.password"
              type="password"
              placeholder="请输入密码"
              show-password
              class="w-full border-ui-border rounded-lg shadow-sm focus:ring-brand focus:border-brand bg-white transition-all"
            />
          </el-form-item>

          <!-- 组件名称 -->
          <el-form-item
            class="mb-0"
            label="组件名称"
            prop="component"
          >
            <el-input
              v-model="config.component"
              @blur="handleComponentChange"
              placeholder="请输入组件名 (如 MySQL)"
              class="w-full border-ui-border rounded-lg shadow-sm focus:ring-brand focus:border-brand bg-white transition-all"
              clearable
            />
          </el-form-item>

          <!-- 日志路径 -->
          <el-form-item class="mb-0" label="日志路径">
            <template #label>
              <label class="block text-sm font-medium text-ui-subtext mb-1 flex justify-between items-center">
                <span>
                  日志路径
                  <span class="text-xs text-ui-subtext font-normal ml-2">
                    (可选)
                  </span>
                </span>
                <span
                  v-if="config.logPath"
                  :class="isVerified ? 'text-ui-success' : 'text-ui-warning'"
                  class="text-xs font-bold"
                >
                  {{ isVerified ? '● 已验证' : '● AI猜测中' }}
                </span>
                <span v-else class="text-xs text-ui-brand">
                  <span class="inline-block w-2 h-2 rounded-full bg-brand mr-1"></span>
                  系统将自动探测路径
                </span>
              </label>
            </template>
            <div class="relative w-full">
              <el-input
                v-model="config.logPath"
                placeholder="留空则自动使用 AI 探测路径..."
                class="w-full border-ui-border rounded-lg shadow-sm focus:ring-brand focus:border-brand bg-white transition-all"
                clearable
              />
              <div v-if="pathLoading" class="absolute right-3 top-3">
                <div class="animate-spin h-4 w-4 border-2 border-brand border-t-transparent rounded-full"></div>
              </div>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <div class="mt-6 flex justify-end">
        <el-button
          type="primary"
          class="flex items-center justify-center px-6 py-2.5 text-white font-bold rounded-lg transition-all shadow-md group"
          :class="loading || !config.serverIp || !config.component ? 'bg-gray-400 cursor-not-allowed' : 'bg-brand hover:bg-brand-hover'"
          :loading="loading"
          :disabled="loading || !config.serverIp || !config.component"
          @click="handleAddConfig"
        >
          <span class="flex items-center">
            <svg
              class="w-4 h-4 mr-2"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M5 13l4 4L19 7"
              />
            </svg>
            <span v-if="loading">保存中...</span>
            <span v-else>保存并开启自动监控</span>
          </span>
        </el-button>
      </div>
    </el-card>

    <!-- 2. 监控列表 -->
    <el-card
      class="bg-white rounded-[8px] shadow-sm border border-ui-border"
      :body-style="{ padding: '24px' }"
    >
      <h2 class="text-lg font-bold mb-4 text-ui-text flex items-center">
        <svg
          class="w-5 h-5 mr-2 text-brand"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
          />
        </svg>
        运行中的监控任务
      </h2>

      <div class="overflow-x-auto">
        <el-table
          :data="monitorList"
          style="width: 100%"
          border
          stripe
          :header-cell-class-name="() => 'bg-gray-50 text-gray-500 text-xs font-medium uppercase tracking-wider'"
          :cell-class-name="() => 'text-sm text-gray-700'"
        >
          <el-table-column
            prop="serverIp"
            label="服务器 IP"
            min-width="160"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                {{ row.serverIp }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            prop="component"
            label="组件"
            min-width="120"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                {{ row.component }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            prop="configValue"
            label="日志路径"
            min-width="220"
          >
            <template #default="{ row }">
              <span class="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">
                {{ row.configValue }}
              </span>
            </template>
          </el-table-column>

          <el-table-column
            label="状态"
            min-width="120"
          >
            <template #default>
              <span class="px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800">
                监控中
              </span>
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            align="right"
            width="120"
          >
            <template #default="{ row }">
              <el-popconfirm
                title="确定要停止该监控任务吗？"
                confirm-button-text="确定"
                cancel-button-text="取消"
                @confirm="handleDelete(row.id)"
              >
                <template #reference>
                  <el-button
                    link
                    type="danger"
                    class="text-red-600 hover:text-red-900 text-sm font-medium"
                  >
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div
          v-if="monitorList.length === 0"
          class="px-6 py-12 text-center text-gray-500 text-sm"
        >
          暂无监控配置，请在上方添加
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogPath, addConfig, listConfigs, deleteConfig } from '../api/diagnosis'

const config = reactive({
  serverIp: '',
  component: '',
  logPath: '',
  username: '',
  password: '',
})

const isVerified = ref(false)
const pathLoading = ref(false)
const loading = ref(false)
const monitorList = ref([])
const errorMessage = ref('')

const handleComponentChange = async () => {
  if (!config.serverIp || !config.component) return

  pathLoading.value = true
  try {
    const res = await getLogPath(
      config.serverIp,
      config.component,
      config.username,
      config.password,
    )
    // request.js 已经把后端 { code, data } 解包成 data
    // 这里期望 res 为 { path: string, ... }
    if (res && res.path) {
      config.logPath = res.path
      isVerified.value = true
    }
  } catch (error) {
    console.error('Auto detect path failed', error)
  } finally {
    pathLoading.value = false
  }
}

const handleAddConfig = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const payload = {
      serverIp: config.serverIp,
      component: config.component,
      configKey: 'error_log_path',
      configValue: config.logPath,
      isEnabled: 1,
      username: config.username,
      password: config.password,
    }
    await addConfig(payload)

    // 成功：清空表单并刷新列表
    config.serverIp = ''
    config.component = ''
    config.logPath = ''
    config.username = ''
    config.password = ''
    isVerified.value = false
    errorMessage.value = ''

    fetchConfigs()
  } catch (error) {
    console.error('Failed to add config', error)
    // 显示错误信息（如SSH认证失败），用户可重新输入账号密码
    errorMessage.value = error.message || '添加配置失败，请检查后重试'
  } finally {
    loading.value = false
  }
}

const fetchConfigs = async () => {
  try {
    const res = await listConfigs()
    // 经过 request.js 拦截后，如果后端为 { code, data: [...] }
    // 这里的 res 应该已经是数组
    if (Array.isArray(res)) {
      monitorList.value = res
    } else if (res && Array.isArray(res.data)) {
      // 兼容旧结构
      monitorList.value = res.data
    } else {
      monitorList.value = []
    }
  } catch (error) {
    console.error('Failed to list configs', error)
  }
}

const handleDelete = async id => {
  if (!confirm('确定要停止该监控任务吗？')) return
  try {
    await deleteConfig(id)
    fetchConfigs()
  } catch (error) {
    console.error('Failed to delete config', error)
  }
}

onMounted(() => {
  fetchConfigs()
})
</script>

