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
        <template v-if="column.key === 'level'">
          <a-tag :color="record.level === 2 ? 'blue' : record.level === 3 ? 'orange' : 'purple'">
            {{ levelLabel(record.level) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : record.status === 2 ? 'orange' : 'red'">
            {{ record.status === 1 ? '启用' : record.status === 2 ? '锁定' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
        <template v-if="column.key === 'lastLoginTime'">{{ fmtTime(record.lastLoginTime) || '-' }}</template>
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

    <a-modal v-model:open="modalVisible" :title="isEdit ? '编辑账号' : '新建账号'"
      @ok="handleSave" :confirm-loading="saving" width="560px">
      <a-form :model="form" layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" :disabled="isEdit" placeholder="登录用户名" />
        </a-form-item>
        <a-form-item v-if="!isEdit" label="初始密码">
          <a-input-password v-model:value="form.password" placeholder="留空则系统自动生成 8 位随机密码" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="form.realName" placeholder="真实姓名" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model:value="form.phone" placeholder="手机号（可选）" />
        </a-form-item>
        <a-form-item label="邮箱">
          <a-input v-model:value="form.email" placeholder="邮箱（可选）" />
        </a-form-item>
        <a-form-item label="账号类型" required>
          <a-radio-group v-model:value="form.level" :disabled="isEdit" @change="onLevelChange">
            <a-radio :value="2">租户管理员</a-radio>
            <a-radio :value="3">岗亭管理员</a-radio>
          </a-radio-group>
        </a-form-item>
        <template v-if="form.level === 2">
          <a-form-item label="归属租户" required>
            <a-select v-model:value="form.tenantId" placeholder="选择租户" :options="tenantOptions"
              :field-names="{ label: 'name', value: 'id' }" show-search option-filter-prop="label"
              @change="onTenantChange" />
          </a-form-item>
          <a-form-item label="归属公司" required>
            <a-select v-model:value="form.companyId" placeholder="选择公司" :options="filteredCompanyOptions"
              :field-names="{ label: 'name', value: 'id' }" show-search option-filter-prop="label"
              :disabled="!form.tenantId" />
          </a-form-item>
        </template>
        <a-form-item label="归属停车场">
          <a-select v-model:value="form.parkingLotIds" mode="multiple" placeholder="选择停车场（可留空，之后编辑补绑）"
            :options="filteredLotOptions" :field-names="{ label: 'name', value: 'id' }"
            show-search option-filter-prop="label" />
        </a-form-item>
        <a-form-item label="绑定角色">
          <a-select v-model:value="form.roleIds" mode="multiple" placeholder="选择角色"
            :options="roleOptions" :field-names="{ label: 'roleName', value: 'id' }" />
        </a-form-item>
        <a-form-item v-if="form.level === 3">
          <a-checkbox v-model:checked="form.allowFeeReduction">允许费用减免</a-checkbox>
        </a-form-item>
        <a-form-item v-if="isEdit" label="状态" required>
          <a-radio-group v-model:value="form.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="pwdVisible" title="密码已重置" @ok="pwdVisible = false"
      :cancel-button-props="{ style: { display: 'none' } }">
      <p>新密码：<strong style="font-size:18px;color:#1677ff">{{ newPassword }}</strong></p>
      <p style="color:#999">请妥善保存，关闭后不可再次查看</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  getAdminAccounts, getAdminAccount, createAdminAccount, updateAdminAccount,
  deleteAdminAccount, resetPassword, getCustomRoles, getCompanies,
  type AdminAccount, type AdminAccountCreateCmd, type AdminAccountUpdateCmd,
  type CustomRoleVO, type CompanyOptionVO,
} from '@/api/account'
import { getTenants, getParkingLots, type TenantVO, type ParkingLotVO } from '@/api/parking-manage'

const loading = ref(false)
const saving = ref(false)
const accounts = ref<AdminAccount[]>([])
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const modalVisible = ref(false)
const isEdit = ref(false)
const editId = ref<string | null>(null)
const pwdVisible = ref(false)
const newPassword = ref('')

const tenantOptions = ref<TenantVO[]>([])
const lotOptions = ref<ParkingLotVO[]>([])
const companyOptions = ref<CompanyOptionVO[]>([])
const roleOptions = ref<CustomRoleVO[]>([])

const form = reactive({
  username: '', password: '', realName: '', phone: '', email: '',
  level: 2 as number,
  tenantId: undefined as number | undefined,
  companyId: undefined as number | undefined,
  parkingLotIds: [] as number[],
  roleIds: [] as number[],
  allowFeeReduction: false,
  status: 1,
})

const columns = [
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '姓名', dataIndex: 'realName', key: 'realName' },
  { title: '账号类型', key: 'level', width: 110 },
  { title: '状态', key: 'status', width: 80 },
  { title: '最后登录', dataIndex: 'lastLoginTime', key: 'lastLoginTime', width: 160 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 200 },
]

function levelLabel(level: number) {
  return level === 1 ? '平台管理员' : level === 2 ? '租户管理员' : level === 3 ? '岗亭管理员' : `级别${level}`
}

function fmtTime(t: string | null) {
  return t ? t.slice(0, 19).replace('T', ' ') : ''
}

/** 公司按选中租户过滤 */
const filteredCompanyOptions = computed(() =>
  companyOptions.value.filter(c => c.tenantId === form.tenantId))

/** 车场过滤：租户管理员限本租户车场；岗亭管理员跨租户可见全部 */
const filteredLotOptions = computed(() => {
  if (form.level === 2 && form.tenantId) {
    return lotOptions.value.filter(l => l.tenantId === form.tenantId)
  }
  return lotOptions.value
})

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

/** 加载表单下拉数据源 */
async function loadOptions() {
  const results = await Promise.allSettled([
    getTenants({ page: 1, size: 200 }),
    getParkingLots({ page: 1, size: 500 }),
    getCompanies({ current: 1, size: 500 }),
    getCustomRoles({ page: 1, size: 200 }),
  ])
  if (results[0].status === 'fulfilled') tenantOptions.value = results[0].value.records
  if (results[1].status === 'fulfilled') lotOptions.value = results[1].value.records
  if (results[2].status === 'fulfilled') companyOptions.value = results[2].value.records
  if (results[3].status === 'fulfilled') roleOptions.value = results[3].value.records
  const failed = results.findIndex(r => r.status === 'rejected')
  if (failed >= 0) message.warning('部分下拉数据加载失败，请刷新重试')
}

function doSearch() { pagination.current = 1; fetchAccounts() }
function onPageChange(pag: { current: number; pageSize: number }) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchAccounts()
}

function resetForm() {
  form.username = ''; form.password = ''; form.realName = ''; form.phone = ''; form.email = ''
  form.level = 2; form.tenantId = undefined; form.companyId = undefined
  form.parkingLotIds = []; form.roleIds = []
  form.allowFeeReduction = false; form.status = 1
}

function openCreate() {
  isEdit.value = false; editId.value = null
  resetForm()
  modalVisible.value = true
}

async function openEdit(record: AdminAccount) {
  isEdit.value = true; editId.value = record.id
  resetForm()
  form.username = record.username
  form.realName = record.realName || ''
  form.phone = record.phone || ''
  form.email = record.email || ''
  form.level = record.level
  form.tenantId = record.tenantId ?? undefined
  form.companyId = record.companyId ?? undefined
  form.status = record.status === 2 ? 1 : record.status
  form.allowFeeReduction = record.allowFeeReduction === 1
  modalVisible.value = true
  try {
    const detail = await getAdminAccount(record.id)
    form.roleIds = detail.roleIds || []
    form.parkingLotIds = detail.parkingLotIds || []
  } catch (e: any) {
    message.error(e.message || '获取账号详情失败')
  }
}

function onLevelChange() {
  form.tenantId = undefined; form.companyId = undefined; form.parkingLotIds = []
}

function onTenantChange() {
  form.companyId = undefined; form.parkingLotIds = []
}

async function handleSave() {
  if (!form.realName.trim()) { message.warning('请输入姓名'); return }
  if (form.level === 2 && !form.tenantId) { message.warning('请选择归属租户'); return }
  if (form.level === 2 && !form.companyId) { message.warning('请选择归属公司'); return }
  saving.value = true
  try {
    if (isEdit.value && editId.value) {
      const cmd: AdminAccountUpdateCmd = {
        realName: form.realName.trim(),
        phone: form.phone || undefined,
        email: form.email || undefined,
        level: form.level,
        companyId: form.companyId ?? undefined,
        status: form.status,
        roleIds: form.roleIds,
        parkingLotIds: form.parkingLotIds,
        allowFeeReduction: form.level === 3 && form.allowFeeReduction ? 1 : 0,
      }
      await updateAdminAccount(editId.value, cmd)
      message.success('已更新')
    } else {
      if (!form.username.trim()) { message.warning('请输入用户名'); saving.value = false; return }
      if (form.password && form.password.length < 6) { message.warning('密码长度至少 6 位'); saving.value = false; return }
      const cmd: AdminAccountCreateCmd = {
        username: form.username.trim(),
        password: form.password || undefined,
        realName: form.realName.trim(),
        phone: form.phone || undefined,
        email: form.email || undefined,
        level: form.level,
        tenantId: form.level === 2 ? form.tenantId : undefined,
        companyId: form.level === 2 ? form.companyId : undefined,
        roleIds: form.roleIds,
        parkingLotIds: form.parkingLotIds,
        allowFeeReduction: form.level === 3 && form.allowFeeReduction ? 1 : 0,
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
    newPassword.value = res.plainPassword; pwdVisible.value = true
  } catch (e: any) { message.error(e.message || '重置失败') }
}

async function handleDelete(record: AdminAccount) {
  try { await deleteAdminAccount(record.id); message.success('已删除'); fetchAccounts() }
  catch (e: any) { message.error(e.message || '删除失败') }
}

onMounted(() => { fetchAccounts(); loadOptions() })
</script>

<style lang="scss" scoped>
.accounts-page { padding: 0; }
.page-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
