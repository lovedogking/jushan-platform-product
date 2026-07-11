<template>
  <div class="error-page">
    <div class="error-content">
      <h1>500</h1>
      <p>抱歉，服务器内部错误</p>
      <a-button type="primary" @click="$router.push('/dashboard')">返回首页</a-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;

  h1 {
    font-size: 72px;
    font-weight: 700;
    color: $error-color;
    margin: 0 0 16px;
  }

  p {
    color: $text-color-secondary;
    font-size: 16px;
    margin: 0 0 24px;
  }
}
</style>
