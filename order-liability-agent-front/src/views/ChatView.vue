<template>
  <div class="chat-page">
    <!-- 全局顶栏：纯白、固定、与下方明显分界，标题左对齐 -->
    <header class="app-header">
      <span class="app-title">订单判责Agent</span>
      <div class="header-right">
        <span class="user-name">{{ userName }}</span>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div class="chat-body">
    <!-- 左侧边栏：新对话按钮 + 对话列表 -->
    <aside class="sidebar">
      <el-button type="primary" class="new-chat-btn" @click="handleNewChat">新对话</el-button>
      <div v-loading="listLoading" class="chat-list">
        <!-- 一律用字符串 chatId 作 key，不碰数字 id -->
        <div
          v-for="chat in chats"
          :key="chat.chatId"
          class="chat-item"
          :class="{ active: chat.chatId === currentChatId }"
          @click="openChat(chat.chatId)"
        >
          <span class="chat-title">{{ chat.title }}</span>
          <el-button link type="danger" @click.stop="handleDelete(chat)">删除</el-button>
        </div>
        <el-empty v-if="!listLoading && chats.length === 0" description="暂无对话" :image-size="60" />
      </div>
    </aside>

    <!-- 右侧：消息区 + 底部输入框 -->
    <main class="main">
      <div v-loading="historyLoading" class="messages">
        <!-- 选中了对话（含待创建的新对话）：渲染气泡，USER 右 / ASSISTANT 左 -->
        <template v-if="currentChatId">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            class="bubble-row"
            :class="msg.role === 'USER' ? 'row-right' : 'row-left'"
          >
            <!-- AI 机器人头像（左侧） -->
            <svg v-if="msg.role !== 'USER'" class="avatar" viewBox="0 0 40 40" aria-hidden="true">
              <circle cx="20" cy="20" r="20" fill="#ecf5ff" />
              <line x1="20" y1="8" x2="20" y2="12" stroke="#409eff" stroke-width="2" />
              <circle cx="20" cy="7" r="2" fill="#409eff" />
              <rect x="10" y="12" width="20" height="16" rx="4" fill="#409eff" />
              <rect x="6.5" y="17" width="3" height="6" rx="1.5" fill="#409eff" />
              <rect x="30.5" y="17" width="3" height="6" rx="1.5" fill="#409eff" />
              <circle cx="15.5" cy="19" r="2.2" fill="#fff" />
              <circle cx="24.5" cy="19" r="2.2" fill="#fff" />
              <rect x="15" y="23.5" width="10" height="2" rx="1" fill="#fff" />
            </svg>
            <div class="bubble" :class="msg.role === 'USER' ? 'bubble-user' : 'bubble-agent'">
              <!-- pending 是流式等待期的空 ASSISTANT 气泡，首个 delta 或 done 后消失 -->
              <template v-if="msg.pending">
                请稍等，正在核实证据<span class="loading-dots"><span>.</span><span>.</span><span>.</span></span>
              </template>
              <template v-else>{{ msg.content }}</template>
            </div>
            <!-- 用户灰色人头头像（右侧） -->
            <svg v-if="msg.role === 'USER'" class="avatar" viewBox="0 0 40 40" aria-hidden="true">
              <circle cx="20" cy="20" r="20" fill="#909399" />
              <circle cx="20" cy="15" r="6.5" fill="#fff" />
              <path d="M8 32c1.5-7 6.5-10 12-10s10.5 3 12 10" fill="#fff" />
            </svg>
          </div>
          <el-empty
            v-if="!historyLoading && messages.length === 0"
            description="暂无消息，发出首条消息后才会创建对话"
            :image-size="60"
          />
        </template>
        <!-- 没选对话：待创建态 -->
        <el-empty v-else description="未选择对话，点左侧新对话开始" :image-size="80" />
      </div>

      <footer class="input-bar">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="4"
          resize="none"
          :disabled="sending"
          placeholder="输入订单问题，Enter 发送，Shift+Enter 换行"
          @keydown.enter.exact.prevent="handleSend"
        />
        <el-button
          type="primary"
          class="send-btn"
          :disabled="sending || inputText.trim().length === 0"
          @click="handleSend"
        >
          发送
        </el-button>
      </footer>
    </main>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLoginUser, logout } from '../api/user'
import { createChat, deleteChat, getChatHistory, getChatList } from '../api/chat'
import { sendStream } from '../api/stream'

const router = useRouter()

// 状态全部放在本组件里，不引入 Pinia
const userName = ref('')
const chats = ref([]) // 侧边栏列表
const currentChatId = ref('') // 空字符串 = 待创建态
const messages = ref([]) // 当前对话的历史消息
const inputText = ref('')
const sending = ref(false) // 发送期间禁用输入和按钮，防重复发送
const listLoading = ref(false)
const historyLoading = ref(false)

onMounted(async () => {
  // 顶栏用户名；40100 时拦截器会跳登录页
  try {
    const user = await getLoginUser()
    userName.value = user.userName
  } catch {
    // 拦截器已处理
  }
  await initChats()
})

// 进页面 / 刷新：拉列表，默认选中第一条；无对话则待创建态
async function initChats() {
  listLoading.value = true
  try {
    chats.value = await getChatList()
    if (chats.value.length > 0) {
      await openChat(chats.value[0].chatId)
    } else {
      currentChatId.value = ''
      messages.value = []
    }
  } catch {
    // 拦截器已 toast
  } finally {
    listLoading.value = false
  }
}

// 刷新列表；失败时保留旧列表，避免一次网络抖动清空侧边栏
async function refreshList() {
  chats.value = await getChatList().catch(() => chats.value)
}

// 打开对话：拉历史渲染；只有 USER 没有 ASSISTANT 的轮次也照常渲染
async function openChat(chatId) {
  if (sending.value) return // 发送中禁止切换，避免气泡串对话
  currentChatId.value = chatId
  messages.value = []
  historyLoading.value = true
  try {
    messages.value = await getChatHistory(chatId)
  } catch (err) {
    // 40400 对话不存在或已删除：拦截器已 toast，这里刷新一次列表并兜底
    if (err.code === 40400) {
      await refreshList()
      const fallback = chats.value.find((c) => c.chatId !== chatId)
      if (fallback) {
        currentChatId.value = fallback.chatId
        messages.value = await getChatHistory(fallback.chatId).catch(() => [])
      } else {
        currentChatId.value = ''
        messages.value = []
      }
    }
  } finally {
    historyLoading.value = false
  }
}

// 新对话：拿 chatId、清空消息区
// API.md 3.1：空对话不落库、不进列表，属正常行为，所以这里不往 chats 里加
async function handleNewChat() {
  if (sending.value) return // 发送中禁止切换，避免气泡串对话
  try {
    const chat = await createChat()
    currentChatId.value = chat.chatId
    messages.value = []
  } catch {
    // 拦截器已 toast
  }
}

// 删除：二次确认 -> 调删除 -> 刷新列表；删的是当前对话就回待创建态
async function handleDelete(chat) {
  if (sending.value) return // 发送中禁止删除，避免恢复 history 时对话已没了
  try {
    await ElMessageBox.confirm(`删除对话「${chat.title}」？删除后不可恢复。`, '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return // 用户点了取消
  }
  try {
    await deleteChat(chat.chatId)
  } catch (err) {
    // 40400 说明已经没了，同样走下面的刷新列表；其他错误中止
    if (err.code !== 40400) {
      return
    }
  }
  const wasCurrent = chat.chatId === currentChatId.value
  await refreshList()
  if (wasCurrent) {
    currentChatId.value = ''
    messages.value = []
  }
}

// 退出登录：POST /user/logout 后跳 /login
async function handleLogout() {
  try {
    await logout()
  } catch {
    // 失败也去登录页，拦截器已 toast
  }
  router.push('/login')
}

// 发送：本地先追加 USER 气泡 + 空 ASSISTANT 气泡，再走流式接口
async function handleSend() {
  if (sending.value) return
  const text = inputText.value.trim()
  if (!text) return
  if (text.length > 2000) {
    ElMessage.warning('消息最长 2000 字')
    return
  }
  sending.value = true
  inputText.value = ''
  // 当前没有 chatId 先按 3.1 创建
  if (!currentChatId.value) {
    try {
      const chat = await createChat()
      currentChatId.value = chat.chatId
    } catch {
      sending.value = false
      return
    }
  }
  const chatId = currentChatId.value
  messages.value.push({ role: 'USER', content: text })
  messages.value.push({ role: 'ASSISTANT', content: '', pending: true })
  // 取 reactive 代理出来的对象再改，才能触发视图更新
  const agentMsg = messages.value[messages.value.length - 1]
  let doneOk = false
  let broken = false
  await sendStream(chatId, text, {
    onDelta(data) {
      // 增量追加，打字机效果
      agentMsg.pending = false
      agentMsg.content += data
    },
    onDone(data) {
      // done 是权威值：整体替换而不是追加，降级/守卫拦截都靠它收敛
      agentMsg.pending = false
      agentMsg.content = data
      doneOk = true
    },
    onError() {
      broken = true
    }
  })
  if (doneOk) {
    sending.value = false
    // done 后刷新侧边栏：标题和排序可能变化
    await refreshList()
  } else if (broken) {
    await recoverFromHistory(chatId, agentMsg)
    sending.value = false
  }
}

// 断流兜底：调一次 history 恢复该对话最终状态，不用同步接口重发
async function recoverFromHistory(chatId, agentMsg) {
  try {
    messages.value = await getChatHistory(chatId)
  } catch {
    // history 也失败：结束 loading，气泡给个提示，别卡在"正在核实证据…"
    agentMsg.pending = false
    if (!agentMsg.content) {
      agentMsg.content = '连接中断，恢复失败，请刷新页面重试'
    }
  }
}
</script>

<style scoped>
.chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.app-header {
  flex: none;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.app-title {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chat-body {
  flex: 1;
  display: flex;
  min-height: 0;
}
.sidebar {
  width: 260px;
  border-right: 1px solid #e4e7ed;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: #f5f7fa;
}
.new-chat-btn {
  width: 100%;
}
.chat-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.chat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}
.chat-item:hover {
  border-color: #c6e2ff;
}
.chat-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}
.chat-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.user-name {
  font-weight: 600;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.avatar {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 50%;
}
.row-right {
  justify-content: flex-end;
}
.row-left {
  justify-content: flex-start;
}
.bubble {
  max-width: 70%;
  padding: 10px 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
}
.bubble-user {
  background: #d9ecff;
}
.bubble-agent {
  background: #fff;
  border: 1px solid #e4e7ed;
}
.input-bar {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}
.send-btn {
  flex: none;
}
/* 等候三个点循环出现：. -> .. -> ... 每 1.5 秒一轮 */
.loading-dots span {
  opacity: 0;
}
.loading-dots span:nth-child(1) {
  opacity: 1;
}
.loading-dots span:nth-child(2) {
  animation: dot-second 1.5s steps(1, end) infinite;
}
.loading-dots span:nth-child(3) {
  animation: dot-third 1.5s steps(1, end) infinite;
}
@keyframes dot-second {
  0% {
    opacity: 0;
  }
  33% {
    opacity: 1;
  }
  100% {
    opacity: 1;
  }
}
@keyframes dot-third {
  0% {
    opacity: 0;
  }
  66% {
    opacity: 1;
  }
  100% {
    opacity: 1;
  }
}
</style>
