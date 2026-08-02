<template>
  <div v-loading="loadingContact" class="app-page notification-page">
    <section class="page-hero notification-hero">
      <div>
        <div class="section-eyebrow">Notification channel</div>
        <h2>通知设置</h2>
        <p>配置告警接收邮箱，验证从风险复检到邮件投递的完整链路。</p>
      </div>
      <span class="status-pill" :class="savedEmail ? 'status-pill--success' : 'status-pill--warning'">{{ contactStatusText }}</span>
    </section>

    <div class="notification-body-grid">
      <section class="section-card notification-form-panel">
        <div class="section-header">
          <div><div class="section-eyebrow">Recipient</div><h3>收件邮箱</h3><p>每个登录用户维护自己的告警收件地址。</p></div>
          <span class="account-chip">{{ username || '未登录' }}</span>
        </div>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent
        >
          <el-form-item label="通知邮箱" prop="email">
            <el-input
              v-model="form.email"
              clearable
              placeholder="例如：ops-team@qq.com"
              @keyup.enter="handleSave"
            >
              <template #prefix>
                <el-icon><Message /></el-icon>
              </template>
            </el-input>
            <div class="field-help">留空保存可清空当前邮箱；建议使用运维团队公共邮箱。</div>
          </el-form-item>

          <el-form-item>
            <div class="form-actions">
              <el-button type="primary" :loading="saving" @click="handleSave">
                <el-icon class="mr-1"><Check /></el-icon>
                保存设置
              </el-button>
              <el-button :loading="testing" @click="handleTestMail">
                <el-icon class="mr-1"><Promotion /></el-icon>
                测试邮件
              </el-button>
              <el-button :disabled="!form.email" @click="handleClear">
                <el-icon class="mr-1"><Delete /></el-icon>
                清空输入
              </el-button>
              <el-button :disabled="!savedEmail" @click="handleRestoreSaved">
                <el-icon class="mr-1"><RefreshLeft /></el-icon>
                恢复
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <div class="delivery-preview soft-panel">
          <div class="delivery-icon">✉</div>
          <div>
            <span>本次测试对象</span>
            <strong>{{ effectiveEmail || '尚未指定收件邮箱' }}</strong>
            <small>{{ lastTestMessage || '保存后可发送一封测试邮件验证链路。' }}</small>
          </div>
        </div>
      </section>

      <section class="section-card notification-rules-panel">
        <div class="section-header"><div><div class="section-eyebrow">Delivery policy</div><h3>通知规则</h3><p>邮件是风险提醒，不替代人工检查与处置确认。</p></div></div>
        <div class="rule-list">
          <div class="rule-item"><span>01</span><div><strong>连续复检</strong><p>同一问题连续两次判定为高风险后才会触发邮件。</p></div></div>
          <div class="rule-item"><span>02</span><div><strong>30 分钟冷却</strong><p>相同风险进入冷却期，避免重复告警轰炸。</p></div></div>
          <div class="rule-item"><span>03</span><div><strong>人工确认</strong><p>收到通知后请及时检查服务器，高风险问题仍需人工决策。</p></div></div>
        </div>
        <div class="saved-contact">
          <span>当前已保存邮箱</span>
          <strong>{{ savedEmail || '暂未保存' }}</strong>
        </div>
        <el-alert :type="savedEmail ? 'success' : 'warning'" :closable="false" show-icon :title="savedEmail ? '通知链路已配置，可发送测试邮件。' : '未配置邮箱，当前不会自动发送通知。'" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAlertContact, saveAlertContact, sendAlertTestMail } from '../api/alert'

const formRef = ref(null)
const loadingContact = ref(false)
const saving = ref(false)
const testing = ref(false)
const savedEmail = ref('')
const lastTestMessage = ref('')

const form = reactive({
  email: '',
})

const rules = {
  email: [
    {
      validator: (_rule, value, callback) => {
        const text = String(value || '').trim()
        if (!text) {
          callback()
          return
        }

        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
        if (!emailPattern.test(text)) {
          callback(new Error('请输入有效的邮箱地址'))
          return
        }

        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
}

const username = computed(() => {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    return JSON.parse(raw)?.username || ''
  } catch (error) {
    return ''
  }
})

const effectiveEmail = computed(() => {
  return String(form.email || '').trim() || savedEmail.value || ''
})

const contactStatusText = computed(() => (
  savedEmail.value ? '已配置收件邮箱' : '未配置收件邮箱'
))

const contactStatusType = computed(() => (
  savedEmail.value ? 'success' : 'info'
))

async function validateEmailFieldIfNeeded() {
  const text = String(form.email || '').trim()
  if (!text || !formRef.value) {
    return true
  }

  try {
    await formRef.value.validateField('email')
    return true
  } catch {
    return false
  }
}

async function loadContact() {
  if (!username.value) return

  loadingContact.value = true
  try {
    const data = await getAlertContact(username.value)
    savedEmail.value = String(data?.email || '').trim()
    form.email = savedEmail.value
  } catch (error) {
    ElMessage.error(error?.message || '读取紧急联系人邮箱失败')
  } finally {
    loadingContact.value = false
  }
}

async function handleSave() {
  if (!username.value) {
    ElMessage.error('未识别当前用户')
    return
  }

  const valid = await validateEmailFieldIfNeeded()
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const data = await saveAlertContact({
      username: username.value,
      email: String(form.email || '').trim(),
    })
    savedEmail.value = String(data?.email || '').trim()
    form.email = savedEmail.value
    lastTestMessage.value = ''
    ElMessage.success(savedEmail.value ? '通知邮箱已保存' : '通知邮箱已清空')
  } catch (error) {
    ElMessage.error(error?.message || '保存通知邮箱失败')
  } finally {
    saving.value = false
  }
}

async function handleTestMail() {
  if (!username.value) {
    ElMessage.error('未识别当前用户')
    return
  }

  const targetEmail = effectiveEmail.value
  if (!targetEmail) {
    ElMessage.warning('请先输入邮箱，或先保存一个通知邮箱')
    return
  }

  const valid = await validateEmailFieldIfNeeded()
  if (!valid) {
    return
  }

  testing.value = true
  try {
    await sendAlertTestMail({
      username: username.value,
      email: String(form.email || '').trim(),
    })
    lastTestMessage.value = `测试邮件已发送到 ${targetEmail}`
    ElMessage.success('测试邮件已发送，请检查收件箱')
  } catch (error) {
    const message = String(error?.message || '')
    if (message.includes('timeout')) {
      lastTestMessage.value = `请求等待超时，邮件可能已发送到 ${targetEmail}`
      ElMessage.warning('请求等待超时，邮件可能已经发出，请检查收件箱')
    } else {
      ElMessage.error(error?.message || '测试邮件失败')
    }
  } finally {
    testing.value = false
  }
}

function handleClear() {
  form.email = ''
  formRef.value?.clearValidate('email')
}

function handleRestoreSaved() {
  form.email = savedEmail.value
  formRef.value?.clearValidate('email')
}

onMounted(() => {
  loadContact()
})
</script>

<style scoped>
.notification-page{display:grid;gap:1.25rem}.notification-hero{background:linear-gradient(135deg,#fffdf6 0%,#edf8ec 100%)}.notification-body-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(19rem,.75fr);gap:1.25rem;align-items:start}.notification-form-panel,.notification-rules-panel{min-width:0}.account-chip{display:inline-flex;align-items:center;border-radius:999px;background:#f3eddb;color:#725d42;padding:.45rem .75rem;font-size:.76rem;font-weight:750}.field-help{margin-top:.45rem;color:var(--color-ui-subtext);font-size:.75rem}.form-actions{display:flex;flex-wrap:wrap;gap:.7rem}.delivery-preview{display:flex;align-items:center;gap:.9rem;margin-top:.7rem;padding:1rem}.delivery-icon{display:grid;width:3rem;height:3rem;flex:0 0 auto;place-items:center;border-radius:17px;background:#e4f7f3;color:#158f84;font-size:1.25rem}.delivery-preview>div:last-child{display:grid;gap:.18rem;min-width:0}.delivery-preview span,.delivery-preview small{color:var(--color-ui-subtext);font-size:.72rem}.delivery-preview strong{overflow:hidden;color:var(--color-ui-text);font-size:.88rem;text-overflow:ellipsis}.rule-list{display:grid;gap:.75rem}.rule-item{display:grid;grid-template-columns:auto 1fr;gap:.8rem;padding:.9rem;border:1px solid var(--color-ui-border);border-radius:17px;background:#fffdf7}.rule-item>span{display:grid;width:2.25rem;height:2.25rem;place-items:center;border-radius:13px;background:#fff4d6;color:#ad7c08;font-size:.72rem;font-weight:900}.rule-item strong{color:var(--color-ui-text);font-size:.86rem}.rule-item p{margin:.2rem 0 0;color:var(--color-ui-subtext);font-size:.75rem;line-height:1.55}.saved-contact{display:grid;gap:.2rem;margin:1rem 0;padding:.9rem 1rem;border-radius:16px;background:#f3eddb}.saved-contact span{color:var(--color-ui-subtext);font-size:.72rem}.saved-contact strong{overflow-wrap:anywhere;color:var(--color-ui-text);font-size:.86rem}
@media(max-width:900px){.notification-body-grid{grid-template-columns:1fr}}
</style>

