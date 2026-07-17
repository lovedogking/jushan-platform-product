<template>
  <div class="fee-calculator-page">
    <a-card title="费用试算" class="calculator-card">
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 14 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
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

    <a-card v-if="result !== null" title="试算结果" class="result-card">
      <a-descriptions :column="2" bordered>
        <a-descriptions-item label="停车场 ID">{{ result.parkingLotId }}</a-descriptions-item>
        <a-descriptions-item label="费用(分)">{{ result.feeCents }}</a-descriptions-item>
        <a-descriptions-item label="费用(元)">
          <span class="payable-amount">{{ result.feeYuan }} 元</span>
        </a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { CalculatorOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import request from '@/utils/request'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const parkingLotOptions = ref<ParkingLotVO[]>([])
const calculating = ref(false)
const result = ref<{ feeCents: number; feeYuan: number; parkingLotId: number } | null>(null)

const formData = reactive({
  parkingLotId: undefined as number | undefined,
  entryTime: null as any,
  exitTime: null as any,
})

async function loadParkingLots() {
  try {
    const res = await getParkingLots({ page: 1, size: 100 })
    parkingLotOptions.value = res.records
  } catch {
    // ignore
  }
}

async function handleCalculate() {
  if (!formData.parkingLotId) {
    message.warning('请选择所属停车场')
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
    const res = await request.get('/admin/billing-rules/calculate', {
      parkingLotId: formData.parkingLotId,
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
