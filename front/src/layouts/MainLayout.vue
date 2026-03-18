<template>
  <div class="flex h-screen flex-col overflow-hidden bg-ui-bg text-ui-text">
    <AppShellHeader :username="username" @logout="handleLogout" />

    <div class="mx-auto flex min-h-0 w-full max-w-[1600px] flex-1 flex-col gap-4 px-3 py-4 sm:px-5 lg:flex-row lg:px-6">
      <aside class="shrink-0 lg:w-[92px]">
        <div class="flex rounded-[28px] border border-white/45 bg-white/18 p-2 shadow-[0_30px_70px_-42px_rgba(15,23,42,0.38)] backdrop-blur-[30px] lg:h-full lg:flex-col">
          <nav class="grid flex-1 grid-cols-3 gap-2 sm:grid-cols-6 lg:grid-cols-1">
            <router-link
              v-for="item in navItems"
              :key="item.path"
              :to="item.path"
              class="group flex min-w-0 flex-col items-center gap-2 rounded-[20px] px-2 py-3 text-center transition-all duration-200"
              :class="isActive(item)
                ? 'bg-[linear-gradient(180deg,rgba(255,255,255,0.28),rgba(255,255,255,0.12))] text-brand shadow-[inset_0_0_0_1px_rgba(96,165,250,0.16),0_18px_30px_-26px_rgba(37,99,235,0.42)] backdrop-blur-xl'
                : 'text-ui-subtext hover:bg-ui-panel hover:text-ui-text'"
            >
              <span
                class="flex h-11 w-11 items-center justify-center rounded-2xl transition-colors"
                :class="isActive(item) ? 'bg-brand/12 text-brand' : 'bg-white/14 text-ui-subtext group-hover:bg-white/24'"
              >
                <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="item.icon" />
                </svg>
              </span>
              <span class="max-w-full truncate text-xs font-medium tracking-[0.06em]">{{ item.label }}</span>
            </router-link>
          </nav>

          <div class="mt-2 hidden rounded-[22px] border border-white/16 bg-white/14 px-3 py-3 text-center text-[11px] font-medium text-ui-subtext backdrop-blur-xl lg:block">
            {{ username || '游客' }}
          </div>
        </div>
      </aside>

      <section class="flex min-h-0 flex-1 flex-col overflow-hidden">
        <div class="mb-3 flex items-center justify-between gap-3 px-1">
          <div>
            <div class="text-[11px] font-semibold uppercase tracking-[0.28em] text-ui-subtext">{{ pageEyebrow }}</div>
            <h1 class="mt-2 text-[26px] font-semibold tracking-[-0.04em] text-ui-text">{{ pageTitle }}</h1>
          </div>

          <div class="hidden items-center gap-2 rounded-full border border-white/38 bg-white/16 px-4 py-2 text-xs text-ui-subtext backdrop-blur-xl md:flex">
            <span class="inline-flex h-2 w-2 rounded-full" :class="username ? 'bg-ui-success' : 'bg-ui-warning'"></span>
            {{ username ? '账号在线' : '未登录' }}
          </div>
        </div>

        <main :class="mainContentClass">
          <RouterView />
        </main>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppShellHeader from '../components/AppShellHeader.vue'

const route = useRoute()
const router = useRouter()

const resolveStoredUsername = () => {
  try {
    const raw = localStorage.getItem('user')
    if (!raw) return ''
    return JSON.parse(raw)?.username || ''
  } catch {
    return ''
  }
}

const username = ref(resolveStoredUsername())

const navItems = [
  {
    label: '总览',
    path: '/dashboard',
    match: path => path === '/dashboard',
    icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    label: '诊断',
    path: '/diagnosis',
    match: path => path === '/diagnosis',
    icon: 'M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z',
  },
  {
    label: '助手',
    path: '/ops-assistant',
    match: path => path === '/ops-assistant',
    icon: 'M8 10h.01M12 10h.01M16 10h.01M9 16H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-5l-4 4v-4z',
  },
  {
    label: '处置',
    path: '/auto-execution',
    match: path => path === '/auto-execution',
    icon: 'M9 12h6m-6 4h6M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2z',
  },
  {
    label: '告警',
    path: '/info-list',
    match: path => path.startsWith('/info-list'),
    icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h8a2 2 0 012 2v14a2 2 0 01-2 2z',
  },
  {
    label: '通知',
    path: '/alert-settings',
    match: path => path === '/alert-settings',
    icon: 'M3 8l7.89 4.26a2 2 0 002.22 0L21 8m-18 8h18a2 2 0 002-2V8a2 2 0 00-2-2H3a2 2 0 00-2 2v6a2 2 0 002 2z',
  },
]

const pageTitle = computed(() => route.meta?.title || '总览')

const pageEyebrow = computed(() => route.path === '/dashboard' ? 'Overview' : 'Workspace')

const mainContentClass = computed(() => {
  const baseClass = 'flex-1 min-h-0 rounded-[30px] border border-white/42 bg-white/18 shadow-[0_30px_70px_-42px_rgba(15,23,42,0.35)] backdrop-blur-[30px]'

  if (route.path === '/dashboard') {
    return `${baseClass} overflow-hidden`
  }

  if (route.path === '/alert-settings') {
    return `${baseClass} overflow-hidden`
  }

  return `${baseClass} overflow-y-auto p-4 sm:p-5 lg:p-6 custom-scrollbar`
})

const isActive = item => item.match(route.path)

const syncUser = () => {
  username.value = resolveStoredUsername()
}

const handleLogout = () => {
  localStorage.removeItem('user')
  username.value = ''
  router.push('/login')
}

onMounted(() => {
  syncUser()
  window.addEventListener('storage', syncUser)
})

onUnmounted(() => {
  window.removeEventListener('storage', syncUser)
})
</script>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #c7d6ea;
  border-radius: 999px;
}
</style>

