<template>
  <div class="monitor-tabs">
    <a-tabs v-model:activeKey="activeTab" type="card">
      <a-tab-pane key="monitor" tab="通行监控">
        <slot name="monitor" />
      </a-tab-pane>
      <a-tab-pane key="vehicle" tab="车辆查询">
        <VehicleQuery :parking-lot-id="parkingLotId" />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import VehicleQuery from './VehicleQuery.vue'

defineProps<{
  parkingLotId: number
}>()

const activeTab = ref('monitor')
</script>

<style lang="scss" scoped>
.monitor-tabs {
  height: 100%;
  overflow: auto;

  :deep(.ant-tabs-content) {
    height: calc(100% - 46px);
  }

  :deep(.ant-tabs-tabpane) {
    height: 100%;
    overflow: auto;
  }
}
</style>
