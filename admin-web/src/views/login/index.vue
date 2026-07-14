<template>
  <div class="login-container">
    <a-card class="login-card" :bordered="false">
      <div class="login-header">
        <div class="logo">
          <CarOutlined class="logo-icon" />
          <span class="logo-text">飓山智慧停车</span>
        </div>
        <h2 class="title">运营平台登录</h2>
        <p class="subtitle">多停车场集中运营管理后台</p>
      </div>

      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <a-form-item name="username">
          <a-input
            v-model:value="formState.username"
            placeholder="请输入账号"
            size="large"
            allow-clear
          >
            <template #prefix>
              <UserOutlined />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="password">
          <a-input-password
            v-model:value="formState.password"
            placeholder="请输入密码"
            size="large"
            allow-clear
          >
            <template #prefix>
              <LockOutlined />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="login-options">
          <a-checkbox v-model:checked="rememberMe">记住账号</a-checkbox>
        </div>

        <a-button
          type="primary"
          html-type="submit"
          size="large"
          block
          :loading="loading"
          class="login-button"
        >
          登录
        </a-button>
      </a-form>

      <a-divider class="divider">或</a-divider>

      <a-button
        block
        size="large"
        class="register-button"
        @click="goToRegister"
      >
        <template #icon><PlusOutlined /></template>
        注册租户
      </a-button>

      <div class="login-footer">
        <SafetyCertificateOutlined />
        <span>© 2026 飓山智能科技有限公司</span>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  CarOutlined,
  UserOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { useAuthStore } from '@/stores'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const REMEMBER_KEY = 'parking_remembered_username'

const formRef = ref<FormInstance>()
const loading = ref(false)
const rememberMe = ref(false)

const formState = reactive({
  username: localStorage.getItem(REMEMBER_KEY) || '',
  password: '',
})

if (formState.username) {
  rememberMe.value = true
}

const rules: Record<string, Rule[]> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 4, message: '密码至少4位', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  loading.value = true
  try {
    await authStore.login(formState.username, formState.password)
    if (rememberMe.value) {
      localStorage.setItem(REMEMBER_KEY, formState.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    message.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (error: any) {
    if (!error?.response) {
      message.error(error?.message || '登录失败，请检查网络连接后重试')
    }
  } finally {
    loading.value = false
  }
}

function goToRegister() {
  router.push('/register')
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f5ff 0%, #e6f0ff 50%, #f0f7ff 100%);
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  background: #fff;
  padding: 48px 40px;
}

.login-header {
  text-align: center;
  margin-bottom: 32px;

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

.login-options {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  color: $text-color-secondary;
  font-size: 14px;
}

.login-button {
  height: 44px;
  font-weight: 600;
  font-size: 16px;
}

.divider {
  margin: 20px 0;
  color: $text-color-secondary;
  font-size: 13px;
}

.register-button {
  height: 44px;
  font-weight: 500;
  font-size: 15px;
  border-color: $primary-color;
  color: $primary-color;

  &:hover {
    background: rgba($primary-color, 0.05);
  }
}

.login-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid $border-color-split;
  color: $text-color-secondary;
  font-size: 12px;

  :deep(.anticon) {
    color: $primary-color;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }
}
</style>
