<template>
  <div class="auth-page">
    <span class="auth-organic-shape auth-organic-shape--one" aria-hidden="true"></span>
    <span class="auth-organic-shape auth-organic-shape--two" aria-hidden="true"></span>

    <AppShellHeader class="relative z-10" show-auth-links />

    <main class="auth-layout">
      <section class="auth-story" aria-labelledby="login-story-title">
        <div class="auth-story__content">
          <span class="auth-badge">欢迎回来</span>
          <h1 id="login-story-title" class="auth-title">继续掌握每一台服务器的运行节奏。</h1>
          <p class="auth-description">
            登录灵枢智维，在同一个温暖清晰的工作台里查看运行态势、分析告警，并让智能助手协同完成处置。
          </p>

          <div class="auth-feature-list" aria-label="平台能力">
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="M4 18V9m5 9V5m5 13v-6m5 6V8" stroke-width="1.8" stroke-linecap="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">运行态势集中呈现</div>
                <p class="auth-feature__text">快速查看服务器健康、资源趋势与最新异常。</p>
              </div>
            </article>
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="M8 10h.01M12 10h.01M16 10h.01M9 16H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-4l-5 4v-4Z" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">智能诊断与执行协同</div>
                <p class="auth-feature__text">从咨询、规划到风险确认，保留清晰的操作上下文。</p>
              </div>
            </article>
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="M12 3.5a6 6 0 0 0-6 6v3.25L4.5 16h15L18 12.75V9.5a6 6 0 0 0-6-6ZM9.75 19h4.5" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">告警与通知闭环</div>
                <p class="auth-feature__text">筛选关键事件，缩短从发现问题到发起处置的路径。</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="auth-form-card" aria-labelledby="login-form-title">
        <div class="auth-form-card__inner">
          <div class="auth-form-kicker">Secure Sign In</div>
          <h2 id="login-form-title" class="auth-form-title">登录账号</h2>
          <p class="auth-form-description">请输入账号信息，继续进入智能运维工作台。</p>

          <el-form :model="form" label-position="top">
            <el-alert
              v-if="errorMessage"
              :title="errorMessage"
              type="error"
              show-icon
              :closable="false"
            />

            <el-form-item label="账号 / 邮箱" prop="username">
              <el-input
                id="email"
                v-model="form.username"
                type="text"
                autocomplete="username"
                placeholder="请输入用户名或邮箱"
                clearable
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                id="password"
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>

            <el-button
              type="primary"
              class="auth-submit"
              :loading="loading"
              :disabled="loading"
              @click="handleLogin"
            >
              {{ loading ? '登录中...' : '登录并进入工作台' }}
            </el-button>

            <div class="auth-switch-link">
              还没有账号？
              <router-link to="/register">立即注册</router-link>
            </div>
          </el-form>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShellHeader from '../components/AppShellHeader.vue'
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
