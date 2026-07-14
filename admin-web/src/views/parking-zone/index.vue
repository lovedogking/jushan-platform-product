<template>
  <div class="parking-zone-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select
          v-model:value="queryLotId"
          placeholder="选择停车场"
          allow-clear
          style="width: 200px"
          @change="handleQuery"
        >
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option v-for="opt in ZONE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" :disabled="!queryLotId" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增区域
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
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'level'">
          {{ levelText(record.level) }}
        </template>
        <template v-if="column.key === 'tag'">
          <a-tag v-if="record.tag">{{ record.tag }}</a-tag>
          <span v-else style="color: #999">-</span>
        </template>
        <template v-if="column.key === 'spaces'">
          <span>{{ record.totalSpaces }} / {{ record.fixedSpaces }} / {{ record.tempSpaces }}</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a-divider type="vertical" />
            <a v-if="record.status !== 1" @click="handleToggleStatus(record, 1)">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 2)">禁用</a>
            <a-divider type="vertical" />
            <a-popconfirm title="确认删除该区域？" @confirm="handleDelete(record)">
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
      width="600px"
      @ok="handleFormSubmit"
    >
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.lotId" placeholder="请选择停车场" :disabled="isEditing">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="区域名称" required>
          <a-input v-model:value="formData.name" placeholder="请输入区域名称" />
        </a-form-item>
        <a-form-item label="区域标签">
          <a-select v-model:value="formData.tag" placeholder="请选择标签" allow-clear>
            <a-select-option v-for="opt in ZONE_TAG_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="区域等级">
          <a-select v-model:value="formData.level" placeholder="请选择等级">
            <a-select-option v-for="opt in ZONE_LEVEL_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="车位总数" required>
          <a-input-number v-model:value="formData.totalSpaces" :min="0" style="width: 100%" @change="calcTempSpaces" />
        </a-form-item>
        <a-form-item label="固定车位数">
          <a-input-number v-model:value="formData.fixedSpaces" :min="0" :max="formData.totalSpaces" style="width: 100%" @change="calcTempSpaces" />
        </a-form-item>
        <a-form-item label="临停车位数">
          <a-input-number v-model:value="formData.tempSpaces" :min="0" style="width: 100%" disabled />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="formData.status" placeholder="请选择状态">
            <a-select-option v-for="opt in ZONE_STATUS_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model:value="formData.remark" placeholder="备注（可选）" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute } from 'vue-router'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import {
  getParkingZones,
  createParkingZone,
  updateParkingZone,
  deleteParkingZone,
  updateParkingZoneStatus,
  ZONE_STATUS_OPTIONS,
  ZONE_LEVEL_OPTIONS,
  ZONE_TAG_OPTIONS,
  type ParkingZoneVO,
} from '@/api/parking-zone'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const route = useRoute()

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '区域名称', dataIndex: 'name', key: 'name', width: 160 },
  { title: '标签', key: 'tag', width: 100 },
  { title: '等级', key: 'level', width: 90 },
  { title: '车位（总/固定/临时）', key: 'spaces', width: 170 },
  { title: '状态', key: 'status', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<ParkingZoneVO[]>([])
const queryStatus = ref<number | undefined>(undefined)
const queryLotId = ref<number | undefined>(undefined)
const parkingLotOptions = ref<ParkingLotVO[]>([])

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
  lotId: undefined as number | undefined,
  name: '',
  tag: undefined as string | undefined,
  level: 1,
  totalSpaces: 0,
  fixedSpaces: 0,
  tempSpaces: 0,
  status: 1,
  remark: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getParkingZones({
      current: pagination.current,
      size: pagination.pageSize,
      lotId: queryLotId.value,
      status: queryStatus.value,
    })
    dataSource.value = res.records
    pagination.total = res.total
  } finally {
    loading.value = false
  }
}

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ current: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch {
    // ignore
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryStatus.value = undefined
  queryLotId.value = undefined
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
  formModalTitle.value = '新增区域'
  formData.lotId = queryLotId.value
  formData.name = ''
  formData.tag = undefined
  formData.level = 1
  formData.totalSpaces = 0
  formData.fixedSpaces = 0
  formData.tempSpaces = 0
  formData.status = 1
  formData.remark = ''
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true
  editingId.value = record.id
  formModalTitle.value = '编辑区域'
  formData.lotId = record.lotId
  formData.name = record.name
  formData.tag = record.tag || undefined
  formData.level = record.level || 1
  formData.totalSpaces = record.totalSpaces || 0
  formData.fixedSpaces = record.fixedSpaces || 0
  formData.tempSpaces = record.tempSpaces || 0
  formData.status = record.status || 1
  formData.remark = record.remark || ''
  formModalOpen.value = true
}

function calcTempSpaces() {
  const total = formData.totalSpaces || 0
  const fixed = formData.fixedSpaces || 0
  formData.tempSpaces = Math.max(0, total - fixed)
}

async function handleFormSubmit() {
  if (!formData.lotId) {
    message.warning('请选择所属停车场')
    return
  }
  if (!formData.name.trim()) {
    message.warning('请输入区域名称')
    return
  }
  formLoading.value = true
  try {
    const payload = {
      lotId: formData.lotId,
      name: formData.name.trim(),
      tag: formData.tag,
      level: formData.level,
      totalSpaces: formData.totalSpaces,
      fixedSpaces: formData.fixedSpaces,
      status: formData.status,
      remark: formData.remark || undefined,
    }
    if (isEditing.value && editingId.value) {
      await updateParkingZone(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createParkingZone(payload)
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
  const actionText = status === 1 ? '启用' : '禁用'
  updateParkingZoneStatus(record.id, status).then(() => {
    message.success(`已${actionText}`)
    fetchData()
  }).catch(() => {})
}

async function handleDelete(record: any) {
  try {
    await deleteParkingZone(record.id)
    message.success('删除成功')
    fetchData()
  } catch {
    // ignore
  }
}

function levelText(level: number) {
  const opt = ZONE_LEVEL_OPTIONS.find(o => o.value === level)
  return opt?.label || '未知'
}

onMounted(() => {
  loadParkingLots()
  // 从 URL 参数获取 lotId
  const lotIdParam = route.query.lotId
  if (lotIdParam) {
    queryLotId.value = Number(lotIdParam)
  }
  fetchData()
})
</script>

<style lang="scss" scoped>
.parking-zone-page {
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
