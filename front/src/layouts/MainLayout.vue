<template>
  <div class="app-shell-lighting">
    <div class="shell-background" aria-hidden="true">
      <span class="shell-background__shape shell-background__shape--mint"></span>
      <span class="shell-background__shape shell-background__shape--sun"></span>
    </div>

    <AppShellHeader
      class="shell-header"
      :username="username"
      :page-title="pageTitle"
      :page-eyebrow="pageEyebrow"
      show-navigation-toggle
      :navigation-open="mobileNavOpen"
      @toggle-navigation="toggleMobileNavigation"
      @logout="handleLogout"
    />

    <div class="shell-body">
      <aside class="shell-sidebar custom-scrollbar" aria-label="主导航">
        <div class="sidebar-intro">
          <span class="sidebar-intro__mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M12 3v4m0 10v4M3 12h4m10 0h4M5.64 5.64l2.83 2.83m7.06 7.06 2.83 2.83m0-12.72-2.83 2.83m-7.06 7.06-2.83 2.83" stroke-width="1.8" stroke-linecap="round" />
              <circle cx="12" cy="12" r="3.25" stroke-width="1.8" />
            </svg>
          </span>
          <div>
            <p>智能运维工作台</p>
            <span>运行态势与处置入口</span>
          </div>
        </div>

        <nav class="sidebar-navigation">
          <section v-for="group in navGroups" :key="group.title" class="nav-group">
            <h2 class="nav-group__title">{{ group.title }}</h2>
            <div class="nav-group__items">
              <router-link
                v-for="item in group.items"
                :key="item.path"
                :to="item.path"
                class="nav-link"
                :class="{ 'nav-link--active': isActive(item) }"
                :aria-current="isActive(item) ? 'page' : undefined"
              >
                <span class="nav-link__icon" aria-hidden="true">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" :d="item.icon" />
                  </svg>
                </span>
                <span class="nav-link__copy">
                  <strong>{{ item.label }}</strong>
                  <small>{{ item.description }}</small>
                </span>
                <svg class="nav-link__arrow" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m9 18 6-6-6-6" />
                </svg>
              </router-link>
            </div>
          </section>
        </nav>

        <div class="sidebar-account">
          <span class="sidebar-account__dot" aria-hidden="true"></span>
          <div>
            <strong>{{ username || '管理员' }}</strong>
            <span>{{ username ? '账号已登录' : '等待登录' }}</span>
          </div>
        </div>
      </aside>

      <Transition name="drawer-mask">
        <button
          v-if="mobileNavOpen"
          type="button"
          class="mobile-nav-mask"
          aria-label="关闭导航"
          @click="mobileNavOpen = false"
        ></button>
      </Transition>

      <Transition name="drawer-panel">
        <aside
          v-if="mobileNavOpen"
          id="mobile-primary-navigation"
          class="mobile-navigation custom-scrollbar"
          aria-label="移动端主导航"
        >
          <div class="mobile-navigation__header">
            <div>
              <span>Navigation</span>
              <strong>工作台导航</strong>
            </div>
            <button type="button" aria-label="关闭导航" @click="mobileNavOpen = false">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                <path d="m6 6 12 12M18 6 6 18" stroke-width="2" stroke-linecap="round" />
              </svg>
            </button>
          </div>

          <nav class="sidebar-navigation">
            <section v-for="group in navGroups" :key="group.title" class="nav-group">
              <h2 class="nav-group__title">{{ group.title }}</h2>
              <div class="nav-group__items">
                <router-link
                  v-for="item in group.items"
                  :key="item.path"
                  :to="item.path"
                  class="nav-link"
                  :class="{ 'nav-link--active': isActive(item) }"
                  :aria-current="isActive(item) ? 'page' : undefined"
                  @click="mobileNavOpen = false"
                >
                  <span class="nav-link__icon" aria-hidden="true">
                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" :d="item.icon" />
                    </svg>
                  </span>
                  <span class="nav-link__copy">
                    <strong>{{ item.label }}</strong>
                    <small>{{ item.description }}</small>
                  </span>
                  <svg class="nav-link__arrow" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m9 18 6-6-6-6" />
                  </svg>
                </router-link>
              </div>
            </section>
          </nav>
        </aside>
      </Transition>

      <section class="shell-workspace" aria-label="页面内容">
        <main class="shell-main custom-scrollbar">
          <div :class="mainContentClass">
            <RouterView />
          </div>
        </main>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
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
const mobileNavOpen = ref(false)

const navGroups = [
  {
    title: '运行态势',
    items: [
      {
        label: '总览',
        description: '服务器与健康态势',
        path: '/dashboard',
        match: path => path === '/dashboard',
        icon: 'M4 5.5A1.5 1.5 0 0 1 5.5 4h4A1.5 1.5 0 0 1 11 5.5v4A1.5 1.5 0 0 1 9.5 11h-4A1.5 1.5 0 0 1 4 9.5v-4Zm9 0A1.5 1.5 0 0 1 14.5 4h4A1.5 1.5 0 0 1 20 5.5v4a1.5 1.5 0 0 1-1.5 1.5h-4A1.5 1.5 0 0 1 13 9.5v-4ZM4 14.5A1.5 1.5 0 0 1 5.5 13h4a1.5 1.5 0 0 1 1.5 1.5v4A1.5 1.5 0 0 1 9.5 20h-4A1.5 1.5 0 0 1 4 18.5v-4Zm9 0a1.5 1.5 0 0 1 1.5-1.5h4a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-4a1.5 1.5 0 0 1-1.5-1.5v-4Z',
      },
      {
        label: '诊断配置',
        description: '组件与日志监控',
        path: '/diagnosis',
        match: path => path === '/diagnosis',
        icon: 'M4 19.5h16M6.5 17V9.5m5 7.5V5m5 12v-4.5M5 9.5h3m2-4.5h3m2 7.5h3',
      },
    ],
  },
  {
    title: '智能处置',
    items: [
      {
        label: '灵枢助手',
        description: '咨询与命令执行',
        path: '/ops-assistant',
        match: path => path === '/ops-assistant',
        icon: 'M8 10h.01M12 10h.01M16 10h.01M9 16H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3h-4l-5 4v-4Z',
      },
      {
        label: '处置记录',
        description: '方案选择与任务发起',
        path: '/auto-execution',
        match: path => path === '/auto-execution',
        icon: 'M7 4h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm2 4h6m-6 4h6m-6 4h4',
      },
      {
        label: '告警中心',
        description: '事件筛选与分析',
        path: '/info-list',
        match: path => path.startsWith('/info-list'),
        icon: 'M12 3.5a6 6 0 0 0-6 6v3.25L4.5 16h15L18 12.75V9.5a6 6 0 0 0-6-6ZM9.75 19h4.5',
      },
    ],
  },
  {
    title: '系统',
    items: [
      {
        label: '通知设置',
        description: '告警触达与测试',
        path: '/alert-settings',
        match: path => path === '/alert-settings',
        icon: 'M3.5 7.5 12 13l8.5-5.5M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z',
      },
    ],
  },
]

const allNavItems = computed(() => navGroups.flatMap(group => group.items))
const activeItem = computed(() => allNavItems.value.find(item => item.match(route.path)))
const activeGroup = computed(() => navGroups.find(group => group.items.some(item => item.match(route.path))))

const pageTitle = computed(() => route.meta?.title || activeItem.value?.label || '总览')
const pageEyebrow = computed(() => activeGroup.value?.title || '智能运维工作台')

const mainContentClass = computed(() => {
  const isFlushRoute = route.path === '/dashboard' || route.path === '/alert-settings'

  if (isFlushRoute) return 'shell-route shell-route--flush'

  //ai辅助生成：trae客户端2026年4月6号
  return 'shell-route shell-route--padded'
})

const isActive = item => item.match(route.path)

const syncUser = () => {
  username.value = resolveStoredUsername()
}

const toggleMobileNavigation = () => {
  mobileNavOpen.value = !mobileNavOpen.value
}

const handleLogout = () => {
  localStorage.removeItem('user')
  username.value = ''
  mobileNavOpen.value = false
  router.push('/login')
}

const handleKeydown = event => {
  if (event.key === 'Escape') mobileNavOpen.value = false
}

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
  },
)

onMounted(() => {
  syncUser()
  window.addEventListener('storage', syncUser)
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('storage', syncUser)
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.app-shell-lighting {
  position: relative;
  display: flex;
  width: 100%;
  height: 100dvh;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  color: var(--app-text);
  background: var(--app-bg);
}

.shell-background {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.shell-background::before {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(114, 93, 66, 0.07) 0.8px, transparent 0.8px);
  background-size: 22px 22px;
  content: '';
  opacity: 0.28;
  mask-image: linear-gradient(to bottom, #000, transparent 48%);
}

.shell-background__shape {
  position: absolute;
  display: block;
  border-radius: 47% 53% 64% 36% / 47% 42% 58% 53%;
  opacity: 0.65;
  animation: shell-drift 22s var(--app-ease) infinite alternate;
}

.shell-background__shape--mint {
  top: -11rem;
  right: -8rem;
  width: 30rem;
  height: 25rem;
  background: rgba(25, 191, 174, 0.09);
}

.shell-background__shape--sun {
  bottom: -14rem;
  left: 4rem;
  width: 26rem;
  height: 24rem;
  background: rgba(230, 171, 32, 0.07);
  animation-direction: alternate-reverse;
}

.shell-header {
  position: relative;
  z-index: 60;
  flex: 0 0 auto;
}

.shell-body {
  position: relative;
  z-index: 1;
  display: grid;
  width: min(100%, 1832px);
  min-width: 0;
  min-height: 0;
  flex: 1;
  grid-template-columns: 232px minmax(0, 1fr);
  gap: 1rem;
  margin: 0 auto;
  padding: 1rem 1.25rem 1.25rem;
}

.shell-sidebar,
.mobile-navigation {
  color: var(--app-text-body);
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-panel);
}

.shell-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  border-radius: var(--app-radius-page);
}

.sidebar-intro {
  display: grid;
  grid-template-columns: 2.75rem minmax(0, 1fr);
  gap: 0.75rem;
  align-items: center;
  margin: 0.85rem;
  padding: 0.8rem;
  background: linear-gradient(135deg, #f6efdd, #edf8f5);
  border: 1px solid var(--app-border-soft);
  border-radius: 16px;
}

.sidebar-intro__mark {
  display: inline-flex;
  width: 2.75rem;
  height: 2.75rem;
  align-items: center;
  justify-content: center;
  color: var(--app-primary-active);
  background: var(--app-panel);
  border: 1px solid #c4e9e2;
  border-radius: 14px;
}

.sidebar-intro__mark svg {
  width: 1.35rem;
  height: 1.35rem;
}

.sidebar-intro p,
.sidebar-intro span {
  margin: 0;
}

.sidebar-intro p {
  color: var(--app-text);
  font-size: 0.8rem;
  font-weight: 800;
}

.sidebar-intro div > span {
  display: block;
  margin-top: 0.1rem;
  color: var(--app-text-muted);
  font-size: 0.68rem;
}

.sidebar-navigation {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1rem;
  padding: 0.35rem 0.75rem 1rem;
}

.nav-group__title {
  margin: 0 0 0.35rem;
  padding: 0 0.65rem;
  color: var(--app-text-faint);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.nav-group__items {
  display: grid;
  gap: 0.25rem;
}

.nav-link {
  display: grid;
  min-height: 52px;
  grid-template-columns: 2.35rem minmax(0, 1fr) 1rem;
  gap: 0.65rem;
  align-items: center;
  padding: 0.45rem 0.65rem;
  color: var(--app-text-muted);
  border: 1px solid transparent;
  border-radius: 14px;
  text-decoration: none;
  transition:
    color var(--app-motion-fast) var(--app-ease),
    background-color var(--app-motion-fast) var(--app-ease),
    border-color var(--app-motion-fast) var(--app-ease),
    transform var(--app-motion-fast) var(--app-ease);
}

.nav-link:hover {
  color: var(--app-text);
  background: #f7f2e5;
  border-color: var(--app-border-soft);
  transform: translateY(-1px);
}

.nav-link--active {
  color: var(--app-text);
  background: var(--app-primary-soft);
  border-color: #b9e7df;
}

.nav-link__icon {
  display: inline-flex;
  width: 2.35rem;
  height: 2.35rem;
  align-items: center;
  justify-content: center;
  color: var(--app-text-muted);
  background: var(--app-panel-soft);
  border-radius: 12px;
}

.nav-link__icon svg {
  width: 1.25rem;
  height: 1.25rem;
}

.nav-link--active .nav-link__icon {
  color: var(--app-primary-active);
  background: var(--app-panel);
}

.nav-link__copy {
  min-width: 0;
}

.nav-link__copy strong,
.nav-link__copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-link__copy strong {
  color: inherit;
  font-size: 0.86rem;
  font-weight: 800;
}

.nav-link__copy small {
  margin-top: 0.08rem;
  color: var(--app-text-faint);
  font-size: 0.68rem;
}

.nav-link--active .nav-link__copy small {
  color: #638c83;
}

.nav-link__arrow {
  width: 0.9rem;
  height: 0.9rem;
  opacity: 0;
  transform: translateX(-3px);
  transition: all var(--app-motion-fast) var(--app-ease);
}

.nav-link:hover .nav-link__arrow,
.nav-link--active .nav-link__arrow {
  opacity: 1;
  transform: translateX(0);
}

.sidebar-account {
  display: grid;
  grid-template-columns: 0.65rem minmax(0, 1fr);
  gap: 0.65rem;
  align-items: center;
  margin: auto 0.85rem 0.85rem;
  padding: 0.8rem 0.9rem;
  background: var(--app-panel-soft);
  border: 1px solid var(--app-border-soft);
  border-radius: 14px;
}

.sidebar-account__dot {
  width: 0.6rem;
  height: 0.6rem;
  background: var(--app-success);
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(105, 173, 56, 0.18);
}

.sidebar-account strong,
.sidebar-account div > span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-account strong {
  color: var(--app-text);
  font-size: 0.78rem;
}

.sidebar-account div > span {
  margin-top: 0.1rem;
  color: var(--app-text-muted);
  font-size: 0.68rem;
}

.shell-workspace {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-page);
  box-shadow: var(--app-shadow-panel);
}

.shell-main {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.shell-route {
  width: 100%;
  min-width: 0;
  min-height: 100%;
  margin: 0 auto;
}

.shell-route--padded {
  max-width: 1600px;
}

.shell-route--padded > :not(.app-page) {
  min-height: 100%;
  padding: clamp(1rem, 1.6vw, 1.75rem);
}

.shell-route--flush {
  max-width: 1600px;
}

.mobile-nav-mask,
.mobile-navigation {
  display: none;
}

@keyframes shell-drift {
  from {
    transform: translate3d(0, 0, 0) rotate(0deg);
  }
  to {
    transform: translate3d(1.25rem, 0.75rem, 0) rotate(3deg);
  }
}

@media (max-width: 1023px) {
  .shell-body {
    display: block;
    padding: 0.75rem 1rem 1rem;
  }

  .shell-sidebar {
    display: none;
  }

  .shell-workspace {
    width: 100%;
    height: 100%;
    border-radius: 20px;
  }

  .mobile-nav-mask {
    position: fixed;
    inset: 68px 0 0;
    z-index: 48;
    display: block;
    width: auto;
    height: auto;
    padding: 0;
    background: rgba(79, 59, 43, 0.3);
    border: 0;
  }

  .mobile-navigation {
    position: fixed;
    top: 68px;
    bottom: 0;
    left: 0;
    z-index: 49;
    display: block;
    width: min(84vw, 304px);
    overflow-x: hidden;
    overflow-y: auto;
    border-width: 0 1px 0 0;
    border-radius: 0 20px 20px 0;
    box-shadow: 18px 0 44px -30px rgba(79, 59, 43, 0.62);
  }

  .mobile-navigation__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    padding: 1rem 1rem 0.75rem;
    border-bottom: 1px solid var(--app-border-soft);
  }

  .mobile-navigation__header span,
  .mobile-navigation__header strong {
    display: block;
  }

  .mobile-navigation__header span {
    color: var(--app-primary-active);
    font-size: 0.65rem;
    font-weight: 800;
    letter-spacing: 0.14em;
    text-transform: uppercase;
  }

  .mobile-navigation__header strong {
    margin-top: 0.15rem;
    color: var(--app-text);
    font-size: 1rem;
  }

  .mobile-navigation__header button {
    display: inline-flex;
    width: 40px;
    height: 40px;
    align-items: center;
    justify-content: center;
    color: var(--app-text-body);
    background: var(--app-panel-soft);
    border: 1px solid var(--app-border-soft);
    border-radius: 12px;
  }

  .mobile-navigation__header svg {
    width: 1.25rem;
    height: 1.25rem;
  }

  .mobile-navigation .sidebar-navigation {
    padding-top: 1rem;
  }

  .drawer-mask-enter-active,
  .drawer-mask-leave-active,
  .drawer-panel-enter-active,
  .drawer-panel-leave-active {
    transition: all var(--app-motion-base) var(--app-ease);
  }

  .drawer-mask-enter-from,
  .drawer-mask-leave-to {
    opacity: 0;
  }

  .drawer-panel-enter-from,
  .drawer-panel-leave-to {
    transform: translateX(-100%);
  }
}

@media (max-width: 767px) {
  .shell-body {
    padding: 0.55rem 0.55rem 0.65rem;
  }

  .shell-workspace {
    border-radius: 18px;
  }

  .shell-route--padded > :not(.app-page) {
    padding: 0.875rem;
  }

  .mobile-nav-mask {
    inset-block-start: 64px;
  }

  .mobile-navigation {
    top: 64px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .shell-background__shape {
    animation: none;
  }

  .nav-link:hover {
    transform: none;
  }
}
</style>
