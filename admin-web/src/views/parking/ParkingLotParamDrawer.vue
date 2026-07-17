<template>
  <a-drawer
    v-model:open="open"
    :title="`车场参数 - ${lotName}`"
    width="640"
    :body-style="{ paddingBottom: '24px' }"
  >
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 16px"
      message="车场参数优先于全局参数。标记“继承”的参数使用全局/默认值，保存后即成为本车场专属配置；可随时“重置为继承”。"
    />

    <a-spin :spinning="loading">
      <div v-if="params.length">
        <a-card
          v-for="(list, group) in groupedParams"
          :key="group"
          :title="group"
          size="small"
          class="param-group"
        >
          <div v-for="p in list" :key="p.key" class="param-row">
            <div class="param-info">
              <div class="param-label">
                {{ p.description || p.key }}
                <a-tag v-if="!p.overridden" color="blue" class="src-tag">继承</a-tag>
                <a-tag v-else color="green" class="src-tag">车场专属</a-tag>
              </div>
              <div class="param-key">{{ p.key }}</div>
              <div class="param-hint">
                继承值：{{ displayValue(p, p.inheritedValue) }}（{{ sourceText(p) }}）
              </div>
            </div>

            <div class="param-editor">
              <a-input-number
                v-if="p.valueType === 'INT'"
                v-model:value="editValues[p.key]"
                :min="0"
                :max="999999"
                :precision="0"
                :disabled="busy(p.key)"
                style="width: 160px"
              />
              <a-select
                v-else-if="p.valueType === 'BOOLEAN'"
                v-model:value="editValues[p.key]"
                :options="BOOLEAN_OPTIONS"
                :disabled="busy(p.key)"
                style="width: 160px"
              />
              <a-select
                v-else-if="p.valueType === 'ENUM'"
                v-model:value="editValues[p.key]"
                :options="enumOptions(p.options)"
                :disabled="busy(p.key)"
                style="width: 160px"
              />
              <a-input
                v-else
                v-model:value="editValues[p.key]"
                :disabled="busy(p.key)"
                style="width: 160px"
              />
            </div>

            <div class="param-action">
              <a-button
                v-permission="'parking:write'"
                type="primary"
                size="small"
                :loading="savingKeys.has(p.key)"
                @click="handleSave(p)"
              >
                保存
              </a-button>
              <a-popconfirm
                v-if="p.overridden"
                title="重置后本车场将继承全局/默认值，确认？"
                @confirm="handleReset(p)"
              >
                <a-button
                  v-permission="'parking:write'"
                  size="small"
                  :loading="resettingKeys.has(p.key)"
                  style="margin-left: 8px"
                >
                  重置为继承
                </a-button>
              </a-popconfirm>
            </div>
          </div>
        </a-card>
      </div>
      <a-empty v-else description="暂无车场参数" />
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  getLotParams,
  setLotParam,
  resetLotParam,
  type ParkingLotParamVO,
} from '@/api/parking-lot-param'

const BOOLEAN_OPTIONS = [
  { label: '是', value: 'true' },
  { label: '否', value: 'false' },
]

const open = ref(false)
const loading = ref(false)
const lotId = ref<number | null>(null)
const lotName = ref('')
const params = ref<ParkingLotParamVO[]>([])
const editValues = ref<Record<string, string>>({})
const savingKeys = ref<Set<string>>(new Set())
const resettingKeys = ref<Set<string>>(new Set())

/** 按分组聚合（保持后端返回顺序） */
const groupedParams = computed(() => {
  const map: Record<string, ParkingLotParamVO[]> = {}
  for (const p of params.value) {
    const g = p.groupName || '其他'
    ;(map[g] ||= []).push(p)
  }
  return map
})

function busy(key: string) {
  return savingKeys.value.has(key) || resettingKeys.value.has(key)
}

function applyList(list: ParkingLotParamVO[]) {
  params.value = list || []
  const values: Record<string, string> = {}
  for (const p of params.value) {
    values[p.key] = p.value ?? ''
  }
  editValues.value = values
}

function enumOptions(optionsStr?: string): { label: string; value: string }[] {
  if (!optionsStr) return []
  try {
    const opts: string[] = JSON.parse(optionsStr)
    return opts.map((o) => ({ label: o, value: o }))
  } catch {
    return []
  }
}

function displayValue(p: ParkingLotParamVO, v?: string): string {
  if (v == null || v === '') return '-'
  if (p.valueType === 'BOOLEAN') return v === 'true' ? '是' : '否'
  return v
}

function sourceText(p: ParkingLotParamVO): string {
  const map: Record<string, string> = { LOT: '车场级', GLOBAL: '全局', DEFAULT: '默认值' }
  // 未覆盖时继承值来源是全局或默认；已覆盖时继承值来源同样来自全局/默认（供参考）
  if (!p.overridden) return map[p.source] || p.source
  return '全局/默认'
}

async function load() {
  if (lotId.value == null) return
  loading.value = true
  try {
    const list = await getLotParams(lotId.value)
    applyList(list)
  } finally {
    loading.value = false
  }
}

async function handleSave(p: ParkingLotParamVO) {
  if (lotId.value == null) return
  const raw = editValues.value[p.key]
  const value = raw == null ? '' : String(raw)
  if (value === '') {
    message.warning('参数值不能为空')
    return
  }
  savingKeys.value.add(p.key)
  try {
    const list = await setLotParam(lotId.value, p.key, value)
    applyList(list)
    message.success('已保存为车场专属配置')
  } catch {
    // 请求拦截器已提示
  } finally {
    savingKeys.value.delete(p.key)
  }
}

async function handleReset(p: ParkingLotParamVO) {
  if (lotId.value == null) return
  resettingKeys.value.add(p.key)
  try {
    const list = await resetLotParam(lotId.value, p.key)
    applyList(list)
    message.success('已重置为继承')
  } catch {
    // 请求拦截器已提示
  } finally {
    resettingKeys.value.delete(p.key)
  }
}

/** 供父组件调用：打开抽屉并加载指定车场的参数 */
function openDrawer(lot: { id: number; name: string }) {
  lotId.value = lot.id
  lotName.value = lot.name
  open.value = true
  load()
}

defineExpose({ open: openDrawer })
</script>

<style scoped>
.param-group {
  margin-bottom: 16px;
}
.param-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}
.param-row:last-child {
  border-bottom: none;
}
.param-info {
  flex: 1;
  min-width: 200px;
  padding-right: 12px;
}
.param-label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}
.src-tag {
  margin-left: 6px;
}
.param-key {
  font-size: 12px;
  color: #999;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
  margin-top: 2px;
}
.param-hint {
  font-size: 12px;
  color: #aaa;
  margin-top: 2px;
}
.param-editor {
  flex-shrink: 0;
  margin: 0 12px;
}
.param-action {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
