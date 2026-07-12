<template>
  <div class="proxy-page">
    <!-- 当前状态卡片 -->
    <a-card title="代操作状态" style="margin-bottom: 16px">
      <template #extra>
        <a-tag :color="proxyStatus.active ? 'red' : 'green'">
          {{ proxyStatus.active ? '代操作中' : '未启动' }}
        </a-tag>
      </template>
      <a-descriptions :column="2" size="small">
        <a-descriptions-item label="目标租户">
          {{ proxyStatus.targetTenantName || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="目标租户 ID">
          {{ proxyStatus.targetTenantId || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="代操作原因" :span="2">
          {{ proxyStatus.reason || '-' }}
        </a-descriptions-item>
        <a-descriptions-item label="启动时间">
          {{ proxyStatus.startedAt || '-' }}
        </a-descriptions-item>
      </a-descriptions>
      <div style="margin-top: 12px">
        <a-button v-if="!proxyStatus.active" type="primary" @click="showStartModal">启动代操作</a-button>
        <a-button v-else danger @click="handleStop">停止代操作</a-button>
        <a-button style="margin-left: 8px" @click="refreshStatus">刷新状态</a-button>
      </div>
    </a-card>

    <!-- 代操作预警提示 -->
    <a-alert
      v-if="proxyStatus.active"
      message="⚠ 当前处于代操作模式"
      description="平台超级管理员正在以目标租户身份执行操作。所有操作将被审计记录，请谨慎操作，完成代操作后请立即退出。"
      type="warning"
      banner
      show-icon
      style="margin-bottom: 16px"
    />

    <!-- 启动代操作弹窗 -->
    <a-modal v-model:open="startModalOpen" title="启动代操作" :confirm-loading="startLoading" @ok="handleStart">
      <a-form :model="startForm" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="目标租户" required>
          <a-select v-model:value="startForm.tenantId" placeholder="请选择目标租户" show-search option-filter-prop="label">
            <a-select-option v-for="t in tenantOptions" :key="t.id" :value="t.id" :label="t.name">
              {{ t.name }} (ID: {{ t.id }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="代操作原因" required>
          <a-textarea v-model:value="startForm.reason" placeholder="请详细说明代操作原因" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getProxyStatus, startProxy, stopProxy, type ProxyStatus } from '@/api/proxy'
import { getTenants, type TenantVO } from '@/api/tenant'

const proxyStatus = reactive<ProxyStatus>({
  active: false, targetTenantId: null, targetTenantName: null, reason: null, startedAt: null,
})

const tenantOptions = ref<TenantVO[]>([])

const startModalOpen = ref(false)
const startLoading = ref(false)
const startForm = reactive({ tenantId: undefined as number | undefined, reason: '' })

async function refreshStatus() {
  try {
    const res = await getProxyStatus()
    Object.assign(proxyStatus, res)
  } catch { /* */ }
}

async function loadTenants() {
  try {
    const res = await getTenants({ page: 1, size: 100, status: 'ENABLED' })
    tenantOptions.value = res.records
  } catch { /* */ }
}

function showStartModal() {
  startForm.tenantId = undefined; startForm.reason = ''; startModalOpen.value = true
}

async function handleStart() {
  if (!startForm.tenantId) { message.warning('请选择目标租户'); return }
  if (!startForm.reason.trim()) { message.warning('请填写代操作原因'); return }
  startLoading.value = true
  try {
    await startProxy(startForm.tenantId, startForm.reason.trim())
    message.success('代操作已启动')
    startModalOpen.value = false; refreshStatus()
  } catch { /* */ } finally { startLoading.value = false }
}

async function handleStop() {
  try {
    await stopProxy()
    message.success('代操作已停止')
    refreshStatus()
  } catch { /* */ }
}

onMounted(() => { refreshStatus(); loadTenants() })
</script>

<style lang="scss" scoped>
.proxy-page { background: #fff; border-radius: $border-radius-base; padding: $spacing-lg; }
</style>
