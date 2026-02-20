<template>
  <div class="h-screen flex font-sans antialiased overflow-hidden">
    <!-- 左侧品牌区域 (45%) -->
    <div class="hidden md:flex md:w-5/12 bg-brand-dark relative flex-col justify-center px-12 lg:px-20 text-white overflow-hidden">
      <!-- 背景装饰 -->
      <div class="absolute inset-0 opacity-10 pointer-events-none">
        <svg class="w-full h-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <path d="M0 100 L100 0 L100 100 Z" fill="#4299e1" />
        </svg>
      </div>

      <div class="relative z-10 space-y-8">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 bg-brand rounded flex items-center justify-center">
            <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
            </svg>
          </div>
          <span class="text-2xl font-bold tracking-wide">AIOps</span>
        </div>

        <div>
          <h1 class="text-3xl font-bold mb-2">加入我们</h1>
          <p class="text-gray-400 text-lg">开始您的智能运维之旅。</p>
        </div>
      </div>

      <div class="absolute bottom-8 text-xs text-gray-500">
        © 2024 Enterprise AIOps Inc. All rights reserved.
      </div>
    </div>

    <!-- 右侧注册表单 (55%) -->
    <div class="w-full md:w-7/12 bg-white flex flex-col justify-center items-center px-8 md:px-16">
      <div class="w-full max-w-md space-y-8">
        <div class="text-center md:text-left">
          <h2 class="text-2xl font-bold text-ui-text">创建新账号</h2>
          <p class="mt-2 text-ui-subtext">请填写以下信息完成注册</p>
        </div>

        <el-form
          :model="form"
          class="space-y-6"
          label-position="top"
        >
          <!-- 消息提示 -->
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
              id="username"
              v-model="form.username"
              type="text"
              autocomplete="username"
              placeholder="请设置用户名"
              clearable
              class="appearance-none block w-full h-12 placeholder-gray-400 input-focus sm:text-sm"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password" class="space-y-1">
            <el-input
              id="password"
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              placeholder="••••••••••••"
              show-password
              class="appearance-none block w-full h-12 placeholder-gray-400 input-focus sm:text-sm"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword" class="space-y-1">
            <el-input
              id="confirmPassword"
              v-model="form.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="••••••••••••"
              show-password
              class="appearance-none block w-full h-12 placeholder-gray-400 input-focus sm:text-sm"
            />
          </el-form-item>

          <div>
            <el-button
              type="primary"
              class="w-full !h-auto py-3 px-4 border border-transparent rounded-lg shadow-sm text-sm font-bold text-white bg-gradient-to-r from-brand to-brand-hover hover:to-[#2c5282] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-brand btn-hover transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed"
              :loading="loading"
              :disabled="loading"
              @click="handleRegister"
            >
              <span v-if="loading">注册中...</span>
              <span v-else>注 册</span>
            </el-button>
          </div>

          <div class="mt-6 text-center">
            <p class="text-sm text-ui-subtext">
              已有账号？
              <router-link to="/" class="font-medium text-brand hover:text-brand-hover">
                立即登录
              </router-link>
            </p>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/user'

const router = useRouter()
const loading = ref(false)
const message = ref('')
const messageType = ref('error')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: ''
})

const handleRegister = async () => {
  if (form.password !== form.confirmPassword) {
    message.value = '两次输入的密码不一致'
    messageType.value = 'error'
    return
  }

  loading.value = true
  message.value = ''
  
  try {
    const res = await register({
      username: form.username,
      password: form.password
    })

    console.log('Register success:', res)
    message.value = '注册成功！正在跳转到登录页...'
    messageType.value = 'success'
    setTimeout(() => {
      router.push('/')
    }, 1500)
  } catch (error) {
    console.error('Register error:', error)
    messageType.value = 'error'
    message.value = error.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>
