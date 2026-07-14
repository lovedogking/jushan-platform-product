<template>
  <div class="register-container">
    <a-card class="register-card" :bordered="false">
      <div class="register-header">
        <div class="logo">
          <CarOutlined class="logo-icon" />
          <span class="logo-text">飓山智慧停车</span>
        </div>
        <h2 class="title">注册租户账号</h2>
        <p class="subtitle">提交企业信息，平台将在 1-2 个工作日内完成审核</p>
      </div>

      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item name="companyName" label="企业名称">
          <a-input
            v-model:value="formState.companyName"
            placeholder="请输入企业全称"
            size="large"
            allow-clear
          >
            <template #prefix>
              <BankOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="contactPerson" label="联系人">
          <a-input
            v-model:value="formState.contactPerson"
            placeholder="请输入联系人姓名"
            size="large"
            allow-clear
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="contactPhone" label="联系电话（登录账号）">
          <a-input
            v-model:value="formState.contactPhone"
            placeholder="请输入手机号"
            size="large"
            allow-clear
          >
            <template #prefix>
              <PhoneOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="password" label="登录密码">
          <a-input-password
            v-model:value="formState.password"
            placeholder="请设置登录密码（6-64位）"
            size="large"
            allow-clear
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item name="confirmPassword" label="确认密码">
          <a-input-password
            v-model:value="formState.confirmPassword"
            placeholder="请再次输入密码"
            size="large"
            allow-clear
          >
            <template #prefix>
              <SafetyCertificateOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="loading"
          class="submit-button"
        >
          提交入驻申请
        </a-button>
      </a-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <a @click="goToLogin">立即登录</a>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  CarOutlined,
  BankOutlined,
  UserOutlined,
  PhoneOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons-vue'
import { register } from '@/api/register'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

const formState = reactive({
  companyName: '',
  contactPerson: '',
  contactPhone: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: any, value: string) => {
  if (value !== formState.password) {
    return Promise.reject(new Error('两次输入的密码不一致'))
  }
  return Promise.resolve()
}

const rules: Record<string, Rule[]> = {
  companyName: [
    { required: true, message: '请输入企业名称', trigger: 'blur' },
    { max: 128, message: '企业名称不能超过128个字符', trigger: 'blur' },
  ],
  contactPerson: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { max: 64, message: '联系人不能超过64个字符', trigger: 'blur' },
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须在6-64个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  loading.value = true
  try {
    await register({
      companyName: formState.companyName,
      contactPerson: formState.contactPerson,
      contactPhone: formState.contactPhone,
      password: formState.password,
    })
    message.success('入驻申请提交成功，请等待审核')
    router.push('/login')
  } catch (error: any) {
    message.error(error?.message || '提交失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}
</script>

<style lang="scss" scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f0ff 50%, #f0f7ff 100%);
  padding: 24px;
}

.register-card {
  width: 100%;
  max-width: 460px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  background: #fff;
  padding: 40px 40px;
}

.register-header {
  text-align: center;
  margin-bottom: 28px;

  .logo {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 12px;
    margin-bottom: 24px;

    .logo-icon {
      font-size: 36px;
      color: $primary-color;
    }

    .logo-text {
      font-size: 24px;
      font-weight: 700;
      color: $text-color;
    }
  }

  .title {
    font-size: 22px;
    font-weight: 600;
    color: $text-color;
    margin: 0 0 8px;
  }

  .subtitle {
    font-size: 14px;
    color: $text-color-secondary;
    margin: 0;
  }
}

.submit-button {
  height: 44px;
  font-weight: 600;
  font-size: 16px;
  margin-top: 8px;
}

.register-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  color: $text-color-secondary;
  font-size: 14px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid $border-color-split;
}

@media (max-width: 480px) {
  .register-card {
    padding: 32px 24px;
  }
}
</style>
