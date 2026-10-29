import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

// 应用入口：创建 Vue 应用，装上 Element Plus 和路由，挂载到 index.html 的 #app
createApp(App).use(ElementPlus).use(router).mount('#app')
