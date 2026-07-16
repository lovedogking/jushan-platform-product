<template>
  <div class="system-param-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>系统参数</h2>
      <span class="page-desc">配置影响系统行为的全局参数，修改后即时生效</span>
    </div>

    <a-spin :spinning="loading">
      <!-- 分组卡片迭代 -->
      <div v-if="hasParams">
        <a-card
          v-for="(params, group) in groupedParams"
          :key="group"
          :title="group"
          class="param-card"
        >
          <div class="param-list">
            <div v-for="param in params" :key="param.key" class="param-row">
              <!-- 参数说明 -->
              <div class="param-info">
                <div class="param-label">{{ param.description || param.key }}</div>
                <div class="param-key">{{ param.key }}</div>
              </div>

              <!-- 编辑器区域 -->
              <div class="param-editor">
                <!-- INT → 数字输入框 -->
                <a-input-number
                  v-if="param.valueType === 'INT'"
                  v-model:value="editValues[param.key]"
                  :min="0"
                  :max="999999"
                  :precision="0"
                  :disabled="savingKeys.has(param.key)"
                />

                <!-- BOOLEAN → 开关 -->
                <a-switch
                  v-else-if="param.valueType === 'BOOLEAN'"
                  :checked="editValues[param.key] === 'true'"
                  :disabled="savingKeys.has(param.key)"
                  @change="(checked: boolean) => handleBooleanChange(param.key, checked)"
                />

                <!-- ENUM → 下拉选择 -->
                <a-select
                  v-else-if="param.valueType === 'ENUM'"
                  v-model:value="editValues[param.key]"
                  :options="enumOptions(param.options)"
                  :disabled="savingKeys.has(param.key)"
                  style="width: 200px"
                />

                <!-- STRING / 默认 → 文本输入 -->
                <a-input
                  v-else
                  v-model:value="editValues[param.key]"
                  :disabled="savingKeys.has(param.key)"
                  style="width: 200px"
                />
              </div>

              <!-- 操作区域 -->
              <div class="param-action">
                <a-button
                  type="primary"
                  size="small"
                  :loading="savingKeys.has(param.key)"
                  :disabled="editValues[param.key] === param.value"
                  @click="handleSave(param.key)"
                >
                  保存
                </a-button>
              </div>
            </div>
          </div>
        </a-card>
      </div>

      <!-- 空状态 -->
      <a-empty v-else description="暂无系统参数" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { getGroupedParams, updateParam, type SystemParamVO } from '@/api/system-param'

/** 加载状态 */
const loading = ref(false)

/** 按分组的参数 */
const groupedParams = ref<Record<string, SystemParamVO[]>>({})

/** 是否有参数（用于模板 v-if） */
const hasParams = computed(() => Object.keys(groupedParams.value).length > 0)

/** 编辑中的值，key=param.key → value */
const editValues = ref<Record<string, string>>({})

/** 正在保存的 key 集合 */
const savingKeys = ref<Set<string>>(new Set())

/** 加载系统参数 */
async function loadParams() {
  loading.value = true
  try {
    const data = await getGroupedParams()
    groupedParams.value = data
    // 初始化编辑值
    const values: Record<string, string> = {}
    for (const params of Object.values(data)) {
      for (const param of params) {
        values[param.key] = param.value || ''
      }
    }
    editValues.value = values
  } finally {
    loading.value = false
  }
}

/** 解析 ENUM 选项 */
function enumOptions(optionsStr: string | undefined): { label: string; value: string }[] {
  if (!optionsStr) return []
  try {
    const opts: string[] = JSON.parse(optionsStr)
    return opts.map((o) => ({ label: o, value: o }))
  } catch {
    return []
  }
}

/** 布尔值切换处理（a-switch 的 v-model 与 string 类型不兼容，手动处理） */
function handleBooleanChange(key: string, checked: boolean) {
  editValues.value[key] = checked ? 'true' : 'false'
  // 开关变化后自动触发保存
  handleSave(key)
}

/** 保存参数 */
async function handleSave(key: string) {
  const value = editValues.value[key]
  if (value === undefined) return

  savingKeys.value.add(key)
  try {
    await updateParam(key, { value })
    message.success('参数已更新')
  } catch {
    // 请求拦截器已处理错误提示
  } finally {
    savingKeys.value.delete(key)
  }
}

onMounted(() => {
  loadParams()
})
</script>

<style scoped>
.system-param-page {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  color: #888;
  font-size: 14px;
}

.param-card {
  margin-bottom: 24px;
  border-radius: 8px;
}

.param-card :deep(.ant-card-head) {
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
  font-size: 16px;
}

.param-list {
  display: flex;
  flex-direction: column;
}

.param-row {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f5f5f5;
}

.param-row:last-child {
  border-bottom: none;
}

.param-info {
  flex: 1;
  min-width: 200px;
}

.param-label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.param-key {
  font-size: 12px;
  color: #999;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', monospace;
}

.param-editor {
  flex-shrink: 0;
  margin: 0 16px;
}

.param-action {
  flex-shrink: 0;
  min-width: 64px;
  text-align: right;
}
</style>
