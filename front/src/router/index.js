import { createRouter, createWebHistory } from 'vue-router'
import Home from '../components/Home.vue'
import Info from '../components/ShowInfo.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/Info',
    name: 'Info',
    component: Info
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
