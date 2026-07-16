<template>
  <div class="exception-record-page">
    <!-- 类型标签页 -->
    <a-tabs v-model:activeKey="activeType" @change="handleTypeChange">
      <a-tab-pane key="" tab="全部" />
      <a-tab-pane
        v-for="(label, type) in EXCEPTION_TYPE_MAP"
        :key="type"
        :tab="label"
      />
    </a-tabs>

    <!-- 异常记录列表 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'exceptionType'">
          <a-tag :color="typeColor(record.exceptionType)">
            {{ record.exceptionTypeLabel || EXCEPTION_TYPE_MAP[record.exceptionType] || record.exceptionType }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'UNHANDLED' ? 'red' : 'green'">
            {{ record.statusLabel || EXCEPTION_STATUS_MAP[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button
            v-if="record.status === 'UNHANDLED'"
            type="link"
            size="small"
            @click="handleMarkHandled(record as ExceptionAdminVO)"
          >
            标记已处理
          </a-button>
          <span v-else class="text-disabled">已处理</span>
        </template>
      </template>
      <template #emptyText>
        <a-empty description="暂无异常记录" />
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'

import {
  getExceptionRecordPage,
  handleException,
  EXCEPTION_TYPE_MAP,
  EXCEPTION_STATUS_MAP,
  type ExceptionAdminVO,
} from '@/api/exception-record'

// ==================== 选项 ====================
function typeColor(type: string) {
  switch (type) {
    case 'DUP_ENTRY': return 'orange'
    case 'RECOGNITION_FAIL': return 'red'
    case 'BLACKLIST': return 'purple'
    case 'UNPAID_INTERCEPT': return 'blue'
    default: return 'default'
  }
}

// ==================== 表格列 ====================
const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '异常类型', key: 'exceptionType', width: 120 },
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 120 },
  { title: '车场', dataIndex: 'parkingLotName', key: 'parkingLot', width: 180 },
  { title: '通道', dataIndex: 'laneName', key: 'lane', width: 120 },
  { title: '异常描述', dataIndex: 'description', key: 'description', width: 300 },
  { title: '处理状态', key: 'status', width: 100 },
  { title: '处理人', dataIndex: 'handlerName', key: 'handler', width: 100 },
  { title: '操作', key: 'action', width: 120, fixed: 'right' as const },
]

// ==================== 查询 ====================
const activeType = ref('')
const loading = ref(false)
const dataSource = ref<ExceptionAdminVO[]>([])
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

async function fetchData() {
  loading.value = true
  try {
    const params: any = {
      page: pagination.current,
      size: pagination.pageSize,
    }
    if (activeType.value) {
      params.exceptionType = activeType.value
    }
    const res = await getExceptionRecordPage(params)
    dataSource.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleTypeChange() {
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

// ==================== 标记已处理 ====================
async function handleMarkHandled(record: ExceptionAdminVO) {
  try {
    await handleException(record.id)
    message.success('已标记为已处理')
    fetchData()
  } catch {
    // 错误由拦截器处理
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.exception-record-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.text-disabled {
  color: rgba(0, 0, 0, 0.25);
}
</style>
