<template>
  <div class="lot-basic-info">
    <a-form :model="form" layout="vertical" style="max-width: 600px">
      <a-form-item label="车场名称">
        <a-input v-model:value="form.name" />
      </a-form-item>
      <a-form-item label="总车位数">
        <a-input-number v-model:value="form.totalSpaces" :min="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="负责人姓名">
        <a-input v-model:value="form.contactName" placeholder="请输入负责人姓名" />
      </a-form-item>
      <a-form-item label="联系电话">
        <a-input v-model:value="form.contactPhone" placeholder="请输入联系电话" />
      </a-form-item>
      <a-form-item label="详细地址">
        <a-textarea v-model:value="form.address" placeholder="请输入详细地址" :rows="2" />
      </a-form-item>
      <a-form-item label="状态">
        <a-switch
          :checked="form.status === 'ENABLED'"
          checked-children="启用"
          un-checked-children="停用"
          @change="handleStatusChange"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { updateParkingLot, updateParkingLotStatus, type ParkingLotVO } from '@/api/parking-manage'

const props = defineProps<{ lot: ParkingLotVO }>()
const emit = defineEmits<{ updated: [lot: ParkingLotVO] }>()

const saving = ref(false)

const form = reactive({
  name: '',
  address: '',
  totalSpaces: 0,
  contactName: '',
  contactPhone: '',
  status: 'ENABLED' as string,
})

watch(() => props.lot, (lot) => {
  if (lot) {
    form.name = lot.name || ''
    form.address = lot.address || ''
    form.totalSpaces = lot.totalSpaces || 0
    form.contactName = lot.contactName || ''
    form.contactPhone = lot.contactPhone || ''
    form.status = lot.status
  }
}, { immediate: true })

async function handleSave() {
  saving.value = true
  try {
    const updated = await updateParkingLot(props.lot.id, {
      name: form.name,
      address: form.address,
      totalSpaces: form.totalSpaces,
      contactName: form.contactName,
      contactPhone: form.contactPhone,
    })
    message.success('保存成功')
    emit('updated', updated)
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(checked: boolean) {
  const action = checked ? 'ENABLED' : 'DISABLED'
  try {
    await updateParkingLotStatus(props.lot.id, action)
    form.status = action
    message.success(checked ? '车场已启用' : '车场已停用')
    emit('updated', { ...props.lot, status: action })
  } catch {
    form.status = props.lot.status // 恢复
  }
}
</script>
