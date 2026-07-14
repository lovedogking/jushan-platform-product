<template>
  <div class="fee-calculator-page">
    <a-card title="费用试算" class="calculator-card">
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 14 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.lotId" placeholder="请选择停车场" @change="handleLotChange">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="适用区域">
          <a-select v-model:value="formData.zoneId" placeholder="车场通用（不选）" allow-clear :disabled="!formData.lotId">
            <a-select-option v-for="zone in zoneOptions" :key="zone.id" :value="zone.id">{{ zone.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="车牌号" required>
          <a-input v-model:value="formData.plateNumber" placeholder="如：京A12345" />
        </a-form-item>
        <a-form-item label="车辆类型" required>
          <a-select v-model:value="formData.vehicleType" placeholder="请选择车辆类型">
            <a-select-option v-for="opt in VEHICLE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="入场时间" required>
          <a-date-picker v-model:value="formData.entryTime" show-time style="width: 100%" placeholder="选择入场时间" />
        </a-form-item>
        <a-form-item label="出场时间" required>
          <a-date-picker v-model:value="formData.exitTime" show-time style="width: 100%" placeholder="选择出场时间" />
        </a-form-item>
        <a-form-item :wrapper-col="{ offset: 6, span: 14 }">
          <a-button type="primary" :loading="calculating" @click="handleCalculate">
            <CalculatorOutlined />开始试算
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 试算结果 -->
    <a-card v-if="result" title="试算结果" class="result-card">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="停车时长">{{ result.parkingDuration }} 分钟</a-descriptions-item>
        <a-descriptions-item label="免费时长">{{ result.freeMinutes }} 分钟</a-descriptions-item>
        <a-descriptions-item label="计费时长">{{ result.billingDuration }} 分钟</a-descriptions-item>
        <a-descriptions-item label="匹配规则">{{ result.feeRuleName }}</a-descriptions-item>
        <a-descriptions-item label="原始金额">{{ result.originalAmount }} 元</a-descriptions-item>
        <a-descriptions-item label="优惠金额">{{ result.discountAmount }} 元</a-descriptions-item>
        <a-descriptions-item label="应付金额" :span="2">
          <span class="payable-amount">{{ result.payableAmount }} 元</span>
        </a-descriptions-item>
      </a-descriptions>

      <a-divider>费用明细</a-divider>
      <a-table
        :columns="breakdownColumns"
        :data-source="result.breakdown"
        :pagination="false"
        size="small"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { CalculatorOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { calculateFee, VEHICLE_TYPE_OPTIONS, type FeeCalculateResultVO } from '@/api/fee-calculation'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingZonesByLotId, type ParkingZoneVO } from '@/api/parking-zone'

const parkingLotOptions = ref<ParkingLotVO[]>([])
const zoneOptions = ref<ParkingZoneVO[]>([])
const calculating = ref(false)
const result = ref<FeeCalculateResultVO | null>(null)

const formData = reactive({
  lotId: undefined as number | undefined,
  zoneId: undefined as number | undefined,
  plateNumber: '',
  vehicleType: 'TEMP',
  entryTime: null as any,
  exitTime: null as any,
})

const breakdownColumns = [
  { title: '费用项', dataIndex: 'itemName', key: 'itemName' },
  { title: '时长(分钟)', dataIndex: 'duration', key: 'duration' },
  { title: '单位数', dataIndex: 'unitCount', key: 'unitCount' },
  { title: '金额(元)', dataIndex: 'amount', key: 'amount' },
]

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ current: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch {
    // ignore
  }
}

async function handleLotChange() {
  formData.zoneId = undefined
  if (formData.lotId) {
    try {
      const res = await getParkingZonesByLotId(formData.lotId)
      zoneOptions.value = res
    } catch {
      zoneOptions.value = []
    }
  } else {
    zoneOptions.value = []
  }
}

async function handleCalculate() {
  if (!formData.lotId) {
    message.warning('请选择所属停车场')
    return
  }
  if (!formData.plateNumber.trim()) {
    message.warning('请输入车牌号')
    return
  }
  if (!formData.entryTime) {
    message.warning('请选择入场时间')
    return
  }
  if (!formData.exitTime) {
    message.warning('请选择出场时间')
    return
  }
  if (!formData.exitTime.isAfter(formData.entryTime)) {
    message.warning('出场时间必须晚于入场时间')
    return
  }

  calculating.value = true
  try {
    const res = await calculateFee({
      lotId: formData.lotId,
      zoneId: formData.zoneId,
      plateNumber: formData.plateNumber.trim(),
      vehicleType: formData.vehicleType,
      entryTime: formData.entryTime.format('YYYY-MM-DD HH:mm:ss'),
      exitTime: formData.exitTime.format('YYYY-MM-DD HH:mm:ss'),
    })
    result.value = res
  } catch {
    // ignore
  } finally {
    calculating.value = false
  }
}

onMounted(() => {
  loadParkingLots()
})
</script>

<style lang="scss" scoped>
.fee-calculator-page {
  padding: $spacing-lg;
}

.calculator-card {
  margin-bottom: $spacing-lg;
}

.result-card {
  .payable-amount {
    font-size: 20px;
    font-weight: bold;
    color: #dc2626;
  }
}
</style>
