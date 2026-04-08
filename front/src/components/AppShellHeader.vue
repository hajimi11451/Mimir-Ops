<template>
  <header class="relative z-40 border-b border-white/42 bg-white/20 shadow-[0_1.25rem_2.625rem_-2rem_rgba(15,23,42,0.34)] backdrop-blur-[1.625rem]">
    <div class="pointer-events-none absolute inset-x-0 bottom-0 h-px bg-gradient-to-r from-transparent via-brand/20 to-transparent"></div>

    <div class="mx-auto flex h-[4.25rem] w-full max-w-[100rem] items-center justify-between px-4 sm:px-6 lg:px-8">
      <router-link
        to="/dashboard"
        class="group flex min-w-0 items-center gap-3 transition-opacity hover:opacity-85"
      >
        <div class="flex h-9 w-9 items-center justify-center rounded-2xl bg-[linear-gradient(135deg,#2563eb,#60a5fa)] shadow-[0_0.75rem_1.75rem_-1.125rem_rgba(37,99,235,0.85)] shrink-0">
          <svg class="h-5 w-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
        </div>

        <div class="min-w-0">
          <div class="truncate text-sm font-semibold tracking-[0.02em] text-ui-text">灵枢智维</div>
          <div class="truncate text-[0.6875rem] uppercase tracking-[0.24em] text-ui-subtext">AIOps Console</div>
        </div>
      </router-link>

      <div class="flex items-center gap-2 sm:gap-3">
        <template v-if="showAuthLinks">
          <span class="hidden rounded-full border border-white/26 bg-white/16 px-3 py-1 text-[0.6875rem] font-medium text-ui-subtext backdrop-blur-xl sm:inline-flex">
            未登录
          </span>
          <router-link
            to="/login"
            class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
            :class="route.path === '/login' ? 'bg-brand text-white shadow-[0_0.75rem_1.5rem_-1.125rem_rgba(37,99,235,1)]' : 'text-ui-subtext hover:bg-white/18 hover:text-ui-text'"
          >
            登录
          </router-link>
          <router-link
            to="/register"
            class="rounded-full border border-ui-border px-4 py-2 text-sm font-medium transition-colors"
            :class="route.path === '/register' ? 'border-brand bg-brand/8 text-brand' : 'bg-white/14 text-ui-subtext backdrop-blur-xl hover:border-brand/30 hover:text-brand'"
          >
            注册
          </router-link>
        </template>

        <template v-else>
          <div class="hidden items-center gap-2 rounded-full border border-white/26 bg-white/16 px-3 py-2 backdrop-blur-xl sm:flex">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand/12 text-sm font-semibold text-brand shrink-0">
              {{ initials }}
            </div>
            <div class="min-w-0">
              <div class="truncate text-sm font-medium text-ui-text">{{ username || '管理员' }}</div>
              <div class="text-[0.6875rem] text-ui-subtext">已登录</div>
            </div>
          </div>

          <button
            type="button"
            class="rounded-full border border-white/26 bg-white/12 px-4 py-2 text-sm font-medium text-ui-subtext backdrop-blur-xl transition-colors hover:border-brand/35 hover:text-brand"
            @click="$emit('logout')"
          >
            退出
          </button>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  username: {
    type: String,
    default: '',
  },
  showAuthLinks: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['logout'])

const route = useRoute()

const initials = computed(() => {
  const text = String(props.username || '').trim()
  if (!text) return 'A'
  return text.charAt(0).toUpperCase()
})
</script>

