<template>
  <div class="page-container">
    <van-empty
      description="页面不存在"
      image="error"
    >
      <van-button type="primary" round @click="$router.replace('/')">
        返回首页
      </van-button>
    </van-empty>
  </div>
</template>

<script setup lang="ts">
</script>
