<template>
  <div class="admin-account-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-input
          v-model:value="queryKeyword"
          placeholder="账号/姓名/手机号"
          allow-clear
          style="width: 220px"
          @press-enter="handleQuery"
        />
        <a-select
          v-model:value="queryStatus"
          placeholder="全部状态"
          allow-clear
          style="width: 130px"
        >
          <a-select-option value="">全部</a-select-option>
          <a-select-option :value="0">正常</a-select-option>
          <a-select-option :value="1">禁用</a-select-option>
          <a-select-option :value="2">锁定</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <permission-button value="account:create">
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>新增账号
        </a-button>
      </permission-button>
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
        <template v-if="column.key === 'level'">
          {{ levelLabel(record.level) }}
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-if="column.key === 'companyName'">
          {{ record.companyId ? companyMap.get(String(record.companyId)) || '-' : '-' }}
        </template>
        <template v-if="column.key === 'lastLoginTime'">
          {{ record.lastLoginTime || '-' }}
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a v-permission="'account:update'" @click="handleEdit(record)">编辑</a>
            <a v-permission="'account:update'" @click="handleResetPassword(record)">重置密码</a>
            <a v-permission="'account:update'" v-if="record.status === 1" @click="handleToggleStatus(record, 0)">禁用</a>
            <a
              v-permission="'account:update'"
              v-else-if="record.status !== 2"
              style="color: #dc2626"
              @click="handleToggleStatus(record, 1)"
            >启用</a>
            <a v-permission="'account:delete'" style="color: #dc2626" @click="handleDelete(record)">删除</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <admin-account-form-modal
      v-model:open="formModalOpen"
      :record="editingRecord"
      :current-level="currentLevel"
      @success="handleFormSuccess"
    />

    <!-- 重置密码结果弹窗 -->
    <a-modal
      v-model:open="resetPasswordModalOpen"
      title="重置密码成功"
      :footer="null"
      width="400px"
    >
      <p>账号：{{ resetPasswordTarget?.username }}</p>
      <p>新密码：<strong>{{ resetPasswordResult }}</strong></p>
      <p class="text-muted">请妥善保存密码，关闭后将无法再次查看。</p>
    </a-modal>

    <!-- 创建成功显示密码 -->
    <a-modal
      v-model:open="createdPasswordModalOpen"
      title="账号创建成功"
      :footer="null"
      :closable="false"
      width="480px"
    >
      <a-result status="success" title="账号创建成功" sub-title="初始密码仅在本次显示，请妥善保存">
        <template #extra>
          <div style="text-align: left; background: #f6f8fa; padding: 16px; border-radius: 6px; margin-top: 16px;">
            <p><strong>账号：</strong>{{ createdAccountInfo?.username }}</p>
            <p><strong>初始密码：</strong><code style="font-size: 18px; letter-spacing: 2px;">{{ createdAccountInfo?.plainPassword }}</code></p>
          </div>
          <a-button type="primary" style="margin-top: 16px;" @click="createdPasswordModalOpen = false">我已保存，关闭</a-button>
        </template>
      </a-result>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import {
  getAdminAccountPage,
  updateAdminAccount,
  deleteAdminAccount,
  resetAdminPassword,
  type AdminAccountVO,
} from '@/api/account'
import { getCompanyTree } from '@/api/company'
import type { CompanyVO } from '@/api/company'
import { useAuthStore } from '@/stores'
import { vPermission } from '@/directives/permission'
import PermissionButton from '@/components/PermissionButton.vue'
import AdminAccountFormModal from './AdminAccountFormModal.vue'

const authStore = useAuthStore()
const currentLevel = computed(() => authStore.level)

const columns = [
  { title: '账号', dataIndex: 'username', key: 'username', width: 130 },
  { title: '姓名', dataIndex: 'realName', key: 'realName', width: 120 },
  { title: '手机号', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '级别', key: 'level', width: 100 },
  { title: '所属公司', key: 'companyName', width: 160 },
  { title: '状态', key: 'status', width: 90 },
  { title: '最后登录时间', key: 'lastLoginTime', width: 170 },
  { title: '操作', key: 'action', width: 240, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<AdminAccountVO[]>([])
const companyMap = ref<Map<string | number, string>>(new Map())
const queryKeyword = ref('')
const queryStatus = ref<number | ''>('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 弹窗相关
const formModalOpen = ref(false)
const editingRecord = ref<AdminAccountVO | undefined>(undefined)

// 重置密码相关
const resetPasswordModalOpen = ref(false)
const resetPasswordTarget = ref<AdminAccountVO | null>(null)
const resetPasswordResult = ref('')

// 创建成功密码展示
const createdPasswordModalOpen = ref(false)
const createdAccountInfo = ref<{ username: string; plainPassword: string } | null>(null)

const LEVEL_LABELS_TENANT: Record<number, string> = {
  1: '租户',
  2: '公司',
  3: '停车场',
}
const LEVEL_LABELS_PLATFORM: Record<number, string> = {
  1: '平台',
  2: '公司',
  3: '停车场',
}

const STATUS_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '正常', color: 'green' },
  0: { label: '禁用', color: 'red' },
  2: { label: '锁定', color: 'orange' },
}

function levelLabel(level: number) {
  const map = authStore.tenantId ? LEVEL_LABELS_TENANT : LEVEL_LABELS_PLATFORM
  return map[level] || '未知'
}

function statusLabel(status: number) {
  return STATUS_MAP[status]?.label || '未知'
}

function statusColor(status: number) {
  return STATUS_MAP[status]?.color || 'default'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getAdminAccountPage({
      page: pagination.current,
      size: pagination.pageSize,
      keyword: queryKeyword.value || undefined,
      status: queryStatus.value === '' ? undefined : queryStatus.value,
    })
    dataSource.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    // 错误已在拦截器统一处理
  } finally {
    loading.value = false
  }
}

// 递归构建公司 ID 到名称的映射
function buildCompanyMap(nodes: CompanyVO[], map: Map<string | number, string>) {
  nodes.forEach((node) => {
    map.set(String(node.id), node.name)
    if (node.children && node.children.length > 0) {
      buildCompanyMap(node.children, map)
    }
  })
}

async function loadCompanyMap() {
  try {
    const tree = await getCompanyTree()
    const map = new Map<string | number, string>()
    buildCompanyMap(tree, map)
    companyMap.value = map
  } catch {
    // 公司映射加载失败不影响账号列表展示
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryKeyword.value = ''
  queryStatus.value = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function handleFormSuccess(result?: any) {
  if (result?.plainPassword) {
    createdAccountInfo.value = {
      username: result.username,
      plainPassword: result.plainPassword,
    }
    createdPasswordModalOpen.value = true
  }
  fetchData()
}

function handleCreate() {
  editingRecord.value = undefined
  formModalOpen.value = true
}

function handleEdit(record: any) {
  editingRecord.value = record as AdminAccountVO
  formModalOpen.value = true
}

function handleToggleStatus(record: any, targetStatus: number) {
  const row = record as AdminAccountVO
  const actionText = targetStatus === 1 ? '启用' : '禁用'
  Modal.confirm({
    title: `确认${actionText}`,
    content: `确定要${actionText}账号 "${row.username}" 吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        await updateAdminAccount(row.id, {
          id: row.id,
          realName: row.realName || '',
          phone: row.phone,
          email: row.email,
          level: row.level,
          companyId: row.companyId,
          lotId: row.lotId,
          status: targetStatus,
          roleIds: row.roleIds,
        })
        message.success(`${actionText}成功`)
        fetchData()
      } catch {
        // 错误已在拦截器统一处理
      }
    },
  })
}

function handleResetPassword(record: any) {
  const row = record as AdminAccountVO
  Modal.confirm({
    title: '确认重置密码',
    content: `确定要重置账号 "${row.username}" 的密码吗？`,
    okText: '确认',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await resetAdminPassword(row.id)
        resetPasswordTarget.value = row
        resetPasswordResult.value = res.plainPassword
        resetPasswordModalOpen.value = true
        fetchData()
      } catch {
        // 错误已在拦截器统一处理
      }
    },
  })
}

function handleDelete(record: any) {
  const row = record as AdminAccountVO
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除账号 "${row.username}" 吗？删除后不可恢复。`,
    okText: '确认',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteAdminAccount(row.id)
        message.success('删除成功')
        fetchData()
      } catch {
        // 错误已在拦截器统一处理
      }
    },
  })
}

onMounted(() => {
  loadCompanyMap()
  fetchData()
})
</script>

<style lang="scss" scoped>
.admin-account-page {
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

.text-muted {
  color: #999;
  font-size: 12px;
}
</style>
