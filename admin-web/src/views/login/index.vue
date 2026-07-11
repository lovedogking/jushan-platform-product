<template>
  <div class="login-container">
    <section class="brand-panel">
      <div class="brand-nav">
        <div class="brand-mark">
          <span>飓山智慧停车运营平台</span>
        </div>
        <a-tag color="processing">飓山智能科技有限公司</a-tag>
      </div>

      <div class="hero-content">
        <div class="hero-kicker">软硬件一体化 · 多停车场集中运营 · SaaS 化交付</div>
        <h1>飓山智慧停车运营平台</h1>
        <p>
          软硬件一体化智慧停车 SaaS 解决方案，覆盖入场识别、计费支付、月卡会员、
          设备监控与异常告警全链路，帮助停车场运营方降低人工成本、提升经营效率。
        </p>
        <div class="hero-metrics">
          <div>
            <strong>300+</strong>
            <span>可接入停车场规模</span>
          </div>
          <div>
            <strong>99%</strong>
            <span>设备状态监控</span>
          </div>
          <div>
            <strong>闭环</strong>
            <span>识别计费支付抬杆</span>
          </div>
        </div>
      </div>

      <div class="capability-grid">
        <div v-for="item in capabilities" :key="item.title" class="capability-item">
          <component :is="item.icon" />
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel">
      <div class="login-card">
        <div class="login-heading">
          <a-tag color="blue">企业级运营控制台</a-tag>
          <h2>登录平台驾驶舱</h2>
          <p>进入收入、车流、车位、设备与异常告警的统一运营视图。</p>
        </div>

        <a-form
          ref="formRef"
          :model="formState"
          :rules="rules"
          layout="vertical"
          @finish="handleSubmit"
        >
          <a-form-item name="username" label="账号">
            <a-input
              v-model:value="formState.username"
              placeholder="请输入平台账号"
              size="large"
              allow-clear
            >
              <template #prefix>
                <UserOutlined />
              </template>
            </a-input>
          </a-form-item>
          <a-form-item name="password" label="密码">
            <a-input-password
              v-model:value="formState.password"
              placeholder="请输入登录密码"
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
            <span>总部运营 / SaaS 展示环境</span>
          </div>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
            class="login-button"
          >
            登录平台驾驶舱
          </a-button>
        </a-form>

        <div class="login-footer">
          <SafetyCertificateOutlined />
          © 2026 飓山智能科技有限公司。数据通过 Sa-Token 鉴权保护，支付与设备链路按模块化边界接入。
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import {
  ApiOutlined,
  CloudServerOutlined,
  CreditCardOutlined,
  DashboardOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
  VideoCameraOutlined,
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

const capabilities = [
  { title: '多停车场管理', desc: '支持总部统一管理多场站、车位容量和运营状态。', icon: CloudServerOutlined },
  { title: '设备在线监控', desc: '摄像头、道闸、显示屏、地感设备状态集中可视。', icon: ApiOutlined },
  { title: '车牌识别联动', desc: '入场识别、出场匹配、异常记录全链路追溯。', icon: VideoCameraOutlined },
  { title: '自动计费支付', desc: '订单生成、微信支付、状态回调、自动抬杆一体化。', icon: CreditCardOutlined },
  { title: '经营数据分析', desc: '收入、车流、车位、设备和告警集中展示。', icon: DashboardOutlined },
]

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
    message.success('登录成功，正在进入经营驾驶舱')
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
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 440px;
  background: $bg-color;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 34px 52px;
  color: #fff;
  background:
    linear-gradient(135deg, rgba(15, 23, 42, 0.98), rgba(11, 42, 74, 0.94)),
    radial-gradient(circle at 78% 20%, rgba(22, 93, 255, 0.22), transparent 30%),
    radial-gradient(circle at 12% 78%, rgba(8, 145, 178, 0.16), transparent 28%),
    #0f172a;
}

.brand-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(219, 234, 254, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(219, 234, 254, 0.05) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 80%);
  pointer-events: none;
}

.brand-nav,
.hero-content,
.capability-grid {
  position: relative;
  z-index: 1;
}

.brand-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 17px;
  font-weight: 700;
}

.hero-content {
  max-width: 760px;
  margin-top: 80px;

  .hero-kicker {
    color: #93c5fd;
    font-weight: 650;
    margin-bottom: 18px;
  }

  h1 {
    color: #fff;
    font-size: 46px;
    font-weight: 780;
    line-height: 1.12;
    margin: 0 0 18px;
  }

  p {
    color: rgba(255, 255, 255, 0.75);
    font-size: 17px;
    line-height: 1.8;
    margin: 0;
    max-width: 680px;
  }
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 34px;

  div {
    padding: 16px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 18px;
    background: rgba(255, 255, 255, 0.06);
  }

  strong {
    display: block;
    color: #fff;
    font-size: 26px;
    line-height: 1.1;
    margin-bottom: 6px;
  }

  span {
    color: rgba(255, 255, 255, 0.66);
    font-size: 13px;
  }
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-top: 48px;
}

.capability-item {
  min-height: 128px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.07);

  :deep(.anticon) {
    color: #93c5fd;
    font-size: 24px;
    margin-bottom: 14px;
  }

  h3 {
    color: #fff;
    font-size: 15px;
    margin-bottom: 8px;
  }

  p {
    color: rgba(255, 255, 255, 0.62);
    font-size: 12px;
    line-height: 1.6;
    margin: 0;
  }
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.login-card {
  width: 100%;
  max-width: 360px;
}

.login-heading {
  margin-bottom: 28px;

  h2 {
    color: $text-color;
    font-size: 28px;
    font-weight: 760;
    margin: 12px 0 8px;
  }

  p {
    color: $text-color-secondary;
    line-height: 1.7;
    margin: 0;
  }
}

.login-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: $text-color-secondary;
  font-size: 12px;
  margin: -4px 0 18px;
}

.login-button {
  height: 44px;
  font-weight: 650;
}

.login-footer {
  display: flex;
  gap: 8px;
  color: $text-color-secondary;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid $border-color-split;

  :deep(.anticon) {
    color: $primary-color;
    margin-top: 3px;
  }
}

@media (max-width: 1180px) {
  .login-container {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: 620px;
  }

  .login-panel {
    min-height: 520px;
  }
}

@media (max-width: 768px) {
  .brand-panel {
    padding: 24px;
  }

  .hero-content {
    margin-top: 46px;

    h1 {
      font-size: 34px;
    }
  }

  .hero-metrics,
  .capability-grid {
    grid-template-columns: 1fr;
  }
}
</style>
