<template>
  <div class="error-page">
    <h1>404</h1>
    <p>页面不存在</p>
    <a-button type="primary" @click="$router.push('/monitor')">返回监控面板</a-button>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  h1 { font-size: 72px; font-weight: 700; color: $primary-color; margin: 0 0 16px; }
  p { color: $text-color-secondary; font-size: 16px; margin: 0 0 24px; }
}
</style>
