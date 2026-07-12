<template>
  <div class="parking-lot-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已停用</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增停车场
      </a-button>
    </div>

    <!-- 表格区 -->
    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'capacity'">
          {{ record.remainingSpaces }} / {{ record.totalSpaces }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a v-if="record.status === 'DISABLED'" @click="handleToggleStatus(record, 'ENABLED')">启用</a>
            <a v-if="record.status === 'ENABLED'" style="color: #dc2626" @click="handleToggleStatus(record, 'DISABLED')">停用</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="formModalOpen"
      :title="formModalTitle"
      :confirm-loading="formLoading"
      width="560px"
      @ok="handleFormSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="停车场名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入停车场名称" />
        </a-form-item>
        <a-form-item label="总车位数" required>
          <a-input-number v-model:value="formData.totalSpaces" :min="0" style="width: 100%" placeholder="请输入总车位数" />
        </a-form-item>
        <a-form-item label="地址">
          <a-input v-model:value="formData.address" placeholder="请输入地址" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="formData.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 停用原因弹窗 -->
    <a-modal
      v-model:open="statusModalOpen"
      title="停用停车场"
      :confirm-loading="statusLoading"
      @ok="handleStatusConfirm"
    >
      <a-form layout="vertical">
        <a-form-item label="停用原因" required>
          <a-textarea v-model:value="statusReason" placeholder="请填写停用原因" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import {
  getParkingLots,
  createParkingLot,
  updateParkingLot,
  updateParkingLotStatus,
  type ParkingLotVO,
} from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '停车场名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '地址', dataIndex: 'address', key: 'address', ellipsis: true },
  { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone', width: 130 },
  { title: '车位（剩余/总）', key: 'capacity', width: 140 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 140, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<ParkingLotVO[]>([])
const queryStatus = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 新增/编辑弹窗
const formModalOpen = ref(false)
const formLoading = ref(false)
const formModalTitle = ref('')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive({
  name: '',
  totalSpaces: 100,
  address: '',
  contactPhone: '',
})

// 停用弹窗
const statusModalOpen = ref(false)
const statusLoading = ref(false)
const statusTarget = ref<ParkingLotVO | null>(null)
const statusReason = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await getParkingLots({
      page: pagination.current,
      size: pagination.pageSize,
      status: queryStatus.value || undefined,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryStatus.value = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function handleCreate() {
  isEditing.value = false
  editingId.value = null
  formModalTitle.value = '新增停车场'
  formData.name = ''
  formData.totalSpaces = 100
  formData.address = ''
  formData.contactPhone = ''
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true
  editingId.value = record.id
  formModalTitle.value = '编辑停车场'
  formData.name = record.name
  formData.totalSpaces = record.totalSpaces
  formData.address = record.address || ''
  formData.contactPhone = record.contactPhone || ''
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.name.trim()) {
    message.warning('请输入停车场名称')
    return
  }
  formLoading.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateParkingLot(editingId.value, {
        name: formData.name.trim(),
        totalSpaces: formData.totalSpaces,
        address: formData.address.trim(),
        contactPhone: formData.contactPhone.trim(),
      })
      message.success('更新成功')
    } else {
      await createParkingLot({
        name: formData.name.trim(),
        totalSpaces: formData.totalSpaces,
        address: formData.address.trim(),
        contactPhone: formData.contactPhone.trim(),
      })
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch {
    // 错误已处理
  } finally {
    formLoading.value = false
  }
}

function handleToggleStatus(record: any, action: string) {
  if (action === 'ENABLED') {
    // 启用无需原因
    updateParkingLotStatus(record.id, { action }).then(() => {
      message.success('已启用')
      fetchData()
    }).catch(() => {})
    return
  }
  // 停用需要原因
  statusTarget.value = record
  statusReason.value = ''
  statusModalOpen.value = true
}

async function handleStatusConfirm() {
  if (!statusReason.value.trim()) {
    message.warning('请填写停用原因')
    return
  }
  if (!statusTarget.value) return
  statusLoading.value = true
  try {
    await updateParkingLotStatus(statusTarget.value.id, {
      action: 'DISABLED',
      reason: statusReason.value,
    })
    message.success('已停用')
    statusModalOpen.value = false
    fetchData()
  } catch {
    // 错误已处理
  } finally {
    statusLoading.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.parking-lot-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}
</style>
