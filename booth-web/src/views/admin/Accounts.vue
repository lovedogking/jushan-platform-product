<template>
  <div class="accounts-page">
    <div class="page-toolbar">
      <a-space>
        <a-input-search v-model:value="keyword" placeholder="搜索账号/姓名" @search="doSearch" allow-clear style="width:240px" />
        <a-select v-model:value="statusFilter" placeholder="状态" allow-clear style="width:100px" @change="doSearch">
          <a-select-option :value="1">启用</a-select-option>
          <a-select-option :value="0">停用</a-select-option>
        </a-select>
      </a-space>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        新建账号
      </a-button>
    </div>

    <a-table :columns="columns" :data-source="accounts" :loading="loading"
      :pagination="pagination" @change="onPageChange" row-key="id" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-if="column.key === 'roleNames'">
          <a-tag v-for="r in record.roleNames" :key="r" color="blue">{{ r }}</a-tag>
        </template>
        <template v-if="column.key === 'actions'">
          <a-space>
            <a @click="openEdit(record)">编辑</a>
            <a @click="handleResetPwd(record)">重置密码</a>
            <a-popconfirm title="确定删除此账号？" @confirm="handleDelete(record)">
              <a style="color:red">删除</a>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal v-model:visible="modalVisible" :title="isEdit ? '编辑账号' : '新建账号'"
      @ok="handleSave" :confirm-loading="saving" width="560px">
      <a-form :model="form" layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" :disabled="isEdit" placeholder="登录用户名" />
        </a-form-item>
        <a-form-item v-if="!isEdit" label="密码" required>
          <a-input-password v-model:value="form.password" placeholder="初始密码" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="form.displayName" placeholder="显示名称" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model:value="form.phone" placeholder="手机号" />
        </a-form-item>
        <a-form-item label="角色类型">
          <a-radio-group v-model:value="form.userType">
            <a-radio value="tenant">租户管理员</a-radio>
            <a-radio value="booth">岗亭管理员</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="归属停车场">
          <a-select v-model:value="form.parkingLotIds" mode="multiple" placeholder="选择停车场">
            <a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="form.userType === 'booth'" label="归属车场管理员">
          <a-select v-model:value="form.parentAccountId" placeholder="选择上级管理员" allow-clear>
            <a-select-option v-for="a in tenantAdminOptions" :key="a.id" :value="a.id">{{ a.displayName }} ({{ a.username }})</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="pwdVisible" title="密码已重置" @ok="pwdVisible = false"
      cancel-button-props="{ style: { display: 'none' } }">
      <p>新密码：<strong style="font-size:18px;color:#1677ff">{{ newPassword }}</strong></p>
      <p style="color:#999">请妥善保存，关闭后不可再次查看</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  getAdminAccounts, createAdminAccount, updateAdminAccount, deleteAdminAccount, resetPassword,
  type AdminAccount, type AdminAccountCreateCmd, type AdminAccountUpdateCmd
} from '@/api/account'

const loading = ref(false)
const saving = ref(false)
const accounts = ref<AdminAccount[]>([])
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const modalVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number | null>(null)
const pwdVisible = ref(false)
const newPassword = ref('')

const lotOptions = ref<{ id: number; name: string }[]>([])
const tenantAdminOptions = ref<{ id: number; username: string; displayName: string }[]>([])

const form = reactive({
  username: '', password: '', displayName: '', phone: '',
  userType: 'tenant' as string,
  parkingLotIds: [] as number[],
  parentAccountId: null as number | null,
})

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'displayName', key: 'displayName' },
  { title: '角色', key: 'roleNames' },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'actions', width: 200 },
]

async function fetchAccounts() {
  loading.value = true
  try {
    const res = await getAdminAccounts({
      page: pagination.current, size: pagination.pageSize,
      keyword: keyword.value || undefined, status: statusFilter.value,
    })
    accounts.value = res.records
    pagination.total = res.total
  } catch (e: any) {
    message.error(e.message || '获取账号列表失败')
  } finally { loading.value = false }
}

function doSearch() { pagination.current = 1; fetchAccounts() }
function onPageChange(pag: { current: number; pageSize: number }) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchAccounts()
}

function openCreate() {
  isEdit.value = false; editId.value = null
  form.username = ''; form.password = ''; form.displayName = ''; form.phone = ''
  form.userType = 'tenant'; form.parkingLotIds = []; form.parentAccountId = null
  modalVisible.value = true
}

function openEdit(record: AdminAccount) {
  isEdit.value = true; editId.value = record.id
  form.username = record.username
  form.displayName = record.displayName || ''
  form.phone = record.phone || ''
  form.parkingLotIds = record.parkingLotIds || []
  form.parentAccountId = record.parentAccountId || null
  modalVisible.value = true
}

async function handleSave() {
  if (!form.displayName) { message.warning('请输入姓名'); return }
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      const cmd: AdminAccountUpdateCmd = { displayName: form.displayName, phone: form.phone || undefined, parkingLotIds: form.parkingLotIds, parentAccountId: form.parentAccountId || undefined }
      await updateAdminAccount(editId.value, cmd)
      message.success('已更新')
    } else {
      if (!form.username || !form.password) { message.warning('请输入用户名和密码'); saving.value = false; return }
      const cmd: AdminAccountCreateCmd = {
        username: form.username, password: form.password,
        displayName: form.displayName, phone: form.phone || undefined,
        roleIds: [], parkingLotIds: form.parkingLotIds,
        parentAccountId: form.parentAccountId || undefined,
      }
      await createAdminAccount(cmd)
      message.success('已创建')
    }
    modalVisible.value = false; fetchAccounts()
  } catch (e: any) { message.error(e.message || '保存失败') } finally { saving.value = false }
}

async function handleResetPwd(record: AdminAccount) {
  try {
    const res = await resetPassword(record.id)
    newPassword.value = res.password; pwdVisible.value = true
  } catch (e: any) { message.error(e.message || '重置失败') }
}

async function handleDelete(record: AdminAccount) {
  try { await deleteAdminAccount(record.id); message.success('已删除'); fetchAccounts() }
  catch (e: any) { message.error(e.message || '删除失败') }
}

onMounted(() => { fetchAccounts() })
</script>

<style lang="scss" scoped>
.accounts-page { padding: 0; }
.page-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
