<template>
  <a-modal
    :open="visible"
    title="首次登录 — 请修改密码"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :footer="null"
    width="460px"
  >
    <a-alert type="info" show-icon message="为保障账号安全，首次登录必须修改密码。" style="margin-bottom: 16px" />

    <a-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      @finish="handleSubmit"
    >
      <a-form-item label="原密码" name="oldPassword">
        <a-input-password v-model:value="formData.oldPassword" placeholder="请输入当前密码" />
      </a-form-item>
      <a-form-item label="新密码" name="newPassword">
        <a-input-password v-model:value="formData.newPassword" placeholder="6-64位新密码" />
      </a-form-item>
      <a-form-item label="确认密码" name="confirmPassword">
        <a-input-password v-model:value="formData.confirmPassword" placeholder="再次输入新密码" />
      </a-form-item>
      <a-form-item :wrapper-col="{ offset: 6, span: 16 }">
        <a-button type="primary" html-type="submit" :loading="loading" block>确认修改密码</a-button>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores'

const props = defineProps<{
  open: boolean
  username: string
}>()

const emit = defineEmits<{
  (e: 'update:open', val: boolean): void
  (e: 'success'): void
}>()

const visible = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const formData = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: any, value: string) => {
  if (!value) return Promise.reject(new Error('请确认新密码'))
  if (value !== formData.newPassword) return Promise.reject(new Error('两次输入的密码不一致'))
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
    { max: 64, message: '密码不能超过64位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  loading.value = true
  try {
    await changePassword({
      oldPassword: formData.oldPassword,
      newPassword: formData.newPassword,
    })
    message.success('密码修改成功')
    // Clear must_change_password flag in local store
    if (authStore.userInfo) {
      authStore.userInfo.mustChangePassword = 0
    }
    emit('success')
    visible.value = false
  } catch (err: any) {
    if (err?.message?.includes('原密码不正确')) {
      message.error('原密码不正确')
    }
  } finally {
    loading.value = false
  }
}
</script>
