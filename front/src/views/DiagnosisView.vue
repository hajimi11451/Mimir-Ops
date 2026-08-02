<template>
  <div class="app-page diagnosis-page">
    <section class="page-hero diagnosis-hero">
      <div>
        <div class="section-eyebrow">Service observability</div>
        <h2>诊断配置</h2>
        <p>为已接入服务器配置组件与日志路径，自动发现异常并进入告警闭环。</p>
      </div>
      <div class="hero-actions">
        <div class="status-pill status-pill--success">{{ activeMonitorCount }} 项监控中</div>
        <el-button type="primary" @click="showCreateForm = !showCreateForm">
          {{ showCreateForm ? '收起配置' : '新增监控配置' }}
        </el-button>
      </div>
    </section>

    <el-collapse-transition>
      <section v-show="showCreateForm" class="section-card diagnosis-create-card">
        <div class="section-header">
          <div>
            <div class="section-eyebrow">New monitor</div>
            <h3>连接服务器并定位日志</h3>
            <p>这里配置的是组件日志监控，不会重复创建整机监控。</p>
          </div>
          <span class="step-badge">01 · 连接与验证</span>
        </div>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          show-icon
          closable
          class="mb-5"
          @close="errorMessage = ''"
        >
          <template #default>
            <div class="text-sm">请检查 IP、账号密码、日志路径和 sudo 权限后重试。</div>
          </template>
        </el-alert>

        <el-form :model="config" label-position="top">
          <div class="form-grid">
            <el-form-item label="服务器 IP" prop="serverIp">
              <el-input v-model="config.serverIp" placeholder="192.168.1.10 或 192.168.1.10:22" clearable @input="invalidatePathVerification" />
            </el-form-item>
            <el-form-item label="SSH 用户名">
              <el-input v-model="config.username" placeholder="例如 root" clearable @input="invalidatePathVerification" />
            </el-form-item>
            <el-form-item label="SSH 密码">
              <el-input v-model="config.password" type="password" placeholder="仅用于连接验证" show-password @input="invalidatePathVerification" />
            </el-form-item>
            <el-form-item label="组件名称" prop="component">
              <el-input v-model="config.component" placeholder="例如 Nginx / MySQL" clearable @input="invalidatePathVerification" @blur="handleComponentChange" />
            </el-form-item>
            <el-form-item class="form-grid__wide" label="日志路径">
              <div class="path-field">
                <el-input v-model="config.logPath" placeholder="可留空，系统将自动探测" clearable @input="invalidatePathVerification">
                  <template #suffix>
                    <span v-if="isVerified" class="verified-label">已验证</span>
                  </template>
                </el-input>
                <el-button :loading="pathLoading" :disabled="!config.serverIp || !config.component" @click="handleComponentChange">
                  自动探测
                </el-button>
              </div>
            </el-form-item>
            <el-form-item class="form-grid__wide" label="读取权限">
              <div class="soft-panel privilege-row">
                <div>
                  <strong>使用 sudo 读取日志</strong>
                  <span>仅在普通账号无法读取目标日志时启用，账号需要具备 sudo 权限。</span>
                </div>
                <el-switch v-model="config.useSudo" inline-prompt active-text="开" inactive-text="关" @change="handlePrivilegeToggle" />
              </div>
            </el-form-item>
          </div>
        </el-form>

        <div class="form-actions">
          <el-button @click="showCreateForm = false">取消</el-button>
          <el-button type="primary" :loading="loading" :disabled="loading || !config.serverIp || !config.component" @click="handleAddConfig">
            保存并开始监控
          </el-button>
        </div>
      </section>
    </el-collapse-transition>

    <section class="section-card">
      <div class="section-header">
        <div>
          <div class="section-eyebrow">Configured services</div>
          <h3>组件监控列表</h3>
          <p>共 {{ monitorList.length }} 项配置，{{ pausedMonitorCount }} 项已暂停。</p>
        </div>
        <el-button :loading="listLoading" :disabled="loading" @click="fetchConfigs">刷新列表</el-button>
      </div>

      <div class="diagnosis-table-wrap">
        <el-table
          :data="monitorList"
          style="width: 100%"
          v-loading="listLoading"
        >
          <el-table-column prop="serverIp" label="服务器" min-width="180">
            <template #default="{ row }">
              <div class="server-cell">
                <span class="server-dot"></span>
                <span class="mono-text">{{ row.serverIp }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="component" label="组件" min-width="130">
            <template #default="{ row }">
              <strong class="component-name">{{ row.component }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="configValue" label="日志路径" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <code class="path-code">{{ row.configValue || '自动探测' }}</code>
            </template>
          </el-table-column>
          <el-table-column label="读取方式" min-width="120">
            <template #default="{ row }">
              <span class="status-pill" :class="row.useSudo ? 'status-pill--warning' : 'status-pill--info'">{{ row.useSudo ? 'sudo' : '普通读取' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="120">
            <template #default="{ row }">
              <span class="status-pill" :class="getMonitorStatusClass(row.isEnabled)">{{ Number(row.isEnabled) === 0 ? '已暂停' : '监控中' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="right" width="210" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-popconfirm
                  :title="Number(row.isEnabled) === 0 ? '确定要恢复该监控任务吗？' : '确定要暂停该监控任务吗？'"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleToggleStatus(row)"
                >
                  <template #reference>
                    <el-button link :type="Number(row.isEnabled) === 0 ? 'primary' : 'warning'">
                      {{ Number(row.isEnabled) === 0 ? '恢复检测' : '暂停检测' }}
                    </el-button>
                  </template>
                </el-popconfirm>

                <el-popconfirm
                  title="确定要删除该监控任务吗？"
                  confirm-button-text="确定"
                  cancel-button-text="取消"
                  @confirm="handleDelete(row.id)"
                >
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!monitorList.length && !listLoading" class="empty-state">
          <div class="empty-state__icon">⌁</div>
          <h4>还没有组件监控</h4>
          <p>先新增一项组件与日志路径配置，系统会开始自动诊断。</p>
          <el-button type="primary" @click="showCreateForm = true">新增监控配置</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLogPath, addConfig, listConfigs, deleteConfig, updateConfigStatus } from '../api/diagnosis'

const config = reactive({
  serverIp: '',
  component: '',
  logPath: '',
  username: '',
  password: '',
  useSudo: false,
})

const isVerified = ref(false)
const pathLoading = ref(false)
const loading = ref(false)
const listLoading = ref(true)
const monitorList = ref([])
const errorMessage = ref('')
const showCreateForm = ref(false)

const activeMonitorCount = computed(() => monitorList.value.filter(item => Number(item?.isEnabled) !== 0).length)
const pausedMonitorCount = computed(() => monitorList.value.filter(item => Number(item?.isEnabled) === 0).length)

const getMonitorStatusClass = isEnabled => (
  Number(isEnabled) === 0
    ? 'status-pill--warning'
    : 'status-pill--success'
)

const invalidatePathVerification = () => {
  isVerified.value = false
}

const handleComponentChange = async () => {
  if (!config.serverIp || !config.component) return

  isVerified.value = false
  pathLoading.value = true
  errorMessage.value = ''
  try {
    const res = await getLogPath(
      config.serverIp,
      config.component,
      config.username,
      config.password,
      config.useSudo,
    )
    // request.js 已经把后端 { code, data } 解包成 data
    // 这里期望 res 为 { path: string, ... }
    if (res && res.path) {
      config.logPath = res.path
      isVerified.value = true
    }
  } catch (error) {
    console.error('Auto detect path failed', error)
    isVerified.value = false
    errorMessage.value = error?.message || '自动探测日志路径失败'
  } finally {
    pathLoading.value = false
  }
}

const handlePrivilegeToggle = () => {
  isVerified.value = false
  if (config.serverIp && config.component) {
    handleComponentChange()
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
      useSudo: config.useSudo,
    }
    await addConfig(payload)

    // 成功：清空表单并刷新列表
    config.serverIp = ''
    config.component = ''
    config.logPath = ''
    config.username = ''
    config.password = ''
    config.useSudo = false
    isVerified.value = false
    errorMessage.value = ''
    showCreateForm.value = false

    await fetchConfigs()
  } catch (error) {
    console.error('Failed to add config', error)
    // 显示错误信息（如SSH认证失败），用户可重新输入账号密码
    errorMessage.value = error.message || '添加配置失败，请检查后重试'
  } finally {
    loading.value = false
  }
}

const fetchConfigs = async () => {
  listLoading.value = true
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
  } finally {
    listLoading.value = false
  }
}

const handleDelete = async id => {
  try {
    await deleteConfig(id)
    ElMessage.success('监控任务已删除')
    await fetchConfigs()
  } catch (error) {
    console.error('Failed to delete config', error)
    ElMessage.error(error?.message || '删除监控任务失败')
  }
}

const handleToggleStatus = async row => {
  try {
    const nextEnabled = Number(row?.isEnabled) === 0 ? 1 : 0
    await updateConfigStatus(row.id, nextEnabled)
    ElMessage.success(nextEnabled === 1 ? '监控已恢复' : '监控已暂停')
    await fetchConfigs()
  } catch (error) {
    console.error('Failed to update config status', error)
    ElMessage.error(error?.message || '更新监控状态失败')
  }
}

onMounted(() => {
  fetchConfigs()
})
</script>

<style scoped>
.diagnosis-page { display: grid; gap: 1.25rem; }
.diagnosis-hero { background: linear-gradient(135deg, #fffdf6 0%, #f1f8ec 100%); }
.hero-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: .75rem; }
.diagnosis-create-card { border-color: rgba(25, 191, 174, .28); }
.step-badge { border-radius: 999px; background: #e4f7f3; color: #158f84; padding: .5rem .85rem; font-size: .75rem; font-weight: 800; }
.form-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: .25rem 1rem; }
.form-grid__wide { grid-column: span 3; }
.path-field { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: .75rem; width: 100%; }
.verified-label { color: #69ad38; font-size: .75rem; font-weight: 800; }
.privilege-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; width: 100%; padding: .9rem 1rem; }
.privilege-row div { display: grid; gap: .2rem; }
.privilege-row strong { color: var(--color-ui-text); font-size: .875rem; }
.privilege-row span { color: var(--color-ui-subtext); font-size: .75rem; }
.form-actions { display: flex; justify-content: flex-end; gap: .75rem; margin-top: 1rem; padding-top: 1rem; border-top: 1px dashed var(--color-ui-border); }
.diagnosis-table-wrap { overflow-x: auto; border: 1px solid var(--color-ui-border); border-radius: 18px; }
.server-cell { display: flex; align-items: center; gap: .6rem; }
.server-dot { width: .55rem; height: .55rem; border-radius: 50%; background: #69ad38; box-shadow: 0 0 0 4px rgba(105, 173, 56, .12); }
.component-name { color: var(--color-ui-text); }
.path-code { display: inline-block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; color: #725d42; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: .78rem; white-space: nowrap; }
.table-actions { display: flex; justify-content: flex-end; gap: .25rem; }
.empty-state { display: grid; justify-items: center; gap: .65rem; padding: 3rem 1rem; text-align: center; color: var(--color-ui-subtext); }
.empty-state__icon { display: grid; width: 3.5rem; height: 3.5rem; place-items: center; border-radius: 20px; background: #e4f7f3; color: #19bfae; font-size: 2rem; }
.empty-state h4 { margin: .25rem 0 0; color: var(--color-ui-text); font-size: 1rem; }
.empty-state p { margin: 0 0 .35rem; font-size: .85rem; }
@media (max-width: 900px) {
  .form-grid { grid-template-columns: 1fr 1fr; }
  .form-grid__wide { grid-column: span 2; }
}
@media (max-width: 640px) {
  .hero-actions { justify-content: flex-start; }
  .form-grid { grid-template-columns: 1fr; }
  .form-grid__wide { grid-column: auto; }
  .path-field { grid-template-columns: 1fr; }
  .privilege-row { align-items: flex-start; }
}
</style>


