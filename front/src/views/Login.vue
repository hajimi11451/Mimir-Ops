<template>
  <div class="h-screen flex overflow-hidden font-sans antialiased">
    <div class="relative hidden md:flex md:w-5/12 flex-col justify-center overflow-hidden bg-brand-dark px-12 text-white lg:px-20">
      <div class="pointer-events-none absolute inset-0 opacity-10">
        <svg class="h-full w-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <path d="M0 100 L100 0 L100 100 Z" fill="#4299e1" />
        </svg>
      </div>

      <div class="relative z-10 space-y-8">
        <div class="flex items-center space-x-3">
          <div class="flex h-10 w-10 items-center justify-center rounded bg-brand">
            <svg class="h-6 w-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <span class="text-2xl font-bold tracking-wide">灵枢智维</span>
        </div>

        <div>
          <h1 class="mb-2 text-3xl font-bold">智能驱动，运维无忧</h1>
          <p class="text-lg text-gray-400">企业级智能运维解决方案，为您的业务保驾护航。</p>
        </div>

        <ul class="space-y-4 text-gray-300">
          <li class="flex items-center space-x-3">
            <svg class="h-5 w-5 flex-shrink-0 text-ui-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>全链路智能监控与数据分析</span>
          </li>
          <li class="flex items-center space-x-3">
            <svg class="h-5 w-5 flex-shrink-0 text-ui-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>毫秒级异常预警机制</span>
          </li>
          <li class="flex items-center space-x-3">
            <svg class="h-5 w-5 flex-shrink-0 text-ui-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>自动化根因分析 (RCA)</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="flex w-full flex-col items-center justify-center bg-white px-8 md:w-7/12 md:px-16">
      <div class="w-full max-w-md space-y-8">
        <div class="text-center md:text-left">
          <h2 class="text-2xl font-bold text-ui-text">欢迎回来</h2>
          <p class="mt-2 text-ui-subtext">请登录您的管理账号以继续</p>
        </div>

        <el-form :model="form" class="space-y-6" label-position="top">
          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            class="p-0"
            :closable="false"
          />

          <el-form-item label="账号/邮箱" prop="username" class="space-y-1">
            <el-input
              v-model="form.username"
              id="email"
              type="text"
              autocomplete="username"
              placeholder="请输入用户名或邮箱"
              clearable
              class="appearance-none block h-12 w-full placeholder-gray-400 input-focus sm:text-sm"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password" class="space-y-1">
            <el-input
              v-model="form.password"
              id="password"
              type="password"
              autocomplete="current-password"
              placeholder="请输入密码"
              show-password
              class="appearance-none block h-12 w-full placeholder-gray-400 input-focus sm:text-sm"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div>
            <el-button
              type="primary"
              class="btn-hover w-full !h-auto rounded-lg border border-transparent px-4 py-3 text-sm font-bold text-white shadow-sm transition-all duration-150 disabled:cursor-not-allowed disabled:opacity-50"
              :loading="loading"
              :disabled="loading"
              @click="handleLogin"
            >
              <span v-if="loading">登录中...</span>
              <span v-else>登 录</span>
            </el-button>
          </div>

          <div class="mt-6 text-center">
            <p class="text-sm text-ui-subtext">
              还没有账号？
              <router-link to="/register" class="font-medium text-brand hover:text-brand-hover">
                立即注册
              </router-link>
            </p>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/user'

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
