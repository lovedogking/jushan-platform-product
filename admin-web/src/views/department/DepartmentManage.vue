<template>
  <div class="department-manage">
    <div class="page-header">
      <h2>部门管理</h2>
      <a-button type="primary" @click="handleAdd" v-permission="'department:create'">
        <plus-outlined /> 新增部门
      </a-button>
    </div>

    <a-row :gutter="16">
      <!-- 左侧部门树 -->
      <a-col :span="6">
        <a-card title="部门架构" :bordered="false">
          <a-tree
            :tree-data="treeData"
            :field-names="{ title: 'name', key: 'id', children: 'children' }"
            @select="handleTreeSelect"
            block-node
          />
        </a-card>
      </a-col>

      <!-- 右侧部门列表 -->
      <a-col :span="18">
        <a-card :bordered="false">
          <a-form layout="inline" :model="searchForm" class="search-form">
            <a-form-item label="部门名称">
              <a-input v-model:value="searchForm.name" placeholder="请输入部门名称" allow-clear />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="handleSearch">
                <search-outlined /> 查询
              </a-button>
              <a-button @click="handleReset" style="margin-left: 8px">
                重置
              </a-button>
            </a-form-item>
          </a-form>

          <a-table
            :columns="columns"
            :data-source="tableData"
            :loading="loading"
            :pagination="pagination"
            @change="handleTableChange"
            row-key="id"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" @click="handleEdit(record)" v-permission="'department:update'">
                    编辑
                  </a-button>
                  <a-popconfirm
                    title="确定删除该部门吗？"
                    @confirm="handleDelete(record)"
                    v-permission="'department:delete'"
                  >
                    <a-button type="link" danger>删除</a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>

    <!-- 新增/编辑弹窗 -->
    <department-form-modal
      v-model:visible="modalVisible"
      :record="currentRecord"
      :tree-data="treeData"
      @success="handleModalSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import DepartmentFormModal from './DepartmentFormModal.vue'
import { getDepartmentTree, getDepartmentPage, deleteDepartment } from '@/api/department'

const loading = ref(false)
const modalVisible = ref(false)
const currentRecord = ref(null)
const treeData = ref([])
const tableData = ref([])

const searchForm = reactive({
  name: '',
  parentId: undefined as number | undefined
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

const columns = [
  { title: '部门名称', dataIndex: 'name', key: 'name' },
  { title: '部门编码', dataIndex: 'code', key: 'code' },
  { title: '负责人', dataIndex: 'managerName', key: 'managerName' },
  { title: '联系电话', dataIndex: 'contactPhone', key: 'contactPhone' },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action', width: 150 }
]

const loadTree = async () => {
  try {
    const res = await getDepartmentTree()
    treeData.value = res.data || []
  } catch (e) {
    console.error(e)
  }
}

const loadTable = async () => {
  loading.value = true
  try {
    const res = await getDepartmentPage({
      current: pagination.current,
      size: pagination.pageSize,
      name: searchForm.name || undefined,
      parentId: searchForm.parentId
    })
    tableData.value = res.data?.records || []
    pagination.total = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadTable()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.parentId = undefined
  pagination.current = 1
  loadTable()
}

const handleTreeSelect = (selectedKeys: number[]) => {
  searchForm.parentId = selectedKeys[0]
  pagination.current = 1
  loadTable()
}

const handleTableChange = (pag: any) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  loadTable()
}

const handleAdd = () => {
  currentRecord.value = null
  modalVisible.value = true
}

const handleEdit = (record: any) => {
  currentRecord.value = record
  modalVisible.value = true
}

const handleDelete = async (record: any) => {
  try {
    await deleteDepartment(record.id)
    message.success('删除成功')
    loadTable()
    loadTree()
  } catch (e) {
    message.error('删除失败')
  }
}

const handleModalSuccess = () => {
  loadTable()
  loadTree()
}

onMounted(() => {
  loadTree()
  loadTable()
})
</script>

<style scoped lang="scss">
.department-manage {
  padding: 24px;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
  }

  .search-form {
    margin-bottom: 16px;
  }
}
</style>
