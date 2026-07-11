<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const theme = {
  token: {
    colorPrimary: '#165DFF',
    colorInfo: '#165DFF',
    colorSuccess: '#00B578',
    colorWarning: '#F59E0B',
    colorError: '#DC2626',
    colorText: '#111827',
    colorTextSecondary: '#64748B',
    colorBorder: '#D8E1EA',
    colorBgLayout: '#F3F6FA',
    borderRadius: 6,
    controlHeight: 32,
  },
  components: {
    Button: {
      borderRadius: 999,
    },
    Table: {
      headerBg: '#F6F8FB',
      headerColor: '#334155',
      rowHoverBg: '#F8FAFC',
    },
    Tag: {
      borderRadiusSM: 999,
    },
  },
}
</script>

<style>
#app {
  width: 100%;
  height: 100%;
}
</style>
