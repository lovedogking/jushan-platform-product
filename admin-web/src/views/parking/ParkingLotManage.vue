<template>
  <div class="parking-lot-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-input
          v-model:value="queryName"
          placeholder="停车场名称"
          allow-clear
          style="width: 180px"
          @press-enter="handleQuery"
        />
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option v-for="opt in PARKING_LOT_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
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
          <a-tag :color="statusColor(record.status)">
            {{ statusText(record.status) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'address'">
          <a-tooltip :title="fullAddress(record)">
            <span class="ellipsis">{{ fullAddress(record) }}</span>
          </a-tooltip>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a @click="handleManageZones(record)">区域</a>
            <a v-permission="'parking:read'" @click="handleManageParams(record)">参数</a>
            <a-divider type="vertical" />
            <a v-if="record.status !== 1" @click="handleToggleStatus(record, 1)">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 2)">停用</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该停车场？" @confirm="handleDelete(record)">
              <a style="color: #dc2626">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:open="formModalOpen"
      :title="formModalTitle"
      :confirm-loading="formLoading"
      width="720px"
      @ok="handleFormSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }" :rules="formRules">
        <a-form-item label="停车场名称" name="name">
          <a-input v-model:value="formData.name" placeholder="请输入停车场名称" />
        </a-form-item>
        <a-form-item label="所属公司" name="companyId">
          <a-select v-model:value="formData.companyId" placeholder="请选择公司" :disabled="isEditing">
            <a-select-option v-for="c in companyOptions" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="详细地址">
          <a-input v-model:value="formData.address" placeholder="请输入详细地址" />
        </a-form-item>
        <a-form-item label="联系人">
          <a-input v-model:value="formData.contactName" placeholder="请输入联系人" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="formData.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="总车位数">
          <a-input-number v-model:value="formData.totalSpaces" :min="0" style="width: 100%" placeholder="请输入总车位数" />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-select v-model:value="formData.status" placeholder="请选择状态">
            <a-select-option :value="1">启用</a-select-option>
            <a-select-option :value="2">禁用</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 车场参数配置抽屉（任务包 1-1） -->
    <ParkingLotParamDrawer ref="paramDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import ParkingLotParamDrawer from './ParkingLotParamDrawer.vue'
import {
  getParkingLots,
  createParkingLot,
  updateParkingLot,
  deleteParkingLot,
  updateParkingLotStatus,
  type ParkingLotVO,
} from '@/api/parking-lot'
import { getCompanies } from '@/api/company'

const router = useRouter()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '车场名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '地址', key: 'address', ellipsis: true },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName', width: 100 },
  { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone', width: 130 },
  { title: '总车位', dataIndex: 'totalSpaces', key: 'totalSpaces', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<ParkingLotVO[]>([])
const queryName = ref('')
const queryStatus = ref<number | undefined>(undefined)
const companyOptions = ref<{ id: number; name: string }[]>([])

// 车场参数抽屉引用
const paramDrawerRef = ref()

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
  companyId: undefined as number | undefined,
  address: '',
  contactName: '',
  contactPhone: '',
  totalSpaces: 0,
  status: 1,
})

const formRules: Record<string, any> = {
  name: [{ required: true, message: '请输入车场名称', trigger: 'blur' }],
  companyId: [{ required: true, message: '请选择所属公司', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getParkingLots({
      current: pagination.current,
      size: pagination.pageSize,
      name: queryName.value || undefined,
      status: queryStatus.value,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadCompanies() {
  try {
    const res = await getCompanies({ page: 1, size: 100 })
    companyOptions.value = res.records
  } catch {
    // ignore
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryName.value = ''
  queryStatus.value = undefined
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
  formModalTitle.value = '新增车场'
  formData.name = ''
  formData.companyId = undefined
  formData.address = ''
  formData.contactName = ''
  formData.contactPhone = ''
  formData.totalSpaces = 0
  formData.status = 1
  formModalOpen.value = true
}

async function handleEdit(record: any) {
  isEditing.value = true
  editingId.value = record.id
  formModalTitle.value = '编辑车场'
  formData.name = record.name
  formData.companyId = record.companyId
  formData.address = record.address || ''
  formData.contactName = record.contactName || ''
  formData.contactPhone = record.contactPhone || ''
  formData.totalSpaces = record.totalSpaces || 0
  formData.status = record.status || 1
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.name.trim()) {
    message.warning('请输入车场名称')
    return
  }
  if (!formData.companyId) {
    message.warning('请选择所属公司')
    return
  }
  formLoading.value = true
  try {
    const payload = {
      name: formData.name.trim(),
      companyId: formData.companyId,
      address: formData.address || undefined,
      contactName: formData.contactName || undefined,
      contactPhone: formData.contactPhone || undefined,
      totalSpaces: formData.totalSpaces,
      status: formData.status,
    }
    if (isEditing.value && editingId.value) {
      await updateParkingLot(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createParkingLot(payload)
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

function handleToggleStatus(record: any, status: number) {
  const actionText = status === 1 ? '启用' : '停用'
  updateParkingLotStatus(record.id, status).then(() => {
    message.success(`已${actionText}`)
    fetchData()
  }).catch(() => {})
}

async function handleDelete(record: any) {
  try {
    await deleteParkingLot(record.id)
    message.success('删除成功')
    fetchData()
  } catch {
    // ignore
  }
}

function handleManageZones(record: any) {
  router.push(`/parking-zones?lotId=${record.id}`)
}

function handleManageParams(record: any) {
  paramDrawerRef.value?.open({ id: record.id, name: record.name })
}

function statusText(status: number) {
  const map: Record<number, string> = { 1: '启用', 2: '禁用' }
  return map[status] || '未知'
}

function statusColor(status: number) {
  const map: Record<number, string> = { 1: 'green', 2: 'orange' }
  return map[status] || 'default'
}

function fullAddress(record: any) {
  const parts = [record.province, record.city, record.district, record.address].filter(Boolean)
  return parts.join('')
}

onMounted(() => {
  loadCompanies()
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

.ellipsis {
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-upload {
  .upload-tip {
    margin-top: 8px;
    color: #999;
    font-size: 12px;
  }
}
</style>
