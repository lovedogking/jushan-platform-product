<template>
  <a-modal
    v-model:open="modelOpen"
    :title="modalTitle"
    :confirm-loading="submitLoading"
    width="600px"
    @ok="handleSubmit"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
    >
      <!-- 公司名称 -->
      <a-form-item label="公司名称" name="name">
        <a-input v-model:value="formData.name" placeholder="请输入公司名称" />
      </a-form-item>

      <!-- 公司级别 -->
      <a-form-item label="公司级别" name="level">
        <a-select v-model:value="formData.level" placeholder="请选择公司级别" @change="handleLevelChange">
          <a-select-option :value="1">集团</a-select-option>
          <a-select-option :value="2">公司</a-select-option>
          <a-select-option :value="3">分公司</a-select-option>
        </a-select>
      </a-form-item>

      <!-- 上级公司 -->
      <a-form-item label="上级公司" name="parentId">
        <CompanyTree
          v-model:value="formData.parentId"
          :tree-data="companyTree"
          :disabled-id="editingId"
          class="tree-panel"
        />
      </a-form-item>

      <!-- 联系人 -->
      <a-form-item label="联系人" name="contactName">
        <a-input v-model:value="formData.contactName" placeholder="请输入联系人" />
      </a-form-item>

      <!-- 联系电话 -->
      <a-form-item label="联系电话" name="contactPhone">
        <a-input v-model:value="formData.contactPhone" placeholder="请输入联系电话" />
      </a-form-item>

      <!-- 地址 -->
      <a-form-item label="地址" name="address">
        <a-textarea v-model:value="formData.address" placeholder="请输入地址" :rows="2" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, nextTick } from 'vue'
import { message, type FormInstance } from 'ant-design-vue'
import {
  createCompany,
  updateCompany,
  getCompanyTree,
  type CompanyVO,
  type CompanyCreateCmd,
} from '@/api/company'
import CompanyTree from './CompanyTree.vue'

interface CompanyForm {
  name: string
  level: number
  parentId: number | null
  contactName: string
  contactPhone: string
  address: string
}

const props = defineProps<{
  /** 弹窗是否显示 */
  open: boolean
  /** 当前编辑的记录，为 null 时表示新增 */
  editingRecord: CompanyVO | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const modalTitle = computed<string>(() =>
  props.editingRecord ? `编辑公司 — ${props.editingRecord.name}` : '新增公司',
)
const editingId = computed<number | null>(() => props.editingRecord?.id || null)

const modelOpen = computed<boolean>({
  get: () => props.open,
  set: (val) => emit('update:open', val),
})

const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const companyTree = ref<CompanyVO[]>([])

const formData = reactive<CompanyForm>({
  name: '',
  level: 2,
  parentId: null,
  contactName: '',
  contactPhone: '',
  address: '',
})

/**
 * 重置表单为初始状态。
 */
function resetForm() {
  formData.name = ''
  formData.level = 2
  formData.parentId = null
  formData.contactName = ''
  formData.contactPhone = ''
  formData.address = ''
  formRef.value?.resetFields()
}

/**
 * 使用详情回填表单。
 */
function fillForm(record: CompanyVO) {
  formData.name = record.name
  formData.level = record.level
  formData.parentId = record.parentId && record.parentId !== 0 ? record.parentId : null
  formData.contactName = record.contactName || ''
  formData.contactPhone = record.contactPhone || ''
  formData.address = record.address || ''
}

/**
 * 加载公司树，用于上级公司选择。
 */
async function loadCompanyTree() {
  try {
    companyTree.value = await getCompanyTree()
  } catch {
    companyTree.value = []
  }
}

watch(
  () => props.open,
  (open) => {
    if (!open) return
    loadCompanyTree()
    resetForm()
    if (props.editingRecord) {
      fillForm(props.editingRecord)
    }
    nextTick(() => {
      formRef.value?.clearValidate()
    })
  },
)

/**
 * 级别变化时的联动处理：集团级别不需要上级公司。
 */
function handleLevelChange() {
  if (formData.level === 1) {
    formData.parentId = null
  } else if (!formData.parentId) {
    formData.parentId = null
  }
  nextTick(() => {
    formRef.value?.validateFields(['parentId'])
  })
}

/**
 * 上级公司校验规则：
 * 1. 集团级别上级必须为空或 0；
 * 2. 非集团级别必须选择上级；
 * 3. 编辑时不能选择自己作为上级。
 */
function validateParent(_rule: any, value: any) {
  // 直接使用表单数据，避免依赖 FormInstance 的类型差异
  const level = formData.level
  if (level === 1) {
    if (value !== null && value !== undefined && value !== 0) {
      return Promise.reject(new Error('集团级别上级公司必须为空或 0'))
    }
    return Promise.resolve()
  }
  if (!value || value === 0) {
    return Promise.reject(new Error('请选择上级公司'))
  }
  if (props.editingRecord && value === props.editingRecord.id) {
    return Promise.reject(new Error('上级公司不能选择自己'))
  }
  return Promise.resolve()
}

const formRules: Record<string, any> = {
  name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择公司级别', trigger: 'change', type: 'number' }],
  parentId: [{ validator: validateParent, trigger: 'change' }],
}

/**
 * 提交表单：新增或更新公司。
 */
async function handleSubmit() {
  try {
    await formRef.value?.validateFields()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const payload: CompanyCreateCmd = {
      parentId: formData.level === 1 ? 0 : (formData.parentId || 0),
      name: formData.name.trim(),
      level: formData.level,
      contactName: formData.contactName?.trim() || undefined,
      contactPhone: formData.contactPhone?.trim() || undefined,
      address: formData.address?.trim() || undefined,
    }

    if (props.editingRecord) {
      await updateCompany(props.editingRecord.id, { ...payload, id: props.editingRecord.id })
      message.success('更新成功')
    } else {
      await createCompany(payload)
      message.success('创建成功')
    }

    emit('update:open', false)
    emit('success')
  } catch {
    // 请求错误由统一拦截器提示
  } finally {
    submitLoading.value = false
  }
}

/**
 * 取消/关闭弹窗时重置表单。
 */
function handleCancel() {
  emit('update:open', false)
  resetForm()
}
</script>

<style lang="scss" scoped>
.tree-panel {
  max-height: 240px;
  overflow: auto;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 8px;
}
</style>
