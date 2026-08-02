<template>
  <header class="app-header">
    <div class="app-header__inner">
      <div class="app-header__leading">
        <button
          v-if="showNavigationToggle"
          type="button"
          class="navigation-trigger"
          :aria-expanded="navigationOpen"
          aria-controls="mobile-primary-navigation"
          :aria-label="navigationOpen ? '关闭导航' : '打开导航'"
          @click="$emit('toggle-navigation')"
        >
          <svg v-if="!navigationOpen" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M4 7h16M4 12h16M4 17h16" stroke-width="2" stroke-linecap="round" />
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="m6 6 12 12M18 6 6 18" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>

        <router-link to="/dashboard" class="brand-link" aria-label="前往灵枢智维总览">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M13.4 2.8 5.2 13h5.9l-.5 8.2L18.8 11h-5.9l.5-8.2Z" stroke-width="1.9" stroke-linejoin="round" />
            </svg>
          </span>
          <span class="brand-copy">
            <strong>灵枢智维</strong>
            <small>AIOps Console</small>
          </span>
        </router-link>

        <div v-if="pageTitle && !showAuthLinks" class="header-context">
          <span class="header-context__divider" aria-hidden="true"></span>
          <span class="header-context__copy">
            <small>{{ pageEyebrow }}</small>
            <strong>{{ pageTitle }}</strong>
          </span>
        </div>
      </div>

      <div class="app-header__actions">
        <template v-if="showAuthLinks">
          <span class="header-status status-pill" data-status="info">安全访问</span>
          <router-link
            to="/login"
            class="auth-navigation-link"
            :class="{ 'auth-navigation-link--active': route.path === '/login' }"
          >
            登录
          </router-link>
          <router-link
            to="/register"
            class="auth-navigation-link auth-navigation-link--outlined"
            :class="{ 'auth-navigation-link--active': route.path === '/register' }"
          >
            注册
          </router-link>
        </template>

        <template v-else>
          <span class="header-status status-pill" :data-status="username ? 'success' : 'warning'">
            {{ username ? '账号在线' : '未登录' }}
          </span>

          <details class="user-menu">
            <summary aria-label="打开用户菜单">
              <span class="user-avatar" aria-hidden="true">{{ initials }}</span>
              <span class="user-summary-copy">
                <strong>{{ username || '管理员' }}</strong>
                <small>用户菜单</small>
              </span>
              <svg class="user-menu__chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                <path d="m7 10 5 5 5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </summary>

            <div class="user-menu__panel">
              <div class="user-menu__identity">
                <span class="user-avatar user-avatar--large" aria-hidden="true">{{ initials }}</span>
                <div>
                  <strong>{{ username || '管理员' }}</strong>
                  <span>{{ username ? '当前账号已登录' : '当前未登录' }}</span>
                </div>
              </div>
              <button type="button" class="user-menu__logout" @click="$emit('logout')">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
                  <path d="M10 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h4m4-4 3-3-3-3m3 3H9" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                退出登录
              </button>
            </div>
          </details>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  username: { type: String, default: '' },
  showAuthLinks: { type: Boolean, default: false },
  showNavigationToggle: { type: Boolean, default: false },
  navigationOpen: { type: Boolean, default: false },
  pageTitle: { type: String, default: '' },
  pageEyebrow: { type: String, default: '智能运维工作台' },
})

defineEmits(['logout', 'toggle-navigation'])

const route = useRoute()

const initials = computed(() => {
  const text = String(props.username || '').trim()
  return text ? text.charAt(0).toUpperCase() : 'A'
})
</script>

<style scoped>
.app-header {
  width: 100%;
  height: 68px;
  color: var(--app-text);
  background: var(--app-panel);
  border-bottom: 1px solid var(--app-border);
  box-shadow: 0 8px 26px -24px rgba(79, 59, 43, 0.5);
}

.app-header__inner {
  display: flex;
  width: min(100%, 1832px);
  height: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin: 0 auto;
  padding: 0 1.25rem;
}

.app-header__leading,
.app-header__actions,
.brand-link,
.header-context,
.user-menu > summary,
.user-menu__identity {
  display: flex;
  align-items: center;
}

.app-header__leading {
  min-width: 0;
  gap: 0.75rem;
}

.app-header__actions {
  flex: 0 0 auto;
  gap: 0.65rem;
}

.navigation-trigger {
  display: none;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: var(--app-text-body);
  background: var(--app-panel-soft);
  border: 1px solid var(--app-border-soft);
  border-radius: 13px;
}

.navigation-trigger svg,
.brand-mark svg {
  width: 1.3rem;
  height: 1.3rem;
}

.brand-link {
  min-width: 0;
  gap: 0.7rem;
  color: inherit;
  text-decoration: none;
}

.brand-mark {
  display: inline-flex;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: var(--app-primary);
  border: 1px solid var(--app-primary-active);
  border-radius: 14px;
  box-shadow: 0 3px 0 var(--app-primary-active);
  transition: all var(--app-motion-fast) var(--app-ease);
}

.brand-link:hover .brand-mark {
  box-shadow: 0 4px 0 var(--app-primary-active);
  transform: translateY(-1px);
}

.brand-copy,
.header-context__copy,
.user-summary-copy {
  min-width: 0;
}

.brand-copy strong,
.brand-copy small,
.header-context__copy small,
.header-context__copy strong,
.user-summary-copy strong,
.user-summary-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-copy strong {
  color: var(--app-text);
  font-size: 0.95rem;
  font-weight: 900;
}

.brand-copy small {
  color: var(--app-text-muted);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.17em;
  text-transform: uppercase;
}

.header-context {
  min-width: 0;
  gap: 1rem;
}

.header-context__divider {
  width: 1px;
  height: 34px;
  background: var(--app-border-soft);
}

.header-context__copy small {
  color: var(--app-primary-active);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.header-context__copy strong {
  color: var(--app-text);
  font-size: 1rem;
  font-weight: 800;
}

.header-status {
  min-height: 30px;
}

.auth-navigation-link {
  display: inline-flex;
  min-width: 58px;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  padding: 0.45rem 0.9rem;
  color: var(--app-text-body);
  border: 1px solid transparent;
  border-radius: 13px;
  font-size: 0.86rem;
  font-weight: 800;
  text-decoration: none;
  transition: all var(--app-motion-fast) var(--app-ease);
}

.auth-navigation-link:hover {
  color: var(--app-primary-active);
  background: var(--app-primary-soft);
}

.auth-navigation-link--outlined {
  background: var(--app-panel-strong);
  border-color: var(--app-border);
}

.auth-navigation-link--active {
  color: #fff;
  background: var(--app-primary);
  border-color: var(--app-primary-active);
  box-shadow: 0 3px 0 var(--app-primary-active);
}

.user-menu {
  position: relative;
}

.user-menu > summary {
  min-width: 154px;
  min-height: 44px;
  gap: 0.65rem;
  padding: 0.3rem 0.55rem 0.3rem 0.35rem;
  color: var(--app-text-body);
  background: var(--app-panel-strong);
  border: 1px solid var(--app-border);
  border-radius: 14px;
  cursor: pointer;
  list-style: none;
}

.user-menu > summary::-webkit-details-marker {
  display: none;
}

.user-avatar {
  display: inline-flex;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: var(--app-primary-active);
  background: var(--app-primary-soft);
  border: 1px solid #bee8e1;
  border-radius: 12px;
  font-size: 0.82rem;
  font-weight: 900;
}

.user-avatar--large {
  width: 42px;
  height: 42px;
  border-radius: 14px;
}

.user-summary-copy {
  flex: 1;
}

.user-summary-copy strong,
.user-summary-copy small {
  max-width: 7rem;
}

.user-summary-copy strong {
  color: var(--app-text);
  font-size: 0.8rem;
}

.user-summary-copy small {
  color: var(--app-text-muted);
  font-size: 0.65rem;
}

.user-menu__chevron {
  width: 1rem;
  height: 1rem;
  color: var(--app-text-faint);
  transition: transform var(--app-motion-fast) var(--app-ease);
}

.user-menu[open] .user-menu__chevron {
  transform: rotate(180deg);
}

.user-menu__panel {
  position: absolute;
  top: calc(100% + 0.55rem);
  right: 0;
  z-index: 80;
  width: 250px;
  padding: 0.75rem;
  color: var(--app-text-body);
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: 16px;
  box-shadow: 0 20px 48px -28px rgba(79, 59, 43, 0.58);
}

.user-menu__identity {
  gap: 0.75rem;
  padding: 0.4rem 0.35rem 0.8rem;
  border-bottom: 1px solid var(--app-border-soft);
}

.user-menu__identity strong,
.user-menu__identity span {
  display: block;
}

.user-menu__identity strong {
  color: var(--app-text);
  font-size: 0.86rem;
}

.user-menu__identity span {
  color: var(--app-text-muted);
  font-size: 0.72rem;
}

.user-menu__logout {
  display: flex;
  width: 100%;
  min-height: 40px;
  align-items: center;
  gap: 0.65rem;
  margin-top: 0.55rem;
  padding: 0.55rem 0.7rem;
  color: var(--app-danger);
  background: #fbeaea;
  border: 1px solid #efc6c6;
  border-radius: 12px;
  font-weight: 800;
}

.user-menu__logout svg {
  width: 1.15rem;
  height: 1.15rem;
}

@media (max-width: 1023px) {
  .navigation-trigger {
    display: inline-flex;
  }

  .header-context__divider,
  .brand-copy small,
  .header-context__copy small {
    display: none;
  }
}

@media (max-width: 767px) {
  .app-header {
    height: 64px;
  }

  .app-header__inner {
    gap: 0.5rem;
    padding: 0 0.75rem;
  }

  .brand-copy,
  .header-status,
  .user-summary-copy,
  .user-menu__chevron {
    display: none;
  }

  .header-context__copy strong {
    max-width: 8rem;
    font-size: 0.92rem;
  }

  .user-menu > summary {
    min-width: 42px;
    min-height: 42px;
    justify-content: center;
    padding: 0.2rem;
  }

  .auth-navigation-link {
    min-width: 50px;
    min-height: 38px;
    padding-inline: 0.7rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .brand-link:hover .brand-mark {
    transform: none;
  }
}
</style>
