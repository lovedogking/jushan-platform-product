<template>
  <div class="custom-role-page">
    <!-- 查询区 -->
    <div class="query-bar">
      <a-space>
        <a-input
          v-model:value="queryKeyword"
          placeholder="角色名称/编码"
          allow-clear
          style="width: 220px"
          @press-enter="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>重置
        </a-button>
      </a-space>
      <permission-button value="role:create">
        <a-button type="primary" @click="handleCreate">
          <template #icon><PlusOutlined /></template>新增角色
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
        <template v-if="column.key === 'action'">
          <a-space>
            <permission-button value="role:update">
              <a @click="handleEdit(record)">编辑</a>
            </permission-button>
            <permission-button value="role:update">
              <a @click="handleConfigPermission(record)">配置权限</a>
            </permission-button>
            <permission-button value="role:delete">
              <a style="color: #dc2626" @click="handleDelete(record)">删除</a>
            </permission-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑角色弹窗 -->
    <a-modal
      v-model:open="roleFormOpen"
      :title="isEditing ? '编辑角色' : '新增角色'"
      :confirm-loading="roleFormLoading"
      width="560px"
      @ok="handleRoleFormSubmit"
    >
      <a-form :model="roleForm" :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="角色名称" required>
          <a-input v-model:value="roleForm.roleName" placeholder="请输入角色名称" :maxlength="64" />
        </a-form-item>
        <a-form-item label="角色编码" required>
          <a-input
            v-model:value="roleForm.roleCode"
            placeholder="请输入角色编码"
            :maxlength="64"
            :disabled="isEditing"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="roleForm.description" placeholder="请输入角色描述" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 权限矩阵弹窗 -->
    <a-modal
      v-model:open="permissionModalOpen"
      title="配置权限"
      width="720px"
      :footer="null"
      destroy-on-close
    >
      <permission-matrix
        :role-id="currentRoleId!"
        @success="handlePermissionSuccess"
        @cancel="permissionModalOpen = false"
      />
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
  getCustomRoleList,
  createCustomRole,
  updateCustomRole,
  deleteCustomRole,
  type CustomRoleVO,
} from '@/api/account'
import PermissionButton from '@/components/PermissionButton.vue'
import PermissionMatrix from './PermissionMatrix.vue'

const columns = [
  { title: '角色名称', dataIndex: 'roleName', key: 'roleName', width: 160 },
  { title: '角色编码', dataIndex: 'roleCode', key: 'roleCode', width: 160 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<CustomRoleVO[]>([])
const queryKeyword = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

// 角色表单弹窗
const roleFormOpen = ref(false)
const roleFormLoading = ref(false)
const isEditing = ref(false)
const editingRoleId = ref<number | null>(null)
const roleForm = reactive({
  roleName: '',
  roleCode: '',
  description: '',
})

// 权限矩阵弹窗
const permissionModalOpen = ref(false)
const currentRoleId = ref<number | null>(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getCustomRoleList({
      current: pagination.current,
      size: pagination.pageSize,
      keyword: queryKeyword.value || undefined,
    })
    dataSource.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    // 错误已在拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryKeyword.value = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function resetRoleForm() {
  roleForm.roleName = ''
  roleForm.roleCode = ''
  roleForm.description = ''
  isEditing.value = false
  editingRoleId.value = null
}

function handleCreate() {
  resetRoleForm()
  roleFormOpen.value = true
}

function handleEdit(record: any) {
  const row = record as CustomRoleVO
  isEditing.value = true
  editingRoleId.value = row.id
  roleForm.roleName = row.roleName
  roleForm.roleCode = row.roleCode
  roleForm.description = row.description || ''
  roleFormOpen.value = true
}

async function handleRoleFormSubmit() {
  if (!roleForm.roleName.trim()) {
    message.warning('请输入角色名称')
    return
  }
  if (!roleForm.roleCode.trim()) {
    message.warning('请输入角色编码')
    return
  }

  roleFormLoading.value = true
  try {
    if (isEditing.value && editingRoleId.value) {
      await updateCustomRole(editingRoleId.value, {
        id: editingRoleId.value,
        roleName: roleForm.roleName.trim(),
        roleCode: roleForm.roleCode.trim(),
        description: roleForm.description.trim(),
      })
      message.success('更新成功')
    } else {
      await createCustomRole({
        roleName: roleForm.roleName.trim(),
        roleCode: roleForm.roleCode.trim(),
        description: roleForm.description.trim(),
      })
      message.success('创建成功')
    }
    roleFormOpen.value = false
    fetchData()
  } catch {
    // 错误已在拦截器统一处理
  } finally {
    roleFormLoading.value = false
  }
}

function handleConfigPermission(record: any) {
  const row = record as CustomRoleVO
  currentRoleId.value = row.id
  permissionModalOpen.value = true
}

function handlePermissionSuccess() {
  message.success('权限已更新，重新登录后生效')
  permissionModalOpen.value = false
}

function handleDelete(record: any) {
  const row = record as CustomRoleVO
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除角色 "${row.roleName}" 吗？删除后不可恢复。`,
    okText: '确认',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteCustomRole(row.id)
        message.success('删除成功')
        fetchData()
      } catch {
        // 错误已在拦截器统一处理
      }
    },
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.custom-role-page {
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
