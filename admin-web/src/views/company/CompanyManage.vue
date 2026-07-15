<template>
  <div class="company-page">
    <!-- 查询区 + 操作区 -->
    <div class="query-bar">
      <a-space>
        <a-input
          v-model:value="queryName"
          placeholder="请输入公司名称"
          allow-clear
          style="width: 220px"
          @press-enter="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button @click="handleReset">
          <template #icon><ReloadOutlined /></template>
          重置
        </a-button>
      </a-space>

      <a-button v-permission="'company:create'" type="primary" @click="handleCreate">
        <template #icon><PlusOutlined /></template>
        新增公司
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
        <!-- 级别列 -->
        <template v-if="column.key === 'level'">
          <a-tag>{{ levelLabel(record.level) }}</a-tag>
        </template>

        <!-- 状态列 -->
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>

        <!-- 操作列 -->
        <template v-if="column.key === 'action'">
          <a-space>
            <PermissionButton value="company:update">
              <a @click="handleEdit(record)">编辑</a>
            </PermissionButton>
            <PermissionButton value="company:delete">
              <a style="color: #dc2626" @click="handleDelete(record)">删除</a>
            </PermissionButton>
          </a-space>
        </template>
      </template>

      <!-- 空数据状态 -->
      <template #emptyText>
        <a-empty description="暂无公司数据" />
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <CompanyFormModal v-model:open="formModalOpen" :editing-record="editingRecord" @success="fetchData" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getCompanyPage, deleteCompany, type CompanyVO } from '@/api/company'
import { vPermission } from '@/directives/permission'
import PermissionButton from '@/components/PermissionButton.vue'
import CompanyFormModal from './CompanyFormModal.vue'

/** 表格行类型：后端 VO 可能携带状态字段 */
type CompanyTableRow = CompanyVO & { status?: string }

const columns = [
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80 },
  { title: '公司名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '级别', key: 'level', width: 90 },
  { title: '联系人', dataIndex: 'contactName', key: 'contactName', width: 110 },
  { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone', width: 140 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<CompanyTableRow[]>([])
const queryName = ref('')

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const formModalOpen = ref(false)
const editingRecord = ref<CompanyVO | null>(null)

const LEVEL_MAP: Record<number, string> = {
  1: '集团',
  2: '公司',
  3: '分公司',
}

function levelLabel(level: number) {
  return LEVEL_MAP[level] || `级别${level}`
}

function statusColor(status?: string) {
  return status === 'DISABLED' ? 'red' : 'green'
}

function statusLabel(status?: string) {
  return status === 'DISABLED' ? '禁用' : '启用'
}

/**
 * 加载公司分页列表。
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getCompanyPage({
      current: pagination.current,
      size: pagination.pageSize,
      name: queryName.value?.trim() || undefined,
    })
    dataSource.value = (res.records || []) as CompanyTableRow[]
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.current = 1
  fetchData()
}

function handleReset() {
  queryName.value = ''
  pagination.current = 1
  fetchData()
}

function handleTableChange(pag: any) {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchData()
}

function handleCreate() {
  editingRecord.value = null
  formModalOpen.value = true
}

function handleEdit(record: any) {
  editingRecord.value = record as CompanyTableRow
  formModalOpen.value = true
}

/**
 * 删除公司：二次确认后调用后端接口。
 */
function handleDelete(record: any) {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除公司「${record.name}」吗？删除后不可恢复。`,
    okText: '删除',
    okType: 'danger',
    async onOk() {
      try {
        await deleteCompany(record.id)
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
.company-page {
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
