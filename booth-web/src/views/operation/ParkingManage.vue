<template>
  <div class="parking-manage">
    <a-row :gutter="16">
      <!-- 左侧：车场列表 -->
      <a-col :span="6">
        <div class="lot-sidebar">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索车场..."
            @search="fetchParkingLots"
            style="margin-bottom: 12px"
          />
          <a-list
            :loading="lotLoading"
            :data-source="parkingLots"
            size="small"
          >
            <template #renderItem="{ item }">
              <a-list-item
                :class="['lot-item', { active: selectedLot?.id === item.id }]"
                @click="selectLot(item)"
              >
                <a-list-item-meta>
                  <template #title>
                    <span>{{ item.name }}</span>
                    <a-tag :color="item.status === 'ENABLED' ? 'green' : 'red'" style="margin-left: 8px">
                      {{ item.status === 'ENABLED' ? '启用' : '停用' }}
                    </a-tag>
                  </template>
                  <template #description>
                    {{ item.totalSpaces }} 车位 · {{ item.address || '未填写地址' }}
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
          <a-button v-if="isPlatform" type="dashed" block @click="showCreateLotModal" style="margin-top: 8px">
            <PlusOutlined /> 新增车场
          </a-button>
        </div>
      </a-col>

      <!-- 右侧：Tab 详情 -->
      <a-col :span="18">
        <div v-if="!selectedLot" class="empty-state">
          <a-empty description="请从左侧选择一个车场查看详情" />
        </div>
        <a-tabs v-else v-model:activeKey="activeTab">
          <a-tab-pane key="basic" tab="基本信息">
            <LotBasicInfo v-if="activeTab === 'basic'" :lot="selectedLot" @updated="handleLotUpdated" />
          </a-tab-pane>
          <a-tab-pane key="lane" tab="车道管理">
            <LaneManager v-if="activeTab === 'lane'" :lot-id="selectedLot.id" />
          </a-tab-pane>
          <a-tab-pane key="device" tab="设备管理">
            <DeviceManager v-if="activeTab === 'device'" :lot-id="selectedLot.id" />
          </a-tab-pane>
        </a-tabs>
      </a-col>
    </a-row>

    <!-- 新增车场弹窗 -->
    <a-modal v-model:open="createLotVisible" title="新增车场" @ok="handleCreateLot" :confirm-loading="createLotLoading">
      <a-form :model="createLotForm" layout="vertical">
        <a-form-item label="车场名称" required>
          <a-input v-model:value="createLotForm.name" placeholder="请输入车场名称" />
        </a-form-item>
        <a-form-item label="总车位数">
          <a-input-number v-model:value="createLotForm.totalSpaces" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="负责人姓名">
          <a-input v-model:value="createLotForm.contactName" placeholder="请输入负责人姓名" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="createLotForm.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="详细地址">
          <a-textarea v-model:value="createLotForm.address" placeholder="请输入详细地址" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getParkingLots, createParkingLot,
  type ParkingLotVO, type ParkingLotCreateCmd
} from '@/api/parking-manage'
import LotBasicInfo from './components/LotBasicInfo.vue'
import LaneManager from './components/LaneManager.vue'
import DeviceManager from './components/DeviceManager.vue'

const ROLES_KEY = 'jushan_roles'

function getUserRoles(): string[] {
  try {
    const raw = sessionStorage.getItem(ROLES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

// 仅超管可新增车场；租户管理员只读/编辑（V1.5）
const isPlatform = computed(() => getUserRoles().includes('platform'))

const searchKeyword = ref('')
const parkingLots = ref<ParkingLotVO[]>([])
const lotLoading = ref(false)
const selectedLot = ref<ParkingLotVO | null>(null)
const activeTab = ref('basic')

// 新增车场
const createLotVisible = ref(false)
const createLotLoading = ref(false)
const createLotForm = reactive<ParkingLotCreateCmd & { contactName?: string; contactPhone?: string }>({
  companyId: undefined,
  name: '',
  address: '',
  totalSpaces: undefined,
  contactName: '',
  contactPhone: '',
})

async function fetchParkingLots() {
  lotLoading.value = true
  try {
    const res = await getParkingLots({ page: 1, size: 100, keyword: searchKeyword.value || undefined })
    parkingLots.value = res.records
  } finally {
    lotLoading.value = false
  }
}

function selectLot(lot: ParkingLotVO) {
  selectedLot.value = lot
  activeTab.value = 'basic'
}

function showCreateLotModal() {
  createLotForm.name = ''
  createLotForm.address = ''
  createLotForm.totalSpaces = undefined
  createLotForm.contactName = ''
  createLotForm.contactPhone = ''
  createLotVisible.value = true
}

async function handleCreateLot() {
  if (!createLotForm.name.trim()) {
    message.warning('请输入车场名称')
    return
  }
  createLotLoading.value = true
  try {
    await createParkingLot({
      companyId: createLotForm.companyId,
      name: createLotForm.name,
      address: createLotForm.address,
      totalSpaces: createLotForm.totalSpaces,
      contactName: createLotForm.contactName || undefined,
      contactPhone: createLotForm.contactPhone || undefined,
    })
    message.success('车场创建成功')
    createLotVisible.value = false
    await fetchParkingLots()
  } finally {
    createLotLoading.value = false
  }
}

function handleLotUpdated(lot: ParkingLotVO) {
  selectedLot.value = lot
  fetchParkingLots()
}

onMounted(() => {
  fetchParkingLots()
})
</script>

<style lang="scss" scoped>
.parking-manage { height: 100%; }
.lot-sidebar { border-right: 1px solid #f0f0f0; padding-right: 8px; height: 100%; overflow-y: auto; }
.lot-item { cursor: pointer; transition: background 0.2s; }
.lot-item:hover { background: #f5f5f5; }
.lot-item.active { background: #e6f7ff; border-left: 3px solid #1890ff; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 400px; }
</style>
