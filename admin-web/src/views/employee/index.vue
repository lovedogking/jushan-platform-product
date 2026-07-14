<template>
  <div class="employee-page">
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryStatus" placeholder="全部状态" allow-clear style="width: 130px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已禁用</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <a-button type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>新增员工
      </a-button>
    </div>

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
            {{ record.status === 'ENABLED' ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'parkingLotNames'">
          <a-tag v-for="name in record.parkingLotNames" :key="name" style="margin: 2px">{{ name }}</a-tag>
          <span v-if="!record.parkingLotNames?.length" style="color: #999">未分配</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a @click="handleResetPassword(record)">重置密码</a>
            <a v-if="record.status === 'DISABLED'" @click="handleToggleStatus(record, 'ENABLED')">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 'DISABLED')">禁用</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="formModalOpen" :title="formModalTitle" :confirm-loading="formLoading" width="600px" @ok="handleFormSubmit">
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="姓名" required>
          <a-input v-model:value="formData.displayName" placeholder="请输入姓名" />
        </a-form-item>
        <a-form-item v-if="!isEditing" label="手机号" required>
          <a-input v-model:value="formData.phone" placeholder="请输入手机号" />
        </a-form-item>
        <a-form-item v-if="!isEditing" label="密码" required>
          <a-input-password v-model:value="formData.password" placeholder="请输入密码（至少6位）" />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="formData.roleCode" placeholder="请选择角色">
            <a-select-option value="parking_manager">停车场管理员</a-select-option>
            <a-select-option value="finance">财务</a-select-option>
            <a-select-option value="device_maintenance">设备维护员</a-select-option>
            <a-select-option value="booth_operator">岗亭操作员</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="授权停车场" required>
          <a-select v-model:value="formData.parkingLotIds" mode="multiple" placeholder="请选择停车场">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 重置密码弹窗 -->
    <a-modal v-model:open="pwdModalOpen" title="重置密码" :confirm-loading="pwdLoading" @ok="handlePwdConfirm">
      <a-form layout="vertical">
        <a-form-item label="新密码" required>
          <a-input-password v-model:value="newPassword" placeholder="请输入新密码（至少6位）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getEmployees, createEmployee, updateEmployee, resetEmployeePassword, updateEmployeeStatus, type EmployeeVO } from '@/api/employee'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 100 },
  { title: '手机号', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '角色', dataIndex: 'roleName', key: 'roleName', width: 120 },
  { title: '授权停车场', key: 'parkingLotNames', width: 200 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<EmployeeVO[]>([])
const queryStatus = ref('')
const parkingLotOptions = ref<ParkingLotVO[]>([])

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const formModalOpen = ref(false)
const formLoading = ref(false)
const formModalTitle = ref('')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive({
  displayName: '', phone: '', password: '',
  roleCode: 'parking_manager', parkingLotIds: [] as number[],
})

const pwdModalOpen = ref(false)
const pwdLoading = ref(false)
const pwdTarget = ref<EmployeeVO | null>(null)
const newPassword = ref('')

async function fetchData() {
  loading.value = true
  try {
    const res = await getEmployees({ page: pagination.current, size: pagination.pageSize, status: queryStatus.value || undefined })
    dataSource.value = res.records
    pagination.total = res.total
  } finally { loading.value = false }
}

async function loadParkingLots() {
  const res = await getParkingLots({ current: 1, size: 100 })
  parkingLotOptions.value = res.records.filter(l => l.status === 1)
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() { queryStatus.value = ''; pagination.current = 1; fetchData() }
function handleTableChange(pag: any) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData()
}

function handleCreate() {
  isEditing.value = false; editingId.value = null
  formModalTitle.value = '新增员工'
  formData.displayName = ''; formData.phone = ''; formData.password = ''
  formData.roleCode = 'parking_manager'; formData.parkingLotIds = []
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true; editingId.value = record.id
  formModalTitle.value = `编辑员工 — ${record.displayName}`
  formData.displayName = record.displayName
  formData.phone = record.phone
  formData.password = ''
  formData.roleCode = record.roleCode
  formData.parkingLotIds = [...(record.parkingLotIds || [])]
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.displayName.trim()) { message.warning('请输入姓名'); return }
  if (!isEditing.value) {
    if (!formData.phone.trim()) { message.warning('请输入手机号'); return }
    if (!formData.password) { message.warning('请输入密码'); return }
    if (formData.password.length < 6) { message.warning('密码至少6位'); return }
  }
  if (!formData.roleCode) { message.warning('请选择角色'); return }
  if (!formData.parkingLotIds.length) { message.warning('请选择授权停车场'); return }
  formLoading.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateEmployee(editingId.value, {
        displayName: formData.displayName.trim(),
        roleCode: formData.roleCode,
        parkingLotIds: formData.parkingLotIds,
      })
      message.success('更新成功')
    } else {
      await createEmployee({
        displayName: formData.displayName.trim(),
        phone: formData.phone.trim(),
        password: formData.password,
        roleCode: formData.roleCode,
        parkingLotIds: formData.parkingLotIds,
      })
      message.success('创建成功')
    }
    formModalOpen.value = false
    fetchData()
  } catch { /* 错误在拦截器处理 */ } finally { formLoading.value = false }
}

function handleResetPassword(record: any) {
  pwdTarget.value = record; newPassword.value = ''; pwdModalOpen.value = true
}

async function handlePwdConfirm() {
  if (!newPassword.value || newPassword.value.length < 6) { message.warning('密码至少6位'); return }
  pwdLoading.value = true
  try {
    await resetEmployeePassword(pwdTarget.value!.id, newPassword.value)
    message.success('密码重置成功')
    pwdModalOpen.value = false
  } catch { /* */ } finally { pwdLoading.value = false }
}

function handleToggleStatus(record: any, action: string) {
  updateEmployeeStatus(record.id, action).then(() => {
    message.success(action === 'ENABLED' ? '已启用' : '已禁用')
    fetchData()
  }).catch(() => {})
}

onMounted(() => {
  loadParkingLots()
  fetchData()
})
</script>

<style lang="scss" scoped>
.employee-page { background: #fff; border-radius: $border-radius-base; padding: $spacing-lg; }
.query-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: $spacing-lg; }
</style>
