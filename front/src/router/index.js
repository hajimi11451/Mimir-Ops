import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import MainLayout from '../layouts/MainLayout.vue'
import Dashboard from '../views/Dashboard.vue'
import DiagnosisView from '../views/DiagnosisView.vue'
import InfoListView from '../views/InfoListView.vue'
import InfoDetailView from '../views/InfoDetailView.vue'
import OpsAssistantView from '../views/OpsAssistantView.vue'
import AutoExecutionView from '../views/AutoExecutionView.vue'
import AlertSettingsView from '../views/AlertSettingsView.vue'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
    },
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/register',
      name: 'register',
      component: Register,
    },
    {
      path: '/',
      component: MainLayout,
      children: [
        {
          path: '/dashboard',
          name: 'dashboard',
          component: Dashboard,
          meta: {
            title: '总览',
            requiresAuth: true,
          },
        },
        {
          path: '/diagnosis',
          name: 'diagnosis',
          component: DiagnosisView,
          meta: {
            title: '诊断',
            requiresAuth: true,
          },
        },
        {
          path: '/ops-assistant',
          name: 'ops-assistant',
          component: OpsAssistantView,
          meta: {
            title: '灵枢助手',
            requiresAuth: true,
          },
        },
        {
          path: '/auto-execution',
          name: 'auto-execution',
          component: AutoExecutionView,
          meta: {
            title: '处置',
            requiresAuth: true,
          },
        },
        {
          path: '/info-list',
          name: 'info-list',
          component: InfoListView,
          meta: {
            title: '告警',
            requiresAuth: true,
          },
        },
        {
          path: '/info-list/:id',
          name: 'info-detail',
          component: InfoDetailView,
          meta: {
            title: '告警详情',
            requiresAuth: true,
          },
        },
        {
          path: '/alert-settings',
          name: 'alert-settings',
          component: AlertSettingsView,
          meta: {
            title: '通知',
            requiresAuth: true,
          },
        },
      ],
    },
  ],
})

// 路由守卫：检查认证状态
router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('user')
  
  // 如果路由需要认证但用户未登录，重定向到登录页
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
  } 
  // 如果已登录但访问登录页，重定向到dashboard
  else if (to.path === '/login' && isLoggedIn) {
    next('/dashboard')
  } 
  else {
    next()
  }
})

export default router
