<template>
  <div class="mx-auto max-w-5xl space-y-6">
    <section class="overflow-hidden rounded-[28px] border border-ui-border bg-white shadow-sm">
      <div class="relative overflow-hidden border-b border-ui-border bg-slate-950 px-8 py-10 text-white">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(59,130,246,0.35),_transparent_45%),radial-gradient(circle_at_bottom_right,_rgba(16,185,129,0.24),_transparent_40%)]"></div>
        <div class="relative flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div class="max-w-2xl">
            <p class="text-xs uppercase tracking-[0.28em] text-slate-300">Alert Mail</p>
            <h1 class="mt-3 text-3xl font-bold">邮箱通知设置</h1>
            <p class="mt-3 text-sm leading-6 text-slate-200">
              配置服务器进入红色健康状态后的紧急联系人邮箱。当前版本先做单邮箱通知，后面你可以再扩展成多人和多渠道。
            </p>
          </div>

          <div class="grid gap-3 rounded-2xl border border-white/10 bg-white/5 px-5 py-4 backdrop-blur-sm sm:grid-cols-2">
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-slate-300">当前用户</div>
              <div class="mt-2 text-lg font-semibold text-white">{{ username || '未登录' }}</div>
            </div>
            <div>
              <div class="text-xs uppercase tracking-[0.2em] text-slate-300">发送策略</div>
              <div class="mt-2 text-sm leading-6 text-slate-100">同一问题连续两次红色后发送邮件</div>
            </div>
          </div>
        </div>
      </div>

      <div class="grid gap-6 px-8 py-8 lg:grid-cols-[1.2fr_0.8fr]">
        <div class="space-y-6">
          <div class="rounded-3xl border border-ui-border bg-ui-bg p-6">
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-lg font-bold text-ui-text">紧急联系人邮箱</h2>
                <p class="mt-2 text-sm leading-6 text-ui-subtext">
                  保存后，后端会把邮箱暂存在内存中。服务重启后需要重新填写，后面如果你要持久化再改数据库即可。
                </p>
              </div>
              <span
                class="inline-flex rounded-full border px-3 py-1 text-xs font-semibold"
                :class="emailStatusClass"
              >
                {{ emailStatusText }}
              </span>
            </div>

            <el-form label-position="top" class="mt-6">
              <el-form-item label="邮箱地址">
                <el-input
                  v-model="form.email"
                  clearable
                  placeholder="例如：ops-team@qq.com"
                  @keyup.enter="handleSave"
                />
              </el-form-item>
            </el-form>

            <div class="flex flex-wrap gap-3">
              <el-button type="primary" :loading="saving" @click="handleSave">
                保存邮箱
              </el-button>
              <el-button :loading="testing" @click="handleTestMail">
                发送测试邮件
              </el-button>
              <el-button :disabled="!form.email" @click="handleClear">
                清空输入
              </el-button>
            </div>

            <div class="mt-5 rounded-2xl border border-amber-100 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
              当前版本只发纯文本邮件，适合先验证链路通不通。QQ SMTP 已接好，后续你只需要把敏感配置改到环境变量即可。
            </div>
          </div>

          <div class="rounded-3xl border border-ui-border bg-white p-6">
            <h2 class="text-lg font-bold text-ui-text">触发条件说明</h2>
            <div class="mt-4 grid gap-3 md:grid-cols-3">
              <div class="rounded-2xl border border-red-100 bg-red-50 p-4">
                <div class="text-xs uppercase tracking-[0.18em] text-red-500">Red</div>
                <div class="mt-2 text-sm font-semibold text-red-700">健康度进入红色</div>
                <p class="mt-2 text-xs leading-5 text-red-600">后端统一计算健康度，最近 10 分钟的告警和当前 CPU/内存都会参与评分。</p>
              </div>
              <div class="rounded-2xl border border-orange-100 bg-orange-50 p-4">
                <div class="text-xs uppercase tracking-[0.18em] text-orange-500">Twice</div>
                <div class="mt-2 text-sm font-semibold text-orange-700">连续两次确认</div>
                <p class="mt-2 text-xs leading-5 text-orange-600">同一问题指纹连续两次仍是红色才发，避免瞬时抖动造成误报。</p>
              </div>
              <div class="rounded-2xl border border-sky-100 bg-sky-50 p-4">
                <div class="text-xs uppercase tracking-[0.18em] text-sky-500">Cool Down</div>
                <div class="mt-2 text-sm font-semibold text-sky-700">30 分钟冷却</div>
                <p class="mt-2 text-xs leading-5 text-sky-600">同一问题在冷却时间内只发一次，避免邮件轰炸紧急联系人。</p>
              </div>
            </div>
          </div>
        </div>

        <aside class="space-y-6">
          <div class="rounded-3xl border border-ui-border bg-gradient-to-br from-white to-slate-50 p-6">
            <div class="text-xs uppercase tracking-[0.2em] text-ui-subtext">Quick Check</div>
            <h2 class="mt-3 text-lg font-bold text-ui-text">当前配置概览</h2>
            <div class="mt-5 space-y-4">
              <div class="rounded-2xl border border-ui-border bg-white px-4 py-3">
                <div class="text-xs text-ui-subtext">已保存邮箱</div>
                <div class="mt-2 break-all text-sm font-semibold text-ui-text">
                  {{ savedEmail || '暂未保存' }}
                </div>
              </div>
              <div class="rounded-2xl border border-ui-border bg-white px-4 py-3">
                <div class="text-xs text-ui-subtext">测试状态</div>
                <div class="mt-2 text-sm font-semibold text-ui-text">
                  {{ lastTestMessage || '尚未发送测试邮件' }}
                </div>
              </div>
            </div>
          </div>

          <div class="rounded-3xl border border-ui-border bg-white p-6">
            <h2 class="text-lg font-bold text-ui-text">接入建议</h2>
            <ul class="mt-4 space-y-3 text-sm leading-6 text-ui-subtext">
              <li>先保存自己的邮箱并发送一封测试邮件，确认 QQ SMTP 可用。</li>
              <li>确认服务器出现高风险或资源红色时，观察两轮采样后是否收到邮件。</li>
              <li>等你后面确认交互后，再把这个页面接到更完整的通知设置中心。</li>
            </ul>
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlertContact, saveAlertContact, sendAlertTestMail } from '../api/alert'

const saving = ref(false)
const testing = ref(false)
const savedEmail = ref('')
const lastTestMessage = ref('')

const form = reactive({
  email: '',
})

const username = computed(() => {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    return JSON.parse(raw)?.username || ''
  } catch (error) {
    return ''
  }
})

const emailStatusText = computed(() => savedEmail.value ? '已保存' : '未配置')

const emailStatusClass = computed(() => (
  savedEmail.value
    ? 'border-green-100 bg-green-50 text-green-700'
    : 'border-slate-200 bg-slate-100 text-slate-600'
))

const loadContact = async () => {
  if (!username.value) return

  try {
    const data = await getAlertContact(username.value)
    savedEmail.value = data?.email || ''
    form.email = savedEmail.value
  } catch (error) {
    ElMessage.error(error?.message || '读取紧急联系人邮箱失败')
  }
}

const handleSave = async () => {
  if (!username.value) {
    ElMessage.error('未识别当前登录用户')
    return
  }

  saving.value = true
  try {
    const data = await saveAlertContact({
      username: username.value,
      email: form.email,
    })
    savedEmail.value = data?.email || ''
    form.email = savedEmail.value
    ElMessage.success(savedEmail.value ? '紧急联系人邮箱已保存' : '紧急联系人邮箱已清空')
  } catch (error) {
    ElMessage.error(error?.message || '保存紧急联系人邮箱失败')
  } finally {
    saving.value = false
  }
}

const handleTestMail = async () => {
  if (!username.value) {
    ElMessage.error('未识别当前登录用户')
    return
  }

  testing.value = true
  try {
    await sendAlertTestMail({
      username: username.value,
      email: form.email,
    })
    lastTestMessage.value = `测试邮件已发送到 ${form.email || savedEmail.value}`
    ElMessage.success('测试邮件已发送，请检查收件箱')
  } catch (error) {
    ElMessage.error(error?.message || '发送测试邮件失败')
  } finally {
    testing.value = false
  }
}

const handleClear = () => {
  form.email = ''
}

onMounted(() => {
  loadContact()
})
</script>
