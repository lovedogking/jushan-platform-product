<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">平台驾驶舱</span>
    </div>

    <!-- 欢迎卡片 -->
    <a-card class="welcome-card" :bordered="false">
      <div class="welcome-content">
        <div class="welcome-text">
          <h2>欢迎使用飓山智慧停车运营平台</h2>
          <p>本页面为平台驾驶舱占位页，后续 T39（计费订单页面）将在此展示经营看板、核心指标和实时概览。</p>
          <a-tag color="processing">M1 工程骨架阶段 — 占位页面</a-tag>
        </div>
      </div>
    </a-card>

    <!-- 指标占位卡片 -->
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="6">
        <a-card :bordered="false">
          <a-skeleton active :loading="true" :paragraph="{ rows: 2 }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-skeleton active :loading="true" :paragraph="{ rows: 2 }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-skeleton active :loading="true" :paragraph="{ rows: 2 }" />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false">
          <a-skeleton active :loading="true" :paragraph="{ rows: 2 }" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
</script>

<style lang="scss" scoped>
.welcome-card {
  border-radius: $border-radius-lg;

  .welcome-content {
    padding: 24px 0;

    h2 {
      font-size: 22px;
      font-weight: 700;
      color: $text-color;
      margin: 0 0 12px;
    }

    p {
      color: $text-color-secondary;
      font-size: 14px;
      line-height: 1.7;
      margin: 0 0 16px;
      max-width: 600px;
    }
  }
}
</style>
