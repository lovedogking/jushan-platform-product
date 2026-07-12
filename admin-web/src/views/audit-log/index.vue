<template>
  <div class="audit-log-page">
    <div class="query-bar">
      <a-space wrap>
        <a-select v-model:value="queryAction" placeholder="操作类型" allow-clear style="width: 140px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="GATE_OPEN">开闸</a-select-option>
          <a-select-option value="TIME_SYNC">校时</a-select-option>
          <a-select-option value="STATUS_CHANGE">状态变更</a-select-option>
          <a-select-option value="PROXY_START">代操作启动</a-select-option>
          <a-select-option value="PROXY_STOP">代操作停止</a-select-option>
          <a-select-option value="CREATE">创建</a-select-option>
          <a-select-option value="UPDATE">更新</a-select-option>
        </a-select>
        <a-select v-model:value="queryIsProxy" placeholder="代操作" allow-clear style="width: 110px" @change="handleQuery">
          <a-select-option :value="undefined">全部</a-select-option>
          <a-select-option :value="1">是</a-select-option>
          <a-select-option :value="0">否</a-select-option>
        </a-select>
        <a-range-picker v-model:value="dateRange" show-time format="YYYY-MM-DD HH:mm:ss" @change="handleQuery" />
        <a-button type="primary" @click="handleQuery"><template #icon><SearchOutlined /></template>查询</a-button>
        <a-button @click="handleReset"><template #icon><ReloadOutlined /></template>重置</a-button>
      </a-space>
    </div>

    <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination" row-key="id"
      @change="handleTableChange" :scroll="{ x: 1100 }">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'isProxy'">
          <a-tag :color="record.isProxy === 1 ? 'red' : 'default'">
            {{ record.isProxy === 1 ? '代理' : '直接' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'result'">
          <a-tag :color="record.result === 'SUCCESS' ? 'green' : record.result === 'FAIL' ? 'red' : 'orange'">
            {{ record.result || '-' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a @click="showDetail(record)">详情</a>
        </template>
      </template>
    </a-table>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailOpen" title="审计日志详情" width="640px" :footer="null">
      <a-descriptions v-if="currentLog" :column="2" size="small" bordered>
        <a-descriptions-item label="ID">{{ currentLog.id }}</a-descriptions-item>
        <a-descriptions-item label="租户 ID">{{ currentLog.tenantId }}</a-descriptions-item>
        <a-descriptions-item label="操作类型">{{ currentLog.action }}</a-descriptions-item>
        <a-descriptions-item label="目标类型">{{ currentLog.targetType }}</a-descriptions-item>
        <a-descriptions-item label="目标 ID">{{ currentLog.targetId }}</a-descriptions-item>
        <a-descriptions-item label="操作人">{{ currentLog.operatorName }}</a-descriptions-item>
        <a-descriptions-item label="操作结果">
          <a-tag :color="currentLog.result === 'SUCCESS' ? 'green' : 'red'">{{ currentLog.result }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="是否代理">
          <a-tag :color="currentLog.isProxy === 1 ? 'red' : 'default'">{{ currentLog.isProxy === 1 ? '是' : '否' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="目标租户" v-if="currentLog.targetTenantId">{{ currentLog.targetTenantId }}</a-descriptions-item>
        <a-descriptions-item label="失败原因" :span="2" v-if="currentLog.failReason">{{ currentLog.failReason }}</a-descriptions-item>
        <a-descriptions-item label="操作原因" :span="2" v-if="currentLog.reason">{{ currentLog.reason }}</a-descriptions-item>
        <a-descriptions-item label="操作前" :span="2" v-if="currentLog.beforeValue">
          <div style="max-height: 120px; overflow: auto; word-break: break-all; font-size: 12px; white-space: pre-wrap;">{{ currentLog.beforeValue }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="操作后" :span="2" v-if="currentLog.afterValue">
          <div style="max-height: 120px; overflow: auto; word-break: break-all; font-size: 12px; white-space: pre-wrap;">{{ currentLog.afterValue }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="客户端 IP">{{ currentLog.clientIp }}</a-descriptions-item>
        <a-descriptions-item label="操作时间">{{ currentLog.createdAt }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getAuditLogs, type AuditLogVO } from '@/api/audit-log'
import dayjs from 'dayjs'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '操作类型', dataIndex: 'action', key: 'action', width: 110 },
  { title: '目标', dataIndex: 'targetType', key: 'targetType', width: 80 },
  { title: '操作人', dataIndex: 'operatorName', key: 'operatorName', width: 100 },
  { title: '代理', key: 'isProxy', width: 60 },
  { title: '结果', key: 'result', width: 80 },
  { title: '失败原因', dataIndex: 'failReason', key: 'failReason', ellipsis: true, width: 140 },
  { title: '客户端 IP', dataIndex: 'clientIp', key: 'clientIp', width: 130 },
  { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 60, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<AuditLogVO[]>([])
const queryAction = ref('')
const queryIsProxy = ref<number | undefined>(undefined)
const dateRange = ref<any>()

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条`,
})

const detailOpen = ref(false)
const currentLog = ref<AuditLogVO | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const p = {
      page: pagination.current as number,
      size: pagination.pageSize as number,
      action: queryAction.value || undefined,
      isProxy: queryIsProxy.value !== undefined ? queryIsProxy.value : undefined,
      startTime: dateRange.value?.length === 2 ? dayjs(dateRange.value[0]).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      endTime: dateRange.value?.length === 2 ? dayjs(dateRange.value[1]).format('YYYY-MM-DDTHH:mm:ss') : undefined,
    }
    const res = await getAuditLogs(p)
    dataSource.value = res.records
    pagination.total = res.total
  } finally { loading.value = false }
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() {
  queryAction.value = ''; queryIsProxy.value = undefined; dateRange.value = []
  pagination.current = 1; fetchData()
}
function handleTableChange(pag: any) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData()
}

function showDetail(record: any) {
  currentLog.value = record; detailOpen.value = true
}

onMounted(() => { fetchData() })
</script>

<style lang="scss" scoped>
.audit-log-page { background: #fff; border-radius: $border-radius-base; padding: $spacing-lg; }
.query-bar { margin-bottom: $spacing-lg; }
</style>
