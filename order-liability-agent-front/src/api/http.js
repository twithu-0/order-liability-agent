import axios from 'axios'
import { ElMessage } from 'element-plus'

// 全局唯一的 axios 实例，所有后端请求都从这里走
const http = axios.create({
  baseURL: 'http://localhost:8787/api',
  // 跨域请求也携带 Cookie（JSESSIONID），登录态完全靠它，不用 token
  withCredentials: true,
  // 普通接口 15 秒超时；后续 /chat/send 同步接口耗时长，调用时单独放宽
  timeout: 15000
})

// 把业务 code 挂到错误对象上，方便调用方针对 40400 等特定 code 单独处理
function bizError(message, code) {
  const err = new Error(message)
  err.code = code
  return err
}

// 响应拦截器：所有请求返回时的统一检查口（约定见 API.md 1.2）
http.interceptors.response.use(
  (resp) => {
    const { code, data, message } = resp.data
    // code=0 成功：直接把 data 交给调用方，外层不用再拆一层
    if (code === 0) {
      return data
    }
    // 40100 未登录 / 登录失效：跳登录页
    if (code === 40100) {
      window.location.href = '/login'
      return Promise.reject(bizError(message, code))
    }
    // 其他业务错误：角落弹提示条展示后端给的 message，并 reject
    ElMessage.error(message || '操作失败')
    return Promise.reject(bizError(message, code))
  },
  (error) => {
    // 网络层错误（后端没启动、断网、超时等），HTTP 都没拿到响应
    ElMessage.error('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default http
