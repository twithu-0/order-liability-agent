import http from './http'

// 登录（API.md 2.2）：成功返回 User 对象，浏览器自动保存 JSESSIONID Cookie
// 失败统一 40000 账号或密码错误，由 http.js 拦截器 toast
export function login(data) {
  return http.post('/user/login', data)
}

// 注册（API.md 2.1）：成功返回 User 对象，但不自动登录，需再调 login
export function register(data) {
  return http.post('/user/register', data)
}

// 注销（API.md 2.3）：幂等，未登录调用也返回 true
export function logout() {
  return http.post('/user/logout')
}

// 获取当前登录用户（API.md 2.4）
// 成功返回 User 对象；未登录时后端返回 40100，由 http.js 拦截器统一处理
export function getLoginUser() {
  return http.get('/user/get/login')
}
