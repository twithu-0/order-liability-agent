<template>
  <!-- 根组件：只放一个路由出口，具体显示哪个页面由当前 URL 决定 -->
  <router-view />
</template>

<style>
/* 全局基础样式：让页面撑满整个浏览器窗口 */
html,
body,
#app {
  height: 100%;
  margin: 0;
  background-color: #f5f7fa;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}
</style>
