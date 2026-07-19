<template>
  <div class="dashboard-page">
    <div class="page-header"><h3>运营概览</h3></div>
    <a-row :gutter="16">
      <a-col :span="6"><a-card><a-statistic title="车场总数" :value="stats.totalLots" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="车道总数" :value="stats.totalLanes" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="设备总数" :value="stats.totalDevices" /></a-card></a-col>
      <a-col :span="6"><a-card><a-statistic title="在场车辆" :value="stats.activeSessions" /></a-card></a-col>
    </a-row>
    <a-row :gutter="16" style="margin-top:16px">
      <a-col :span="12"><a-card title="今日入场"><a-statistic :value="stats.todayEntries" /></a-card></a-col>
      <a-col :span="12"><a-card title="今日出场"><a-statistic :value="stats.todayExits" /></a-card></a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { getDashboardStats } from '@/api/parking-manage'

const stats = reactive({ totalLots: 0, totalLanes: 0, totalDevices: 0, activeSessions: 0, todayEntries: 0, todayExits: 0 })

onMounted(async () => {
  try { const res = await getDashboardStats(); Object.assign(stats, res) } catch { /* keep defaults */ }
})
</script>

<style lang="scss" scoped>
.dashboard-page { .page-header { margin-bottom: 16px; h3 { margin: 0; } } }
</style>
