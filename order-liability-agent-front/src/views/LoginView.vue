<template>
  <!-- 登录/注册共用一页，mode 切换两种表单 -->
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">订单判责Agent</h2>
      <el-form label-width="80px" @submit.prevent="handleSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.userAccount" placeholder="字母/数字/下划线，4~25位" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.userPassword" type="password" show-password placeholder="至少4位" />
        </el-form-item>
        <!-- 注册才需要的两个字段 -->
        <template v-if="!isLogin">
          <el-form-item label="确认密码">
            <el-input v-model="form.checkPassword" type="password" show-password placeholder="再输一遍密码" />
          </el-form-item>
          <el-form-item label="昵称">
            <el-input v-model="form.userName" placeholder="选填，最长20位" />
          </el-form-item>
        </template>
        <el-form-item label-width="0" class="submit-item">
          <el-button
            type="primary"
            class="submit-btn"
            :loading="submitting"
            :disabled="!canSubmit || submitting"
            @click="handleSubmit"
          >
            {{ submitting ? (isLogin ? '登录中…' : '注册中…') : (isLogin ? '登录' : '注册') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-button link type="primary" @click="toggleMode">
        {{ isLogin ? '没有账号？去注册' : '已有账号？去登录' }}
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, register } from '../api/user'

const router = useRouter()

// 当前模式：login 登录 / register 注册
const mode = ref('login')
const isLogin = computed(() => mode.value === 'login')

// 请求进行中：按钮禁用并显示"登录中…"，防止重复提交
const submitting = ref(false)

// 两种模式共用一份表单数据，切换时不清空账号密码
const form = reactive({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  userName: ''
})

// 提交门槛：账号密码非空、密码至少4位；注册还要求两次密码一致
const canSubmit = computed(() => {
  const accountOk = form.userAccount.trim().length > 0
  const passwordOk = form.userPassword.length >= 4
  if (isLogin.value) {
    return accountOk && passwordOk
  }
  return accountOk && passwordOk && form.checkPassword === form.userPassword
})

function toggleMode() {
  mode.value = isLogin.value ? 'register' : 'login'
  form.checkPassword = ''
}

async function handleSubmit() {
  if (!canSubmit.value || submitting.value) {
    return
  }
  submitting.value = true
  const account = form.userAccount.trim()
  try {
    if (!isLogin.value) {
      const payload = {
        userAccount: account,
        userPassword: form.userPassword,
        checkPassword: form.checkPassword
      }
      const nickname = form.userName.trim()
      if (nickname) {
        payload.userName = nickname
      }
      await register(payload)
      // 注册成功不自动登录（API.md 2.1），落到下面统一调一次登录
    }
    await login({ userAccount: account, userPassword: form.userPassword })
    router.push('/chat')
  } catch {
    // 失败时拦截器已 toast 后端 message；表单内容保留，方便改了重试
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 动态渐变背景：偏暗的蓝灰/青/紫色系，缓慢流动 */
  background: linear-gradient(120deg, #2f4858, #4f5d75, #5d576b, #33658a);
  background-size: 300% 300%;
  animation: gradient-flow 14s ease infinite;
}
@keyframes gradient-flow {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}
.login-card {
  width: 420px;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25);
}
.submit-btn {
  background-color: #409eff;
  border-color: #409eff;
}
.submit-item :deep(.el-form-item__content) {
  justify-content: center;
}
.login-title {
  margin: 0 0 20px;
  text-align: center;
}
</style>
