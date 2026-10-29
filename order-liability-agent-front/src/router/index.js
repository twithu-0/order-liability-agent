import { createRouter, createWebHistory } from 'vue-router'
import { getLoginUser } from '../api/user'
import LoginView from '../views/LoginView.vue'
import ChatView from '../views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 根路径默认去聊天页，没登录会被下面的守卫弹回登录页
    { path: '/', redirect: '/chat' },
    { path: '/login', name: 'login', component: LoginView },
    // meta.requiresAuth 标记：这个页面需要登录才能进
    { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } }
  ]
})

// 全局路由守卫：进页面前的门卫
// 每次跳转前先问后端"我登录了吗"，40100 或请求失败一律弹回 /login
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) {
    return true
  }
  try {
    await getLoginUser()
    return true
  } catch {
    return '/login'
  }
})

export default router
