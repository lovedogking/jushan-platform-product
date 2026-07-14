<template>
  <a-modal
    :title="isEdit ? '编辑部门' : '新增部门'"
    :visible="visible"
    :confirm-loading="confirmLoading"
    @ok="handleOk"
    @cancel="handleCancel"
    width="600px"
  >
    <a-form
      ref="formRef"
      :model="formState"
      :rules="rules"
      layout="vertical"
    >
      <a-form-item label="上级部门" name="parentId">
        <a-tree-select
          v-model:value="formState.parentId"
          :tree-data="treeData"
          :field-names="{ label: 'name', value: 'id', children: 'children' }"
          placeholder="请选择上级部门（不选则为顶级部门）"
          allow-clear
          tree-default-expand-all
        />
      </a-form-item>

      <a-form-item label="部门名称" name="name">
        <a-input v-model:value="formState.name" placeholder="请输入部门名称" />
      </a-form-item>

      <a-form-item label="部门编码" name="code">
        <a-input v-model:value="formState.code" placeholder="请输入部门编码" />
      </a-form-item>

      <a-form-item label="所属停车场" name="parkingLotId">
        <a-select
          v-model:value="formState.parkingLotId"
          placeholder="请选择所属停车场"
          :options="parkingLotOptions"
        />
      </a-form-item>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="负责人" name="managerName">
            <a-input v-model:value="formState.managerName" placeholder="请输入负责人姓名" />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="联系电话" name="contactPhone">
            <a-input v-model:value="formState.contactPhone" placeholder="请输入联系电话" />
          </a-form-item>
        </a-col>
      </a-row>

      <a-form-item label="排序" name="sortOrder">
        <a-input-number v-model:value="formState.sortOrder" :min="0" style="width: 100%" />
      </a-form-item>

      <a-form-item label="备注" name="remark">
        <a-textarea v-model:value="formState.remark" :rows="3" placeholder="请输入备注" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import { createDepartment, updateDepartment } from '@/api/department'

const props = defineProps<{
  visible: boolean
  record: any
  treeData: any[]
}>()

const emit = defineEmits<['update:visible', 'success']>()

const formRef = ref<FormInstance>()
const confirmLoading = ref(false)

const isEdit = computed(() => !!props.record)

const formState = reactive({
  parentId: 0 as number,
  name: '',
  code: '',
  parkingLotId: undefined as number | undefined,
  managerName: '',
  contactPhone: '',
  sortOrder: 0,
  remark: ''
})

const parkingLotOptions = ref([
  { label: '停车场A', value: 1 },
  { label: '停车场B', value: 2 }
])

const rules = {
  name: [{ required: true, message: '请输入部门名称' }],
  parkingLotId: [{ required: true, message: '请选择所属停车场' }]
}

watch(() => props.visible, (val) => {
  if (val && props.record) {
    Object.assign(formState, props.record)
  } else if (val) {
    formRef.value?.resetFields()
    formState.parentId = 0
    formState.name = ''
    formState.code = ''
    formState.parkingLotId = undefined
    formState.managerName = ''
    formState.contactPhone = ''
    formState.sortOrder = 0
    formState.remark = ''
  }
})

const handleOk = async () => {
  try {
    await formRef.value?.validate()
    confirmLoading.value = true

    if (isEdit.value) {
      await updateDepartment(props.record.id, formState)
      message.success('编辑成功')
    } else {
      await createDepartment(formState)
      message.success('新增成功')
    }

    emit('update:visible', false)
    emit('success')
  } catch (e) {
    console.error(e)
  } finally {
    confirmLoading.value = false
  }
}

const handleCancel = () => {
  emit('update:visible', false)
}
</script>
