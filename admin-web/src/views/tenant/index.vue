<template>
  <div class="tenant-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 150px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="PENDING_REVIEW">待审核</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已禁用</a-select-option>
          <a-select-option value="REJECTED">已拒绝</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>

      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增租户
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
      <template #bodyCell="{ column, record, index }">
        <template v-if="column.key === 'index'">
          {{ (pagination.current - 1) * pagination.pageSize + index + 1 }}
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-if="column.key === 'contactPhone'">
          {{ record.contactPhone }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a v-if="record.status === 'PENDING_REVIEW'" @click="handleAudit(record, 'APPROVED')">通过</a>
            <a v-if="record.status === 'PENDING_REVIEW'" style="color: #dc2626" @click="handleAudit(record, 'REJECTED')">拒绝</a>
            <a v-if="record.status === 'DISABLED'" @click="handleAudit(record, 'ENABLED')">启用</a>
            <a v-if="record.status === 'ENABLED'" style="color: #dc2626" @click="handleAudit(record, 'DISABLED')">禁用</a>
            <a style="color: #dc2626" @click="handleDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 审核/拒绝弹窗 -->
    <a-modal
      v-model:open="auditModalOpen"
      :title="auditModalTitle"
      :confirm-loading="auditLoading"
      @ok="handleAuditConfirm"
    >
      <a-form :model="auditForm" layout="vertical">
        <a-form-item v-if="auditAction === 'REJECTED' || auditAction === 'DISABLED'" label="原因" required>
          <a-textarea v-model:value="auditForm.reason" placeholder="请填写操作原因" :rows="3" />
        </a-form-item>
        <a-form-item v-else label="备注">
          <a-textarea v-model:value="auditForm.reason" placeholder="备注（可选）" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增租户弹窗 -->
    <a-modal
      v-model:open="createModalOpen"
      title="新增租户"
      :confirm-loading="createLoading"
      @ok="handleCreateConfirm"
    >
      <a-form ref="createFormRef" :model="createForm" :rules="createFormRules" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="企业名称" name="name">
          <a-input v-model:value="createForm.name" placeholder="请输入企业名称" />
        </a-form-item>
        <a-form-item label="联系人" name="contactPerson">
          <a-input v-model:value="createForm.contactPerson" placeholder="请输入联系人" />
        </a-form-item>
        <a-form-item label="联系电话" name="contactPhone">
          <a-input v-model:value="createForm.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="车场上限" name="maxParkingLots">
          <a-input-number v-model:value="createForm.maxParkingLots" :min="1" :max="999" style="width: 100%" />
        </a-form-item>
        <a-form-item label="设备上限" name="maxDevices">
          <a-input-number v-model:value="createForm.maxDevices" :min="1" :max="9999" style="width: 100%" />
        </a-form-item>
        <a-form-item label="账号上限" name="maxEmployees">
          <a-input-number v-model:value="createForm.maxEmployees" :min="1" :max="9999" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal, type FormInstance } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getTenants, auditTenant, createTenant, deleteTenant, type TenantVO } from '@/api/tenant'

const columns = [
  { title: '#', key: 'index', width: 50 },
  { title: '企业名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '联系人', dataIndex: 'contactPerson', key: 'contactPerson', width: 100 },
  { title: '手机号', dataIndex: 'contactPhone', key: 'contactPhone', width: 130 },
  { title: '管理员账号', dataIndex: 'adminUsername', key: 'adminUsername', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '车场上限', dataIndex: 'maxParkingLots', key: 'maxParkingLots', width: 80 },
  { title: '设备上限', dataIndex: 'maxDevices', key: 'maxDevices', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<TenantVO[]>([])
const queryStatus = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 审核弹窗
const auditModalOpen = ref(false)
const auditLoading = ref(false)
const auditAction = ref('')
const auditTarget = ref<TenantVO | null>(null)
const auditForm = reactive({ reason: '' })

const auditModalTitle = ref('')

// 新增租户弹窗
const createModalOpen = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  name: '',
  contactPerson: '',
  contactPhone: '',
  maxParkingLots: 3,
  maxDevices: 10,
  maxEmployees: 20,
})
const createFormRules: Record<string, any> = {
  name: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
}

function handleCreate() {
  createForm.name = ''
  createForm.contactPerson = ''
  createForm.contactPhone = ''
  createForm.maxParkingLots = 3
  createForm.maxDevices = 10
  createForm.maxEmployees = 20
  createFormRef.value?.resetFields()
  createModalOpen.value = true
}

async function handleCreateConfirm() {
  try {
    await createFormRef.value?.validateFields()
  } catch {
    return
  }
  createLoading.value = true
  try {
    await createTenant({
      name: createForm.name.trim(),
      contactPerson: createForm.contactPerson.trim(),
      contactPhone: createForm.contactPhone.trim(),
      maxParkingLots: createForm.maxParkingLots,
      maxDevices: createForm.maxDevices,
      maxEmployees: createForm.maxEmployees,
    })
    message.success('租户创建成功')
    createModalOpen.value = false
    fetchData()
  } catch {
    // 错误已在拦截器处理
  } finally {
    createLoading.value = false
  }
}

const STATUS_MAP: Record<string, { color: string; label: string }> = {
  PENDING_REVIEW: { color: 'orange', label: '待审核' },
  ENABLED: { color: 'green', label: '已启用' },
  DISABLED: { color: 'red', label: '已禁用' },
  REJECTED: { color: 'default', label: '已拒绝' },
}

function statusColor(status: string) {
  return STATUS_MAP[status]?.color || 'default'
}
function statusLabel(status: string) {
  return STATUS_MAP[status]?.label || status
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getTenants({
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

const ACTION_LABELS: Record<string, string> = {
  APPROVED: '通过审核',
  REJECTED: '拒绝',
  ENABLED: '启用',
  DISABLED: '禁用',
}

function handleAudit(record: any, action: string) {
  auditTarget.value = record
  auditAction.value = action
  auditForm.reason = ''
  auditModalTitle.value = `${ACTION_LABELS[action] || action} — ${record.name}`
  auditModalOpen.value = true
}

async function handleAuditConfirm() {
  if (!auditTarget.value) return
  const needReason = ['REJECTED', 'DISABLED'].includes(auditAction.value)
  if (needReason && !auditForm.reason.trim()) {
    message.warning('请填写操作原因')
    return
  }
  auditLoading.value = true
  try {
    await auditTenant(auditTarget.value.id, {
      action: auditAction.value,
      reason: auditForm.reason || undefined,
    })
    message.success('操作成功')
    auditModalOpen.value = false
    fetchData()
  } catch {
    // 错误已在拦截器处理
  } finally {
    auditLoading.value = false
  }
}

function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除租户「${record.name}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    async onOk() {
      try {
        await deleteTenant(record.id)
        message.success('删除成功')
        fetchData()
      } catch {
        // 错误由统一拦截器处理
      }
    },
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.tenant-page {
  background: #fff;
  border-radius: $border-radius-base;
  padding: $spacing-lg;
}

.query-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
