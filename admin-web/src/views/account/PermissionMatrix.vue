<template>
  <div class="permission-matrix">
    <div class="matrix-toolbar">
      <a-space>
        <a-button size="small" @click="handleSelectAll(true)">全选</a-button>
        <a-button size="small" @click="handleSelectAll(false)">全不选</a-button>
      </a-space>
      <span class="permission-tip">权限变更后，用户需重新登录方可生效</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="matrixData"
      :loading="loading"
      :pagination="false"
      row-key="moduleCode"
      bordered
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'moduleName'">
          <strong>{{ (record as MatrixRow).moduleName }}</strong>
        </template>
        <template v-else-if="column.key === 'selectAll'">
          <a-checkbox
            :checked="isModuleAllSelected(record as MatrixRow)"
            :indeterminate="isModuleIndeterminate(record as MatrixRow)"
            @change="(e: any) => handleModuleSelectAll(record as MatrixRow, e.target.checked)"
          >
            全选
          </a-checkbox>
        </template>
        <template v-else-if="operationKeys.includes(column.key as string)">
          <a-checkbox
            :checked="(record as MatrixRow).permissions[column.key as string]"
            @change="(e: any) => handlePermissionChange((record as MatrixRow).moduleCode, column.key as string, e.target.checked)"
          >
            {{ column.title }}
          </a-checkbox>
        </template>
      </template>
    </a-table>

    <div class="matrix-actions">
      <a-space>
        <a-button :loading="saveLoading" type="primary" @click="handleSave">保存</a-button>
        <a-button @click="emit('cancel')">取消</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import {
  getRolePermissions,
  saveRolePermissions,
  type RolePermissionVO,
} from '@/api/account'

const props = defineProps<{
  roleId: number
}>()

const emit = defineEmits<{
  (e: 'success'): void
  (e: 'cancel'): void
}>()

// 权限操作类型
const OPERATIONS = [
  { key: 'view', label: '查看' },
  { key: 'create', label: '新增' },
  { key: 'update', label: '编辑' },
  { key: 'delete', label: '删除' },
  { key: 'export', label: '导出' },
]

const operationKeys = OPERATIONS.map(op => op.key)

// 权限模块定义
const MODULES = [
  { code: 'company', name: '公司管理' },
  { code: 'account', name: '账号管理' },

]

// 表格列定义
const columns: any[] = [
  { title: '模块', key: 'moduleName', width: 140 },
  { title: '全选', key: 'selectAll', width: 80, align: 'center' as const },
  ...OPERATIONS.map(op => ({
    title: op.label,
    key: op.key,
    width: 100,
    align: 'center' as const,
  })),
]

interface MatrixRow {
  moduleCode: string
  moduleName: string
  permissions: Record<string, boolean>
}

const loading = ref(false)
const saveLoading = ref(false)
const matrixData = reactive<MatrixRow[]>(
  MODULES.map(m => ({
    moduleCode: m.code,
    moduleName: m.name,
    permissions: Object.fromEntries(OPERATIONS.map(op => [op.key, false])),
  })),
)

// 获取指定模块行
function getRow(moduleCode: string) {
  return matrixData.find(row => row.moduleCode === moduleCode)
}

// 判断模块是否全选
function isModuleAllSelected(row: MatrixRow) {
  return operationKeys.every(key => row.permissions[key])
}

// 判断模块是否部分选中
function isModuleIndeterminate(row: MatrixRow) {
  const selectedCount = operationKeys.filter(key => row.permissions[key]).length
  return selectedCount > 0 && selectedCount < operationKeys.length
}

// 模块全选/全不选
function handleModuleSelectAll(row: MatrixRow, checked: boolean) {
  operationKeys.forEach(key => {
    row.permissions[key] = checked
  })
}

// 单个权限变化
function handlePermissionChange(moduleCode: string, opKey: string, checked: boolean) {
  const row = getRow(moduleCode)
  if (row) {
    row.permissions[opKey] = checked
  }
}

// 全局全选/全不选
function handleSelectAll(checked: boolean) {
  matrixData.forEach(row => {
    operationKeys.forEach(key => {
      row.permissions[key] = checked
    })
  })
}

// 加载角色权限
async function loadPermissions() {
  loading.value = true
  try {
    const res = await getRolePermissions(props.roleId)
    // 重置为未选中
    matrixData.forEach(row => {
      operationKeys.forEach(key => {
        row.permissions[key] = false
      })
    })
    // 回填已有权限
    res.forEach((item: RolePermissionVO) => {
      const row = getRow(item.permissionCode)
      if (row && item.permissionType) {
        row.permissions[item.permissionType] = true
      }
    })
  } catch {
    message.error('加载权限矩阵失败')
  } finally {
    loading.value = false
  }
}

// 保存权限
async function handleSave() {
  const permissions: RolePermissionVO[] = []
  matrixData.forEach(row => {
    operationKeys.forEach(key => {
      if (row.permissions[key]) {
        permissions.push({
          permissionCode: row.moduleCode,
          permissionType: key,
        })
      }
    })
  })

  saveLoading.value = true
  try {
    await saveRolePermissions(props.roleId, permissions)
    message.success('权限已更新，重新登录后生效')
    emit('success')
  } catch {
    // 错误已在拦截器统一处理
  } finally {
    saveLoading.value = false
  }
}

onMounted(() => {
  loadPermissions()
})
</script>

<style lang="scss" scoped>
.permission-matrix {
  .matrix-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
  }

  .permission-tip {
    color: #999;
    font-size: 12px;
  }

  .matrix-actions {
    margin-top: 16px;
    text-align: right;
  }
}
</style>
