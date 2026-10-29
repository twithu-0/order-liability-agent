import http from './http'

// 创建对话（API.md 3.1）：返回 chatId；空对话不落库，发首条消息后才进列表
export function createChat() {
  return http.post('/chat/create')
}

// 对话列表（API.md 3.2）：按最近活跃倒序
export function getChatList() {
  return http.get('/chat/list')
}

// 对话历史（API.md 3.3）：时间正序，USER / ASSISTANT 交替
export function getChatHistory(chatId) {
  return http.get('/chat/history', { params: { chatId } })
}

// 删除对话（API.md 3.6）：chatId 走 query 参数，不可恢复
export function deleteChat(chatId) {
  return http.post('/chat/delete', null, { params: { chatId } })
}
