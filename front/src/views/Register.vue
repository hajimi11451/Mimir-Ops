<template>
  <div class="auth-page">
    <span class="auth-organic-shape auth-organic-shape--one" aria-hidden="true"></span>
    <span class="auth-organic-shape auth-organic-shape--two" aria-hidden="true"></span>

    <AppShellHeader class="relative z-10" show-auth-links />

    <main class="auth-layout">
      <section class="auth-story" aria-labelledby="register-story-title">
        <div class="auth-story__content">
          <span class="auth-badge">创建工作台账号</span>
          <h1 id="register-story-title" class="auth-title">让监控、诊断与处置自然衔接。</h1>
          <p class="auth-description">
            创建账号后即可进入灵枢智维，接入服务器、配置组件日志监控，并在需要时让智能助手参与分析与执行。
          </p>

          <div class="auth-feature-list" aria-label="使用流程">
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="M5 5h14v14H5zM8 9h8m-8 3h5m-5 3h7" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">接入服务器</div>
                <p class="auth-feature__text">使用现有连接信息完成接入，随后即可查看运行态势。</p>
              </div>
            </article>
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="M5 18h14M7 15V9m5 6V5m5 10v-3" stroke-width="1.8" stroke-linecap="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">配置监控与诊断</div>
                <p class="auth-feature__text">聚合主机指标、组件日志和告警分析上下文。</p>
              </div>
            </article>
            <article class="auth-feature">
              <span class="auth-feature__icon" aria-hidden="true">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path d="m7 12 3 3 7-7M12 3.5 20 7v5c0 4.8-3.2 7.4-8 8.5C7.2 19.4 4 16.8 4 12V7l8-3.5Z" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <div>
                <div class="auth-feature__title">安全发起智能处置</div>
                <p class="auth-feature__text">高风险动作保持明确确认，关键步骤随时可暂停或停止。</p>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="auth-form-card" aria-labelledby="register-form-title">
        <div class="auth-form-card__inner">
          <div class="auth-form-kicker">Create Account</div>
          <h2 id="register-form-title" class="auth-form-title">创建账号</h2>
          <p class="auth-form-description">填写账号信息，开始使用智能运维工作台。</p>

          <el-form :model="form" label-position="top">
            <el-alert
              v-if="message"
              :title="message"
              :type="messageType === 'success' ? 'success' : 'error'"
              show-icon
              :closable="false"
            />

            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                type="text"
                autocomplete="username"
                placeholder="请设置用户名"
                clearable
              />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                autocomplete="new-password"
                placeholder="请输入密码"
                show-password
              />
            </el-form-item>

            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入密码"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>

            <el-button
              type="primary"
              class="auth-submit"
              :loading="loading"
              :disabled="loading"
              @click="handleRegister"
            >
              {{ loading ? '注册中...' : '创建账号' }}
            </el-button>

            <div class="auth-switch-link">
              已有账号？
              <router-link to="/login">去登录</router-link>
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
