<template>
  <a-modal
    :open="open"
    :title="isEditing ? '编辑账号' : '新增账号'"
    :confirm-loading="submitLoading"
    width="600px"
    @ok="handleSubmit"
    @update:open="(val: boolean) => emit('update:open', val)"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <a-form-item label="账号" name="username">
        <a-input
          v-model:value="formData.username"
          placeholder="请输入登录账号"
          :disabled="isEditing"
          :maxlength="64"
          @blur="handleUsernameBlur"
        />
      </a-form-item>
      <a-form-item label="姓名" name="realName">
        <a-input v-model:value="formData.realName" placeholder="请输入真实姓名" :maxlength="64" />
      </a-form-item>
      <a-form-item label="手机号" name="phone">
        <a-input v-model:value="formData.phone" placeholder="请输入手机号" :maxlength="11" />
      </a-form-item>
      <a-form-item label="邮箱" name="email">
        <a-input v-model:value="formData.email" placeholder="请输入邮箱" :maxlength="128" />
      </a-form-item>
      <a-form-item label="级别" name="level">
        <a-select v-model:value="formData.level" placeholder="请选择管理员级别" @change="(val: any) => handleLevelChange(val as number)">
          <a-select-option v-for="opt in levelOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="所属公司" name="companyId">
        <a-tree-select
          v-model:value="formData.companyId"
          :tree-data="companyTreeData"
          placeholder="请选择所属公司"
          allow-clear
          tree-default-expand-all
          :disabled="companyDisabled"
          :field-names="{ children: 'children', label: 'name', value: 'id' }"
        />
      </a-form-item>
      <a-form-item v-if="formData.level === 3" label="所属车场" name="lotId">
        <a-select v-model:value="formData.lotId" placeholder="请选择所属车场" allow-clear>
          <a-select-option v-for="lot in parkingLots" :key="lot.id" :value="lot.id">
            {{ lot.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="角色" name="roleIds">
        <a-select
          v-model:value="formData.roleIds"
          mode="multiple"
          placeholder="请选择角色"
          allow-clear
        >
          <a-select-option v-for="role in roleList" :key="role.id" :value="role.id">
            {{ role.roleName }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="状态" name="status">
        <a-radio-group v-model:value="formData.status">
          <a-radio :value="0">正常</a-radio>
          <a-radio :value="1">禁用</a-radio>
        </a-radio-group>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  createAdminAccount,
  updateAdminAccount,
  getAdminAccountPage,
  getCustomRoleList,
  type AdminAccountVO,
} from '@/api/account'
import { getCompanyTree } from '@/api/company'
import { getParkingLots } from '@/api/parking-lot'
import type { CompanyVO } from '@/api/company'

const props = defineProps<{
  open: boolean
  record?: AdminAccountVO
  currentLevel: number
}>()

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
  (e: 'success'): void
}>()

// 表单引用
const formRef = ref<FormInstance>()
const submitLoading = ref(false)

// 是否为编辑态
const isEditing = computed(() => !!props.record?.id)

// 表单数据
const formData = reactive<{
  id?: number
  username: string
  realName: string
  phone: string
  email: string
  level: number | undefined
  companyId: number | undefined
  lotId: number | undefined
  roleIds: number[]
  status: number
}>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  level: undefined,
  companyId: undefined,
  lotId: undefined,
  roleIds: [],
  status: 0,
})

// 级别选项
const levelOptions = computed(() => {
  // 二级管理员只能创建三级账号
  if (props.currentLevel === 2) {
    return [{ value: 3, label: '停车场' }]
  }
  return [
    { value: 1, label: '平台' },
    { value: 2, label: '公司' },
    { value: 3, label: '停车场' },
  ]
})

// 公司树数据
const companyTreeData = ref<CompanyVO[]>([])
// 停车场列表
const parkingLots = ref<{ id: number; name: string }[]>([])
// 角色列表
const roleList = ref<{ id: number; roleName: string }[]>([])

// 公司选择是否禁用（未选择级别时禁用）
const companyDisabled = computed(() => !formData.level)

// 手机号正则
const PHONE_PATTERN = /^1[3-9]\d{9}$/
// 邮箱正则
const EMAIL_PATTERN = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/

// 动态校验规则
const formRules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { max: 64, message: '账号长度不能超过64个字符', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 64, message: '姓名长度不能超过64个字符', trigger: 'blur' },
  ],
  phone: [
    { pattern: PHONE_PATTERN, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  email: [
    { pattern: EMAIL_PATTERN, message: '请输入正确的邮箱', trigger: 'blur' },
    { max: 128, message: '邮箱长度不能超过128个字符', trigger: 'blur' },
  ],
  level: [{ required: true, message: '请选择级别', trigger: 'change' }],
  companyId: [
    {
      required: true,
      validator: (_rule: any, value: number | undefined) => {
        if ((formData.level === 2 || formData.level === 3) && !value) {
          return Promise.reject(new Error('请选择所属公司'))
        }
        return Promise.resolve()
      },
      trigger: 'change',
    },
  ],
  lotId: [
    {
      required: true,
      validator: (_rule: any, value: number | undefined) => {
        if (formData.level === 3 && !value) {
          return Promise.reject(new Error('请选择所属车场'))
        }
        return Promise.resolve()
      },
      trigger: 'change',
    },
  ],
}

// 级别变化时重置公司、车场
function handleLevelChange(level: number) {
  formData.companyId = undefined
  formData.lotId = undefined
  if (level !== 3) {
    formData.lotId = undefined
  }
}

// 账号失焦唯一性校验
async function handleUsernameBlur() {
  if (!formData.username || isEditing.value) return
  try {
    const res = await getAdminAccountPage({
      current: 1,
      size: 1,
      keyword: formData.username,
    })
    if (res.records && res.records.length > 0) {
      message.warning('该账号已存在')
    }
  } catch {
    // 校验失败不阻塞，提交时后端会再次校验
  }
}

// 加载公司树
async function loadCompanyTree() {
  try {
    companyTreeData.value = await getCompanyTree()
  } catch (e) {
    message.error('加载公司树失败')
  }
}

// 加载停车场列表
async function loadParkingLots() {
  try {
    const res = await getParkingLots({ current: 1, size: 1000 })
    parkingLots.value = res.records || []
  } catch (e) {
    message.error('加载停车场列表失败')
  }
}

// 加载角色列表
async function loadRoles() {
  try {
    const res = await getCustomRoleList({ current: 1, size: 1000 })
    roleList.value = res.records || []
  } catch (e) {
    message.error('加载角色列表失败')
  }
}

// 重置表单
function resetForm() {
  formData.id = undefined
  formData.username = ''
  formData.realName = ''
  formData.phone = ''
  formData.email = ''
  formData.level = undefined
  formData.companyId = undefined
  formData.lotId = undefined
  formData.roleIds = []
  formData.status = 0
  formRef.value?.resetFields()
}

// 回填表单
function fillForm(record: AdminAccountVO) {
  formData.id = record.id
  formData.username = record.username
  formData.realName = record.realName || ''
  formData.phone = record.phone || ''
  formData.email = record.email || ''
  formData.level = record.level
  formData.companyId = record.companyId
  formData.lotId = record.lotId
  formData.roleIds = record.roleIds || []
  formData.status = record.status ?? 0
}

// 监听弹窗打开状态
watch(
  () => props.open,
  (open) => {
    if (open) {
      if (props.record) {
        fillForm(props.record)
      } else {
        resetForm()
        // 二级管理员默认级别为停车场
        if (props.currentLevel === 2) {
          formData.level = 3
        }
      }
    }
  },
)

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    if (isEditing.value && formData.id) {
      await updateAdminAccount(formData.id, {
        id: formData.id,
        realName: formData.realName.trim(),
        phone: formData.phone || undefined,
        email: formData.email || undefined,
        level: formData.level!,
        companyId: formData.companyId,
        lotId: formData.lotId,
        status: formData.status,
        roleIds: formData.roleIds,
      })
      message.success('更新成功')
    } else {
      await createAdminAccount({
        username: formData.username.trim(),
        realName: formData.realName.trim(),
        phone: formData.phone || undefined,
        email: formData.email || undefined,
        level: formData.level!,
        companyId: formData.companyId,
        lotId: formData.lotId,
        status: formData.status,
        roleIds: formData.roleIds,
      })
      message.success('创建成功')
    }
    emit('update:open', false)
    emit('success')
  } catch {
    // 错误已在拦截器统一处理
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadCompanyTree()
  loadParkingLots()
  loadRoles()
})
</script>
