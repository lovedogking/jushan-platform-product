<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-heading">
        <h2>岗亭端登录</h2>
        <p>请输入岗亭工作人员账号密码进入实时监控面板。</p>
      </div>

      <a-form :model="formState" layout="vertical" @finish="handleSubmit">
        <a-form-item name="username" label="账号" :rules="[{ required: true, message: '请输入账号' }]">
          <a-input v-model:value="formState.username" placeholder="请输入账号" size="large" allow-clear>
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" label="密码" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="formState.password" placeholder="请输入密码" size="large" allow-clear>
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-button type="primary" html-type="submit" size="large" block :loading="loading" class="login-button">
          登录岗亭端
        </a-button>
      </a-form>

      <!-- 错误提示 -->
      <a-alert v-if="errorMsg" type="error" :message="errorMsg" show-icon class="login-error" />

      <div class="login-footer">
        岗亭端为停车场现场工作人员使用，具备实时监控、人工放行、设备查看等能力。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { login as loginApi } from '@/api/auth'

const TOKEN_KEY = 'jushan_access_token'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const errorMsg = ref('')

const formState = reactive({ username: '', password: '' })

/**
 * 写入 Token 前校验 accessToken 是非空字符串。
 * 不满足时抛错，不写 localStorage，不进入已登录状态。
 */
function validateAndSetToken(accessToken: unknown): string {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('服务端返回的 accessToken 无效')
  }
  const trimmed = accessToken.trim()
  localStorage.setItem(TOKEN_KEY, trimmed)
  return trimmed
}

async function handleSubmit() {
  loading.value = true
  errorMsg.value = ''
  try {
    const result = await loginApi({
      username: formState.username,
      password: formState.password,
    })
    validateAndSetToken(result.accessToken)
    const redirect = (route.query.redirect as string) || '/monitor'
    router.push(redirect)
  } catch (e: any) {
    const traceInfo = e.traceId ? `（traceId: ${e.traceId}）` : ''
    errorMsg.value = (e.message || '登录失败') + traceInfo
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f172a 0%, #102a43 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: $border-radius-lg;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-heading {
  text-align: center;
  margin-bottom: 32px;

  h2 {
    font-size: 24px;
    font-weight: 700;
    color: $text-color;
    margin: 0 0 8px;
  }

  p {
    color: $text-color-secondary;
    font-size: 13px;
    margin: 0;
  }
}

.login-button {
  height: 44px;
  font-weight: 650;
  margin-top: 8px;
}

.login-error {
  margin-top: 16px;
}

.login-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid $border-color-split;
  color: $text-color-secondary;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}
</style>
