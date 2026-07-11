<template>
  <div class="monitor-page">
    <div class="page-header">
      <span class="page-title">实时监控面板</span>
      <a-space>
        <a-tag color="processing">岗亭端</a-tag>
        <a-tag color="default">M1 工程骨架 — 占位页面</a-tag>
      </a-space>
    </div>

    <!-- 实时事件区域占位 -->
    <div class="realtime-grid">
      <div class="realtime-card" v-for="i in 4" :key="i">
        <div class="card-header">
          <span class="card-title">{{ ['入口车道 1', '入口车道 2', '出口车道 1', '出口车道 2'][i - 1] }}</span>
          <span class="status-dot online" />
        </div>
        <a-skeleton active :loading="true" :paragraph="{ rows: 3 }" />
      </div>
    </div>

    <!-- 底部提示 -->
    <a-card :bordered="false" style="margin-top: 16px; text-align: center">
      <p style="color: #64748b; margin: 0">
        岗亭端将在后续任务（T33｜记录查询、实时推送与岗亭基础页）中实现完整的实时通行事件推送和设备状态展示。
      </p>
    </a-card>
  </div>
</template>

<script setup lang="ts">
</script>

<style lang="scss" scoped>
.monitor-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: $spacing-lg;

    .page-title {
      font-size: 18px;
      font-weight: 600;
      color: $text-color;
    }
  }

  .realtime-card {
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: $spacing-md;

      .card-title {
        font-size: 15px;
        font-weight: 600;
        color: $text-color;
      }
    }
  }
}
</style>
