<template>
  <div class="min-h-screen bg-ui-bg text-ui-text">
    <AppShellHeader show-auth-links />

    <main class="mx-auto flex w-full max-w-[1460px] flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row lg:items-stretch lg:px-8">
      <section class="relative overflow-hidden rounded-[32px] border border-white/70 bg-[linear-gradient(180deg,rgba(255,255,255,0.92),rgba(240,247,255,0.96))] p-8 shadow-[0_30px_80px_-44px_rgba(15,23,42,0.28)] lg:flex lg:flex-1 lg:flex-col lg:justify-between">
        <div class="pointer-events-none absolute right-[-60px] top-[-60px] h-48 w-48 rounded-full bg-brand/10 blur-3xl"></div>
        <div class="pointer-events-none absolute bottom-[-40px] left-[-40px] h-40 w-40 rounded-full bg-sky-200/50 blur-3xl"></div>

        <div class="relative z-10 max-w-xl space-y-6">
          <span class="inline-flex rounded-full border border-brand/12 bg-brand/6 px-4 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-brand">
            创建账号
          </span>
          <div>
            <h1 class="text-3xl font-semibold tracking-[-0.04em] text-ui-text sm:text-5xl">把监控、诊断和处置放进同一个工作台。</h1>
            <p class="mt-4 max-w-lg text-sm leading-7 text-ui-subtext sm:text-base">
              注册后即可使用灵枢智维控制台，统一查看服务器态势、告警记录与自动化处置流程。
            </p>
          </div>
        </div>

        <div class="relative z-10 mt-8 grid gap-4 sm:grid-cols-3">
          <div class="rounded-[24px] border border-white/32 bg-white/20 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.22)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">总览</div>
            <div class="mt-3 text-xl font-semibold text-ui-text">状态清晰</div>
            <p class="mt-2 text-sm text-ui-subtext">统一查看服务器健康和最新告警。</p>
          </div>
          <div class="rounded-[24px] border border-white/32 bg-white/20 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.22)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">助手</div>
            <div class="mt-3 text-xl font-semibold text-ui-text">智能协同</div>
            <p class="mt-2 text-sm text-ui-subtext">对话式生成命令并保留操作记录。</p>
          </div>
          <div class="rounded-[24px] border border-white/32 bg-white/20 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.22)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-ui-subtext">通知</div>
            <div class="mt-3 text-xl font-semibold text-ui-text">闭环提醒</div>
            <p class="mt-2 text-sm text-ui-subtext">关键告警通过邮件触达接收人。</p>
          </div>
        </div>
      </section>

      <section class="w-full rounded-[32px] border border-white/42 bg-white/18 p-6 shadow-[0_34px_88px_-48px_rgba(15,23,42,0.38)] backdrop-blur-[30px] sm:p-8 lg:w-[460px]">
        <div class="space-y-2">
          <span class="inline-flex rounded-full bg-brand/8 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-brand">注册</span>
          <h2 class="text-2xl font-semibold tracking-[-0.03em] text-ui-text">创建账号</h2>
          <p class="text-sm text-ui-subtext">填写信息后立即开始使用。</p>
        </div>

        <el-form :model="form" class="mt-8 space-y-5" label-position="top">
          <el-alert
            v-if="message"
            :title="message"
            :type="messageType === 'success' ? 'success' : 'error'"
            show-icon
            class="p-0"
            :closable="false"
          />

          <el-form-item label="用户名" prop="username" class="space-y-1">
            <el-input
              v-model="form.username"
              type="text"
              autocomplete="username"
              placeholder="请设置用户名"
              clearable
              class="appearance-none block w-full h-12 input-focus"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password" class="space-y-1">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="请输入密码"
              show-password
              class="appearance-none block w-full h-12 input-focus"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword" class="space-y-1">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码"
              show-password
              class="appearance-none block w-full h-12 input-focus"
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <el-button
            type="primary"
            class="w-full !h-auto rounded-2xl border-0 px-4 py-3 text-sm font-semibold text-white shadow-[0_22px_38px_-24px_rgba(37,99,235,0.85)] transition-all duration-150 hover:translate-y-[-1px]"
            :loading="loading"
            :disabled="loading"
            @click="handleRegister"
          >
            {{ loading ? '注册中...' : '注册' }}
          </el-button>

          <div class="pt-2 text-center text-sm text-ui-subtext">
            已有账号？
            <router-link to="/login" class="font-medium text-brand hover:text-brand-hover">
              去登录
            </router-link>
          </div>
        </el-form>
      </section>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/user'
import AppShellHeader from '../components/AppShellHeader.vue'

const router = useRouter()
const loading = ref(false)
const message = ref('')
const messageType = ref('error')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const handleRegister = async () => {
  if (!form.username || !form.password || !form.confirmPassword) {
    message.value = '请完整填写注册信息'
    messageType.value = 'error'
    return
  }

  if (form.password !== form.confirmPassword) {
    message.value = '两次输入的密码不一致'
    messageType.value = 'error'
    return
  }

  loading.value = true
  message.value = ''

  try {
    await register({
      username: form.username,
      password: form.password,
    })

    message.value = '注册成功，正在跳转登录页...'
    messageType.value = 'success'
    setTimeout(() => {
      router.push('/login')
    }, 1200)
  } catch (error) {
    messageType.value = 'error'
    message.value = error.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

