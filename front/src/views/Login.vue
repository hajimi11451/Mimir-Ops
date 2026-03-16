<template>
  <div class="min-h-screen bg-ui-bg text-ui-text">
    <AppShellHeader show-auth-links />

    <main class="mx-auto flex w-full max-w-[1460px] flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row lg:items-stretch lg:px-8">
      <section class="relative overflow-hidden rounded-[32px] border border-white/10 bg-[linear-gradient(135deg,rgba(15,23,42,0.96),rgba(29,78,216,0.92))] p-8 text-white shadow-[0_30px_80px_-42px_rgba(15,23,42,0.8)] lg:flex lg:flex-1 lg:flex-col lg:justify-between">
        <div class="pointer-events-none absolute -left-16 top-16 h-40 w-40 rounded-full bg-white/10 blur-3xl"></div>
        <div class="pointer-events-none absolute right-0 top-0 h-48 w-48 rounded-full bg-sky-300/20 blur-3xl"></div>

        <div class="relative z-10 max-w-xl space-y-6">
          <span class="inline-flex rounded-full border border-white/20 bg-white/8 px-4 py-1 text-xs font-medium uppercase tracking-[0.28em] text-slate-200">
            智能运维控制台
          </span>
          <div>
            <h1 class="text-3xl font-semibold tracking-[-0.04em] sm:text-5xl">用更少的操作，看清服务器和告警。</h1>
            <p class="mt-4 max-w-lg text-sm leading-7 text-slate-300 sm:text-base">
              统一查看监控、诊断、处置与通知，把常用运维动作收进一个控制台。
            </p>
          </div>
        </div>

        <div class="relative z-10 mt-8 grid gap-4 sm:grid-cols-3">
          <div class="rounded-[24px] border border-white/18 bg-white/12 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.38)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-slate-300">监控</div>
            <div class="mt-3 text-2xl font-semibold">1 屏</div>
            <p class="mt-2 text-sm text-slate-300">CPU、内存、告警同屏查看。</p>
          </div>
          <div class="rounded-[24px] border border-white/18 bg-white/12 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.38)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-slate-300">诊断</div>
            <div class="mt-3 text-2xl font-semibold">AI</div>
            <p class="mt-2 text-sm text-slate-300">自动分析日志并生成处理建议。</p>
          </div>
          <div class="rounded-[24px] border border-white/18 bg-white/12 p-5 shadow-[0_18px_40px_-32px_rgba(15,23,42,0.38)] backdrop-blur-[24px]">
            <div class="text-xs uppercase tracking-[0.22em] text-slate-300">处置</div>
            <div class="mt-3 text-2xl font-semibold">闭环</div>
            <p class="mt-2 text-sm text-slate-300">从建议到执行，保留完整记录。</p>
          </div>
        </div>
      </section>

      <section class="w-full rounded-[32px] border border-white/42 bg-white/18 p-6 shadow-[0_34px_88px_-48px_rgba(15,23,42,0.46)] backdrop-blur-[30px] sm:p-8 lg:w-[440px]">
        <div class="space-y-2">
          <span class="inline-flex rounded-full bg-brand/8 px-3 py-1 text-xs font-semibold tracking-[0.18em] text-brand">登录</span>
          <h2 class="text-2xl font-semibold tracking-[-0.03em] text-ui-text">进入控制台</h2>
          <p class="text-sm text-ui-subtext">使用你的账号继续。</p>
        </div>

        <el-form :model="form" class="mt-8 space-y-5" label-position="top">
          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            class="p-0"
            :closable="false"
          />

          <el-form-item label="账号" prop="username" class="space-y-1">
            <el-input
              v-model="form.username"
              type="text"
              autocomplete="username"
              placeholder="请输入用户名"
              clearable
              class="appearance-none block w-full h-12 input-focus"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password" class="space-y-1">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              class="appearance-none block w-full h-12 input-focus"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-button
            type="primary"
            class="w-full !h-auto rounded-2xl border-0 px-4 py-3 text-sm font-semibold text-white shadow-[0_22px_38px_-24px_rgba(37,99,235,0.85)] transition-all duration-150 hover:translate-y-[-1px]"
            :loading="loading"
            :disabled="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登录' }}
          </el-button>

          <div class="pt-2 text-center text-sm text-ui-subtext">
            还没有账号？
            <router-link to="/register" class="font-medium text-brand hover:text-brand-hover">
              去注册
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
import { login } from '../api/user'
import AppShellHeader from '../components/AppShellHeader.vue'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: '',
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    await login({
      username: form.username,
      password: form.password,
    })

    localStorage.setItem('user', JSON.stringify({ username: form.username }))
    router.push('/dashboard')
  } catch (error) {
    errorMessage.value = error.message || '登录失败，请检查用户名或密码'
  } finally {
    loading.value = false
  }
}
</script>

