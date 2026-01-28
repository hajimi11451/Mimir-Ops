<template>
  <div class="login-container">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <h2>运维 Agent 平台</h2>
        </div>
      </template>
      
      <el-tabs v-model="activeTab" class="custom-tabs" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-width="0"
            size="large"
            class="login-form"
          >
            <el-form-item prop="username">
              <el-input 
                v-model="loginForm.username" 
                placeholder="用户名" 
                prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input 
                v-model="loginForm.password" 
                type="password" 
                placeholder="密码" 
                prefix-icon="Lock" 
                show-password 
                maxlength="14"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
                立即登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册账号" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-width="80px" 
            label-position="top"
            size="large"
            class="register-form"
            status-icon
          >
            <el-form-item label="用户名" prop="username">
              <el-input 
                v-model="registerForm.username" 
                placeholder="请输入用户名" 
                prefix-icon="User"
              />
            </el-form-item>
            
            <el-form-item label="设置密码" prop="fPassword">
              <el-input 
                v-model="registerForm.fPassword" 
                type="password" 
                placeholder="请输入密码 (14位以内)" 
                prefix-icon="Lock" 
                show-password
                maxlength="14"
              />
            </el-form-item>
            
            <el-form-item label="确认密码" prop="sPassword">
              <el-input 
                v-model="registerForm.sPassword" 
                type="password" 
                placeholder="请再次输入密码" 
                prefix-icon="CircleCheck" 
                show-password
                maxlength="14"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="success" class="submit-btn" :loading="loading" @click="handleRegister">
                立即注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, CircleCheck } from '@element-plus/icons-vue'

const router = useRouter()

// 状态控制
const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref(null)
const registerFormRef = ref(null)

// ================= 登录逻辑 =================
const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate((valid) => {
    if (valid) {
      loading.value = true
      // 模拟后端请求
      setTimeout(() => {
        console.log('登录提交的数据:', loginForm)
        ElMessage.success('登录成功')
        loading.value = false
        // 跳转到 Dashboard
        router.push('/dashboard')
      }, 1000)
    }
  })
}

// ================= 注册逻辑 =================
const registerForm = reactive({
  username: '',
  fPassword: '', // First Password
  sPassword: ''  // Second Password (Confirm)
})

// 自定义校验：检查两次密码是否一致
const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.fPassword) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  fPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 14, message: '长度在 6 到 14 个字符', trigger: 'blur' }
  ],
  sPassword: [
    { required: true, validator: validatePass2, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  await registerFormRef.value.validate((valid) => {
    if (valid) {
      loading.value = true
      
      // 【关键逻辑】构建发送给后端的数据 payload
      // 剔除 sPassword，只发送 username 和 password (fPassword)
      const payload = {
        username: registerForm.username,
        password: registerForm.fPassword
      }

      console.log('发送给后端的注册数据:', payload)

      // 模拟后端请求
      setTimeout(() => {
        ElMessage.success('注册成功，请登录')
        loading.value = false
        // 注册成功后自动切换回登录页
        activeTab.value = 'login'
        // 可选：自动填入刚注册的账号
        loginForm.username = payload.username
      }, 1000)
    }
  })
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #f0f2f5; /* 浅灰背景 */
  background-image: radial-gradient(#e1e1e1 1px, transparent 1px);
  background-size: 20px 20px;
}

.login-card {
  width: 420px;
  border-radius: 8px;
}

.card-header {
  text-align: center;
}

.card-header h2 {
  margin: 0;
  color: #303133;
  font-size: 22px;
}

.custom-tabs {
  padding: 0 10px;
}

.login-form,
.register-form {
  margin-top: 20px;
}

.submit-btn {
  width: 100%;
  letter-spacing: 1px;
}

/* 调整注册表单各个 item 的间距 */
:deep(.el-form-item--large) {
  margin-bottom: 22px;
}
</style>