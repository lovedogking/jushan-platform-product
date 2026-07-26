<template>
  <div class="fee-rules-page">
    <div class="page-header">
      <h3>计费规则</h3>
      <a-space>
        <a-select v-model:value="filterLotId" placeholder="全部车场" style="width:160px" @change="fetchData" allow-clear :loading="lotLoading">
          <a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-button type="primary" @click="showAddModal"><PlusOutlined /> 新增规则</a-button>
      </a-space>
    </div>

    <a-table :columns="cols" :data-source="data" :loading="loading" :pagination="pag" @change="onPage" row-key="id" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'billingMode'">
          <a-tag :color="billingColor(record.billingMode)">{{ billingLabel(record.billingMode) }}</a-tag>
        </template>
        <template v-if="column.key === 'vehicleType'">
          <span v-if="record.vehicleType">{{ formatVehicleTypes(record.vehicleType) }}</span>
          <span v-else class="text-muted">全部</span>
        </template>
        <template v-if="column.key === 'plateColor'">
          <template v-if="record.plateColor">
            <a-tag v-for="c in record.plateColor.split(',')" :key="c" :color="plateColorHex(c)" size="small">{{ plateColorLabel(c) }}</a-tag>
          </template>
          <span v-else class="text-muted">全部</span>
        </template>
        <template v-if="column.key === 'status'">
          <a-switch :checked="record.status === 1" @change="(v: boolean) => toggleStatus(record, v)" :loading="record._toggling" size="small" />
        </template>
        <template v-if="column.key === 'fee'">
          <span class="fee-text">{{ formatFee(record) }}</span>
        </template>
        <template v-if="column.key === 'actions'">
          <a-space>
            <a @click="showEditModal(record)">编辑</a>
            <a-popconfirm title="确定删除？" @confirm="handleDelete(record.id)"><a style="color:red">删除</a></a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="formVisible" :title="editingId ? '编辑计费规则' : '新增计费规则'" :confirm-loading="formSaving" @ok="handleSave" width="700px">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="规则名称" required>
              <a-input v-model:value="form.name" placeholder="如 标准计费规则" :maxlength="128" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="生效车场" required>
              <a-select v-model:value="form.lotId" placeholder="选择车场">
                <a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="规则描述">
          <a-textarea v-model:value="form.description" placeholder="选填" :maxlength="256" :rows="2" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="计费模式" required>
              <a-select v-model:value="form.billingMode">
                <a-select-option :value="1">按时计费</a-select-option>
                <a-select-option :value="2">按次计费</a-select-option>
                <a-select-option :value="3">阶梯计费</a-select-option>
                <a-select-option :value="4">分时段计费</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="适用车辆类型">
              <a-select v-model:value="form.vehicleType" mode="multiple" placeholder="全部">
                <a-select-option value="TEMP">临时车</a-select-option>
                <a-select-option value="MONTHLY">月租车</a-select-option>
                <a-select-option value="PREPAID">储值车</a-select-option>
                <a-select-option value="FREE">免费车</a-select-option>
                <a-select-option value="BLACKLIST">黑名单</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="适用车牌颜色">
              <a-select v-model:value="form.plateColor" mode="multiple" placeholder="全部">
                <a-select-option value="BLUE">蓝牌</a-select-option>
                <a-select-option value="GREEN">绿牌</a-select-option>
                <a-select-option value="YELLOW">黄牌</a-select-option>
                <a-select-option value="BLACK">黑牌</a-select-option>
                <a-select-option value="WHITE">白牌</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
        <!-- 按时计费模式 -->
        <template v-if="form.billingMode === 1">
          <a-row :gutter="16">
            <a-col :span="6"><a-form-item label="免费时长(分钟)"><a-input-number v-model:value="form.freeMinutes" :min="0" style="width:100%" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="计费单位(分钟)"><a-input-number v-model:value="form.unitMinutes" :min="1" style="width:100%" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="首时段时长(分钟)"><a-input-number v-model:value="form.firstPeriodMinutes" :min="0" style="width:100%" placeholder="0=无" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="首时段价格(元)"><a-input-number v-model:value="form.firstPeriodPrice" :min="0" :precision="2" style="width:100%" placeholder="如 5.00" /></a-form-item></a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="6"><a-form-item label="续费单价(元)"><a-input-number v-model:value="form.subsequentPrice" :min="0" :precision="2" style="width:100%" placeholder="如 3.00" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="日封顶(元)"><a-input-number v-model:value="form.dailyCap" :min="0" :precision="2" style="width:100%" placeholder="空=不封顶" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="总封顶(元)"><a-input-number v-model:value="form.maxAmount" :min="0" :precision="2" style="width:100%" placeholder="空=不封顶" /></a-form-item></a-col>
            <a-col :span="6"><a-form-item label="夜间封顶(元)"><a-input-number v-model:value="form.nightCap" :min="0" :precision="2" style="width:100%" placeholder="空=不封顶" /></a-form-item></a-col>
          </a-row>
        </template>
        <!-- 按次计费 -->
        <template v-if="form.billingMode === 2">
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item label="免费时长(分钟)"><a-input-number v-model:value="form.freeMinutes" :min="0" style="width:100%" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item label="每次价格(元)"><a-input-number v-model:value="form.firstPeriodPrice" :min="0" :precision="2" style="width:100%" placeholder="如 10.00" /></a-form-item></a-col>
          </a-row>
        </template>
        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="生效开始时间"><a-date-picker v-model:value="form.effectiveStart" show-time style="width:100%" placeholder="选填" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="生效结束时间"><a-date-picker v-model:value="form.effectiveEnd" show-time style="width:100%" placeholder="选填" /></a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="优先级"><a-input-number v-model:value="form.priority" :min="0" style="width:100%" placeholder="越大越优先" /></a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { getFeeRules, createFeeRule, updateFeeRule, deleteFeeRule, updateFeeRuleStatus, type FeeRuleVO, type FeeRuleCreateCmd } from '@/api/fee-rule'
import { getBoothParkingLots } from '@/api/parking-lot'
import { getParkingLots } from '@/api/parking-manage'

const ROLES_KEY = 'jushan_roles'
function getUserRoles(): string[] { try { const r = sessionStorage.getItem(ROLES_KEY); return r ? JSON.parse(r) : [] } catch { return [] } }
const isPlatform = getUserRoles().includes('platform')

const loading = ref(false); const lotLoading = ref(false)
const filterLotId = ref<number | undefined>(undefined)
const lotOptions = ref<{ id: number; name: string }[]>([])
const data = ref<any[]>([])
const pag = reactive({ current: 1, pageSize: 10, total: 0 })

const cols = [
  { title: '规则名称', dataIndex: 'name', key: 'name', width: 200 },
  { title: '计费模式', key: 'billingMode', width: 90 },
  { title: '适用车辆类型', key: 'vehicleType', width: 130 },
  { title: '适用车牌颜色', key: 'plateColor', width: 130 },
  { title: '费率摘要', key: 'fee', width: 160 },
  { title: '状态', key: 'status', width: 70 },
  { title: '操作', key: 'actions', width: 120 },
]

function billingLabel(m: number) {
  const map: Record<number, string> = { 1: '按时', 2: '按次', 3: '阶梯', 4: '分时段' }
  return map[m] || '--'
}
function billingColor(m: number) {
  const map: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'orange', 4: 'purple' }
  return map[m] || 'default'
}

function formatVehicleTypes(vt: string) {
  const map: Record<string, string> = { TEMP: '临时车', MONTHLY: '月租车', PREPAID: '储值车', FREE: '免费车', BLACKLIST: '黑名单' }
  return vt.split(',').map(v => map[v.trim()] || v.trim()).join('、')
}

function plateColorLabel(c: string) {
  const map: Record<string, string> = { BLUE: '蓝牌', GREEN: '绿牌', YELLOW: '黄牌', BLACK: '黑牌', WHITE: '白牌' }
  return map[c.trim()] || c.trim()
}
function plateColorHex(c: string) {
  const map: Record<string, string> = { BLUE: 'blue', GREEN: 'green', YELLOW: 'gold', BLACK: 'default', WHITE: 'default' }
  return map[c.trim()] || 'default'
}

function formatFee(record: FeeRuleVO) {
  const parts: string[] = []
  if (record.freeMinutes && record.freeMinutes > 0) parts.push(`免${record.freeMinutes}分`)
  if (record.billingMode === 1) {
    if (record.firstPeriodPrice && record.firstPeriodMinutes) parts.push(`首${record.firstPeriodMinutes}分${(record.firstPeriodPrice).toFixed(2)}元`)
    if (record.subsequentPrice) parts.push(`续${(record.subsequentPrice).toFixed(2)}元/${record.unitMinutes || 60}分`)
    if (record.dailyCap) parts.push(`日封顶${(record.dailyCap).toFixed(2)}`)
  } else if (record.billingMode === 2) {
    if (record.firstPeriodPrice) parts.push(`${(record.firstPeriodPrice).toFixed(2)}元/次`)
  }
  return parts.length ? parts.join(' · ') : '--'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getFeeRules({ page: pag.current, size: pag.pageSize, lotId: filterLotId.value })
    data.value = (res.records || []).map(r => ({
      ...r,
      firstPeriodPrice: r.firstPeriodPrice != null ? r.firstPeriodPrice : undefined,
      subsequentPrice: r.subsequentPrice != null ? r.subsequentPrice : undefined,
      dailyCap: r.dailyCap != null ? r.dailyCap : undefined,
      maxAmount: r.maxAmount != null ? r.maxAmount : undefined,
      nightCap: r.nightCap != null ? r.nightCap : undefined,
      _toggling: false,
    }))
    pag.total = res.total
  } finally { loading.value = false }
}

function onPage(p: { current: number; pageSize: number }) { pag.current = p.current; pag.pageSize = p.pageSize; fetchData() }

async function loadLotOptions() {
  lotLoading.value = true
  try {
    if (isPlatform) {
      const r = await getParkingLots({ page: 1, size: 1000 })
      lotOptions.value = (r.records || []).map(l => ({ id: l.id, name: l.name }))
    } else {
      const lots = await getBoothParkingLots()
      lotOptions.value = (lots || []).map(l => ({ id: l.id, name: l.name }))
    }
  } finally { lotLoading.value = false }
}

// --- 弹窗 ---
const formVisible = ref(false); const formSaving = ref(false); const editingId = ref<number | null>(null)
const form = reactive({
  name: '', description: '', lotId: undefined as number | undefined, billingMode: 1,
  vehicleType: [] as string[], plateColor: [] as string[],
  freeMinutes: 0, unitMinutes: 60, firstPeriodMinutes: 0,
  firstPeriodPrice: undefined as number | undefined, subsequentPrice: undefined as number | undefined,
  dailyCap: undefined as number | undefined, maxAmount: undefined as number | undefined, nightCap: undefined as number | undefined,
  priority: 0, effectiveStart: null as Dayjs | null, effectiveEnd: null as Dayjs | null,
})

function showAddModal() {
  editingId.value = null
  Object.assign(form, {
    name: '', description: '', lotId: filterLotId.value || undefined, billingMode: 1,
    vehicleType: [], plateColor: [],
    freeMinutes: 0, unitMinutes: 60, firstPeriodMinutes: 0,
    firstPeriodPrice: undefined, subsequentPrice: undefined,
    dailyCap: undefined, maxAmount: undefined, nightCap: undefined,
    priority: 0, effectiveStart: null, effectiveEnd: null,
  })
  formVisible.value = true
}

function showEditModal(record: any) {
  editingId.value = record.id
  Object.assign(form, {
    name: record.name, description: record.description || '', lotId: record.lotId, billingMode: record.billingMode,
    vehicleType: record.vehicleType ? record.vehicleType.split(',').filter(Boolean) : [],
    plateColor: record.plateColor ? record.plateColor.split(',').filter(Boolean) : [],
    freeMinutes: record.freeMinutes, unitMinutes: record.unitMinutes, firstPeriodMinutes: record.firstPeriodMinutes || 0,
    firstPeriodPrice: record.firstPeriodPrice, subsequentPrice: record.subsequentPrice,
    dailyCap: record.dailyCap, maxAmount: record.maxAmount, nightCap: record.nightCap,
    priority: record.priority || 0,
    effectiveStart: record.effectiveStart ? dayjs(record.effectiveStart) : null,
    effectiveEnd: record.effectiveEnd ? dayjs(record.effectiveEnd) : null,
  })
  formVisible.value = true
}

async function handleSave() {
  if (!form.name.trim()) { message.warning('请输入规则名称'); return }
  if (!form.lotId) { message.warning('请选择生效车场'); return }

  formSaving.value = true
  try {
    const cmd: FeeRuleCreateCmd = {
      lotId: form.lotId!,
      name: form.name.trim(),
      description: form.description || undefined,
      billingMode: form.billingMode,
      vehicleType: form.vehicleType.length ? form.vehicleType.join(',') : undefined,
      plateColor: form.plateColor.length ? form.plateColor.join(',') : undefined,
      freeMinutes: form.freeMinutes,
      unitMinutes: form.unitMinutes,
      firstPeriodMinutes: form.firstPeriodMinutes,
      firstPeriodPrice: form.firstPeriodPrice,
      subsequentPrice: form.subsequentPrice,
      dailyCap: form.dailyCap,
      maxAmount: form.maxAmount,
      nightCap: form.nightCap,
      priority: form.priority,
      status: 1,
      effectiveStart: form.effectiveStart ? dayjs(form.effectiveStart).format('YYYY-MM-DD HH:mm:ss') : undefined,
      effectiveEnd: form.effectiveEnd ? dayjs(form.effectiveEnd).format('YYYY-MM-DD HH:mm:ss') : undefined,
    }

    if (editingId.value) {
      await updateFeeRule(editingId.value, cmd)
      message.success('更新成功')
    } else {
      await createFeeRule(cmd)
      message.success('创建成功')
    }
    formVisible.value = false
    fetchData()
  } catch (e: any) {
    message.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally { formSaving.value = false }
}

async function handleDelete(id: number) {
  try { await deleteFeeRule(id); message.success('已删除'); fetchData() } catch (e: any) { message.error(e.message) }
}

async function toggleStatus(record: any, checked: boolean) {
  record._toggling = true
  try {
    await updateFeeRuleStatus(record.id, checked ? 1 : 2)
    record.status = checked ? 1 : 2
  } catch (e: any) { message.error(e.message) } finally { record._toggling = false }
}

onMounted(() => { loadLotOptions(); fetchData() })
</script>

<style lang="scss" scoped>
.fee-rules-page { height: 100%; display: flex; flex-direction: column; }
.text-muted { color: #bfbfbf; }
.fee-text { font-family: 'SF Mono', 'Consolas', monospace; font-size: 12px; }
</style>
