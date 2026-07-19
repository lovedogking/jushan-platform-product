<template>
  <div class="device-page">
    <div class="query-bar">
      <a-space>
        <a-select v-model:value="queryParkingLotId" placeholder="停车场" allow-clear style="width: 180px" @change="handleQuery">
          <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="queryDeviceType" placeholder="设备类型" allow-clear style="width: 120px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="CAMERA">相机</a-select-option>
          <a-select-option value="GATE">道闸</a-select-option>
        </a-select>
        <a-select v-model:value="queryStatus" placeholder="状态" allow-clear style="width: 100px" @change="handleQuery">
          <a-select-option value="">全部</a-select-option>
          <a-select-option value="ENABLED">已启用</a-select-option>
          <a-select-option value="DISABLED">已停用</a-select-option>
        </a-select>
        <a-button type="primary" @click="handleQuery"><template #icon><SearchOutlined /></template>查询</a-button>
        <a-button @click="handleReset"><template #icon><ReloadOutlined /></template>重置</a-button>
      </a-space>
      <a-space>
        <a-button @click="handleBatchQueryStatus" :loading="batchQueryLoading">批量刷新状态</a-button>
        <a-button type="primary" @click="handleCreate"><template #icon><PlusOutlined /></template>新增设备</a-button>
      </a-space>
    </div>

    <a-table :columns="columns" :data-source="dataSource" :loading="loading" :pagination="pagination"
      row-key="id" @change="handleTableChange" :row-selection="rowSelection">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'deviceType'">
          <a-tag :color="record.deviceType === 'CAMERA' ? 'blue' : 'orange'">
            {{ record.deviceType === 'CAMERA' ? '相机' : '道闸' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'recognitionDirection'">
          <a-tag v-if="record.recognitionDirection === 1" color="green">入场</a-tag>
          <a-tag v-else-if="record.recognitionDirection === 2" color="red">出场</a-tag>
          <span v-else style="color: #999">-</span>
        </template>
        <template v-if="column.key === 'cameraRole'">
          <a-tag v-if="record.cameraRole === 1" color="blue">主</a-tag>
          <a-tag v-else-if="record.cameraRole === 2" color="orange">备</a-tag>
          <span v-else style="color: #999">-</span>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'onlineState'">
          <template v-if="statusMap[record.id]?.collectedAt">
            <a-tag v-if="statusMap[record.id]?.stale" color="orange">状态过期</a-tag>
            <a-tooltip :title="statusMap[record.id]?.online ? '在线' : '离线'">
              <span :style="{ color: statusMap[record.id]?.online ? '#52c41a' : '#dc2626', cursor: 'pointer' }" @click="handleQuerySingleStatus(record)">
                {{ statusMap[record.id]?.online ? '● 在线' : '● 离线' }}
              </span>
            </a-tooltip>
          </template>
          <a-tag v-else @click="handleQuerySingleStatus(record)" style="cursor: pointer">点击查询</a-tag>
        </template>
        <template v-if="column.key === 'laneInfo'">
          <span v-if="record.laneId" style="color: #1677ff">已绑定</span>
          <span v-else style="color: #999">未绑定</span>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a @click="handleEdit(record)">编辑</a>
            <a @click="handleBindLane(record)">绑定车道</a>
            <a v-if="record.deviceType === 'GATE'" @click="handleSetExecutor(record)">执行相机</a>
            <a @click="handleSyncTime(record)">校时</a>
            <template v-if="record.deviceType === 'GATE' || record.deviceType === 'CAMERA'">
              <a @click="handleOpenGate(record)">开闸</a>
              <a @click="handleCloseGate(record)">关闸</a>
            </template>
            <template v-if="record.deviceType === 'CAMERA'">
              <a @click="handleOpenControl(record)">显示屏</a>
              <a @click="handleVoiceBroadcast(record)">语音播报</a>
            </template>
            <a v-if="record.status === 'DISABLED'" @click="handleToggleStatus(record, 'ENABLED')">启用</a>
            <a v-else style="color: #dc2626" @click="handleToggleStatus(record, 'DISABLED')">停用</a>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑弹窗 -->
    <a-modal v-model:open="formModalOpen" :title="formModalTitle" :confirm-loading="formLoading" width="600px" @ok="handleFormSubmit">
      <a-form :model="formData" :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="所属停车场" required>
          <a-select v-model:value="formData.parkingLotId" placeholder="请选择停车场" :disabled="isEditing">
            <a-select-option v-for="lot in parkingLotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设备名称" required>
          <a-input v-model:value="formData.name" placeholder="如：入口相机1" />
        </a-form-item>
        <a-form-item label="设备编码" required>
          <a-input v-model:value="formData.code" placeholder="停车场内唯一编码" />
        </a-form-item>
        <a-form-item label="设备序列号" required>
          <a-input v-model:value="formData.deviceSn" placeholder="厂商设备序列号" :disabled="isEditing" />
        </a-form-item>
        <a-form-item label="设备类型" required>
          <a-select v-model:value="formData.deviceType" placeholder="请选择设备类型">
            <a-select-option value="CAMERA">相机</a-select-option>
            <a-select-option value="GATE">道闸</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="识别方向" v-if="formData.deviceType === 'CAMERA'">
          <a-select v-model:value="formData.recognitionDirection" placeholder="请选择识别方向" allow-clear>
            <a-select-option :value="1">入场</a-select-option>
            <a-select-option :value="2">出场</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="主备角色" v-if="formData.deviceType === 'CAMERA'">
          <a-select v-model:value="formData.cameraRole" placeholder="请选择主备角色" allow-clear>
            <a-select-option :value="1">主相机</a-select-option>
            <a-select-option :value="2">备相机</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="厂商" required>
          <a-select v-model:value="formData.vendorId" placeholder="请选择厂商" @change="onVendorChange">
            <a-select-option v-for="v in vendorOptions" :key="v.id" :value="v.id">{{ v.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="型号" required>
          <a-select v-model:value="formData.modelId" placeholder="请选择型号">
            <a-select-option v-for="m in modelOptions" :key="m.id" :value="m.id">{{ m.name }} ({{ m.code }})</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="设备能力">
          <a-input v-model:value="formData.capabilities" placeholder="如 RECOGNIZE,CAPTURE" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model:value="formData.description" placeholder="备注（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 车道绑定弹窗 -->
    <a-modal v-model:open="laneModalOpen" title="绑定车道" :confirm-loading="laneLoading" @ok="handleLaneBindConfirm">
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 16 }">
        <a-form-item label="目标车道">
          <a-select v-model:value="selectedLaneId" placeholder="请选择车道">
            <a-select-option v-for="l in laneOptions" :key="l.id" :value="l.id">
              {{ l.name }} ({{ l.type === 1 ? '入口' : l.type === 2 ? '出口' : '双向' }})
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="当前绑定" v-if="laneTarget?.laneId">
          <a-tag>已绑定车道 ID: {{ laneTarget.laneId }}</a-tag>
          <a-button size="small" danger @click="handleUnbindLane">解绑</a-button>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 执行相机弹窗 -->
    <a-modal v-model:open="executorModalOpen" title="设置执行相机" :confirm-loading="executorLoading" @ok="handleExecutorConfirm">
      <a-form :label-col="{ span: 8 }" :wrapper-col="{ span: 14 }">
        <a-form-item label="道闸设备">
          <span>{{ executorTarget?.name }}</span>
        </a-form-item>
        <a-form-item label="执行相机">
          <a-select v-model:value="selectedExecutorId" placeholder="请选择同停车场相机">
            <a-select-option v-for="d in executorCameraOptions" :key="d.id" :value="d.id">
              {{ d.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="当前执行相机" v-if="executorTarget?.executorDeviceId">
          <span>已绑定相机 ID: {{ executorTarget.executorDeviceId }}</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 校时结果弹窗 -->
    <a-modal v-model:open="syncResultOpen" title="校时结果" :footer="null" width="480px">
      <a-descriptions v-if="syncResult" :column="1" size="small" bordered>
        <a-descriptions-item label="结果">
          <a-tag :color="syncResult.success ? 'green' : 'red'">
            {{ syncResult.success ? '成功' : '失败' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="设备返回码">{{ syncResult.deviceCode }}</a-descriptions-item>
        <a-descriptions-item label="消息">{{ syncResult.message }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- 设备控制弹窗 -->
    <DeviceControlModal
      v-model:open="controlModalOpen"
      :device-id="controlTargetDeviceId"
      :device-name="controlTargetDeviceName"
      :initial-tab="controlInitialTab"
    />

    <!-- 开闸/关闸结果弹窗 -->
    <a-modal v-model:open="gateResultOpen" title="操作结果" :footer="null" width="480px">
      <a-descriptions v-if="gateResult" :column="1" size="small" bordered>
        <a-descriptions-item label="结果">
          <a-tag :color="gateResult.success ? 'green' : 'red'">
            {{ gateResult.success ? '成功' : '失败' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="设备返回码">{{ gateResult.deviceCode }}</a-descriptions-item>
        <a-descriptions-item label="消息">{{ gateResult.message }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined } from '@ant-design/icons-vue'
import {
  getDevices, createDevice, updateDevice, updateDeviceStatus,
  getVendors, getModels, bindLane, unbindLane, setExecutor,
  syncDeviceTime, queryDeviceStatus,
  openGate, closeGate,
  type DeviceVO, type DeviceStatusVO, type DeviceVendor, type DeviceModel,
} from '@/api/device'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'
import { getParkingLanes, type ParkingLaneVO } from '@/api/parking-lane'
import DeviceControlModal from '@/components/DeviceControlModal.vue'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 70 },
  { title: '名称', dataIndex: 'name', key: 'name', width: 130 },
  { title: '编码', dataIndex: 'code', key: 'code', width: 90 },
  { title: '类型', key: 'deviceType', width: 70 },
  { title: '识别方向', key: 'recognitionDirection', width: 80 },
  { title: '主备', key: 'cameraRole', width: 60 },
  { title: '序列号', dataIndex: 'deviceSn', key: 'deviceSn', width: 130, ellipsis: true },
  { title: '厂商', dataIndex: 'vendorName', key: 'vendorName', width: 90 },
  { title: '型号', dataIndex: 'modelName', key: 'modelName', width: 100 },
  { title: '状态', key: 'status', width: 70 },
  { title: '在线', key: 'onlineState', width: 90 },
  { title: '车道', key: 'laneInfo', width: 70 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 560, fixed: 'right' as const },
]

const loading = ref(false)
const dataSource = ref<DeviceVO[]>([])
const queryParkingLotId = ref<number | undefined>(undefined)
const queryDeviceType = ref('')
const queryStatus = ref('')
const parkingLotOptions = ref<ParkingLotVO[]>([])
const vendorOptions = ref<DeviceVendor[]>([])
const modelOptions = ref<DeviceModel[]>([])
const laneOptions = ref<ParkingLaneVO[]>([])
const statusMap = ref<Record<number, DeviceStatusVO>>({})

const selectedRowKeys = ref<number[]>([])
const rowSelection: any = { selectedRowKeys, onChange: (keys: number[]) => { selectedRowKeys.value = keys } }

const pagination = reactive({
  current: 1, pageSize: 10, total: 0,
  showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条`,
})

// 表单
const formModalOpen = ref(false)
const formLoading = ref(false)
const formModalTitle = ref('')
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formData = reactive({
  parkingLotId: undefined as number | undefined,
  name: '', code: '', deviceSn: '', deviceType: 'CAMERA',
  recognitionDirection: undefined as number | undefined,
  cameraRole: undefined as number | undefined,
  vendorId: undefined as number | undefined, modelId: undefined as number | undefined,
  capabilities: '', description: '',
})

// 车道绑定
const laneModalOpen = ref(false)
const laneLoading = ref(false)
const laneTarget = ref<DeviceVO | null>(null)
const selectedLaneId = ref<number | undefined>(undefined)

// 执行相机
const executorModalOpen = ref(false)
const executorLoading = ref(false)
const executorTarget = ref<DeviceVO | null>(null)
const selectedExecutorId = ref<number | undefined>(undefined)
const executorCameraOptions = ref<DeviceVO[]>([])

// 校时
const syncResultOpen = ref(false)
const syncResult = ref<{ success: boolean; deviceCode: number; message: string } | null>(null)

// 批量查询
const batchQueryLoading = ref(false)

// 设备控制弹窗
const controlModalOpen = ref(false)
const controlTargetDeviceId = ref(0)
const controlTargetDeviceName = ref('')
const controlInitialTab = ref('display')

// 开闸/关闸结果
const gateResultOpen = ref(false)
const gateResult = ref<{ success: boolean; deviceCode: number; message: string } | null>(null)

function handleOpenGate(record: any) {
  const deviceName = record.name || `设备#${record.id}`
  if (!confirm(`确定要对【${deviceName}】执行开闸吗？`)) return
  openGate(record.id).then((res) => {
    gateResult.value = res
    gateResultOpen.value = true
    message.success(res.success ? '开闸成功' : `开闸失败: ${res.message || '未知错误'}`)
  }).catch(() => {
    message.error('开闸请求失败')
  })
}

function handleCloseGate(record: any) {
  const deviceName = record.name || `设备#${record.id}`
  if (!confirm(`确定要对【${deviceName}】执行关闸吗？`)) return
  closeGate(record.id).then((res) => {
    gateResult.value = res
    gateResultOpen.value = true
    message.success(res.success ? '关闸成功' : `关闸失败: ${res.message || '未知错误'}`)
  }).catch(() => {
    message.error('关闸请求失败')
  })
}

function handleOpenControl(record: any) {
  controlTargetDeviceId.value = record.id
  controlTargetDeviceName.value = record.name || `设备#${record.id}`
  controlInitialTab.value = 'display'
  controlModalOpen.value = true
}

function handleVoiceBroadcast(record: any) {
  controlTargetDeviceId.value = record.id
  controlTargetDeviceName.value = record.name || `设备#${record.id}`
  controlInitialTab.value = 'voice'
  controlModalOpen.value = true
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getDevices({
      page: pagination.current, size: pagination.pageSize,
      parkingLotId: queryParkingLotId.value,
      status: queryStatus.value || undefined,
      deviceType: queryDeviceType.value || undefined,
    })
    dataSource.value = res.records
    pagination.total = res.total
    // 后台加载状态快照
    loadSnapshots(res.records.map(d => d.id))
  } finally { loading.value = false }
}

async function loadSnapshots(deviceIds: number[]) {
  if (!deviceIds.length) return
  try {
    const { getDeviceStatusSnapshots } = await import('@/api/device')
    const snapshots = await getDeviceStatusSnapshots(deviceIds)
    snapshots.forEach(s => { if (s) statusMap.value[s.deviceId] = s })
  } catch { /* 快照静默失败 */ }
}

async function loadOptions() {
  const [lotsRes, vendorsRes] = await Promise.all([
    getParkingLots({ current: 1, size: 100 }),
    getVendors(),
  ])
  parkingLotOptions.value = lotsRes.records
  vendorOptions.value = vendorsRes
}

async function onVendorChange(vendorId: any) {
  formData.modelId = undefined
  if (vendorId) {
    modelOptions.value = await getModels(vendorId)
  } else {
    modelOptions.value = []
  }
}

function handleQuery() { pagination.current = 1; fetchData() }
function handleReset() {
  queryParkingLotId.value = undefined; queryStatus.value = ''; queryDeviceType.value = ''
  pagination.current = 1; fetchData()
}
function handleTableChange(pag: any) {
  pagination.current = pag.current; pagination.pageSize = pag.pageSize; fetchData()
}

function handleCreate() {
  isEditing.value = false; editingId.value = null
  formModalTitle.value = '新增设备'
  formData.parkingLotId = queryParkingLotId.value || undefined
  formData.name = ''; formData.code = ''; formData.deviceSn = ''
  formData.deviceType = 'CAMERA'; formData.recognitionDirection = undefined; formData.cameraRole = undefined
  formData.vendorId = undefined; formData.modelId = undefined
  formData.capabilities = ''; formData.description = ''
  modelOptions.value = []
  formModalOpen.value = true
}

function handleEdit(record: any) {
  isEditing.value = true; editingId.value = record.id
  formModalTitle.value = `编辑设备 — ${record.name}`
  formData.parkingLotId = record.parkingLotId
  formData.name = record.name; formData.code = record.code
  formData.deviceSn = record.deviceSn; formData.deviceType = record.deviceType
  formData.recognitionDirection = record.recognitionDirection
  formData.cameraRole = record.cameraRole
  formData.vendorId = record.vendorId; formData.modelId = record.modelId
  formData.capabilities = record.capabilities || ''; formData.description = record.description || ''
  // 加载型号
  getModels(record.vendorId).then(m => { modelOptions.value = m })
  formModalOpen.value = true
}

async function handleFormSubmit() {
  if (!formData.parkingLotId) { message.warning('请选择停车场'); return }
  if (!formData.name.trim()) { message.warning('请输入设备名称'); return }
  if (!formData.code.trim()) { message.warning('请输入设备编码'); return }
  if (!isEditing.value && !formData.deviceSn.trim()) { message.warning('请输入序列号'); return }
  if (!formData.vendorId) { message.warning('请选择厂商'); return }
  if (!formData.modelId) { message.warning('请选择型号'); return }
  formLoading.value = true
  try {
    if (isEditing.value && editingId.value) {
      await updateDevice(editingId.value, {
        name: formData.name.trim(), code: formData.code.trim(),
        deviceType: formData.deviceType,
        recognitionDirection: formData.recognitionDirection,
        cameraRole: formData.cameraRole,
        capabilities: formData.capabilities.trim(),
        description: formData.description.trim(),
      })
      message.success('更新成功')
    } else {
      await createDevice({
        parkingLotId: formData.parkingLotId!,
        vendorId: formData.vendorId!, modelId: formData.modelId!,
        name: formData.name.trim(), code: formData.code.trim(),
        deviceSn: formData.deviceSn.trim(), deviceType: formData.deviceType,
        recognitionDirection: formData.recognitionDirection,
        cameraRole: formData.cameraRole,
        capabilities: formData.capabilities.trim(),
        description: formData.description.trim(),
      })
      message.success('创建成功')
    }
    formModalOpen.value = false; fetchData()
  } catch { /* */ } finally { formLoading.value = false }
}

function handleToggleStatus(record: any, action: string) {
  updateDeviceStatus(record.id, action).then(() => {
    message.success(action === 'ENABLED' ? '已启用' : '已停用')
    fetchData()
  }).catch(() => {})
}

async function handleBindLane(record: any) {
  laneTarget.value = record
  selectedLaneId.value = undefined
  // 加载同停车场已启用车道
  try {
    const res = await getParkingLanes({ page: 1, size: 50, parkingLotId: record.parkingLotId, status: 1 })
    laneOptions.value = res.records
  } catch { laneOptions.value = [] }
  laneModalOpen.value = true
}

async function handleLaneBindConfirm() {
  if (!selectedLaneId.value || !laneTarget.value) { message.warning('请选择车道'); return }
  laneLoading.value = true
  try {
    await bindLane(laneTarget.value.id, selectedLaneId.value)
    message.success('绑定成功')
    laneModalOpen.value = false; fetchData()
  } catch { /* */ } finally { laneLoading.value = false }
}

async function handleUnbindLane() {
  if (!laneTarget.value) return
  laneLoading.value = true
  try {
    await unbindLane(laneTarget.value.id)
    message.success('解绑成功')
    laneModalOpen.value = false; fetchData()
  } catch { /* */ } finally { laneLoading.value = false }
}

async function handleSetExecutor(record: any) {
  executorTarget.value = record
  selectedExecutorId.value = undefined
  // 加载同停车场相机
  try {
    const res = await getDevices({ page: 1, size: 50, parkingLotId: record.parkingLotId, deviceType: 'CAMERA', status: 'ENABLED' })
    executorCameraOptions.value = res.records
  } catch { executorCameraOptions.value = [] }
  executorModalOpen.value = true
}

async function handleExecutorConfirm() {
  if (!selectedExecutorId.value || !executorTarget.value) { message.warning('请选择执行相机'); return }
  executorLoading.value = true
  try {
    await setExecutor(executorTarget.value.id, selectedExecutorId.value)
    message.success('执行相机设置成功')
    executorModalOpen.value = false; fetchData()
  } catch { /* */ } finally { executorLoading.value = false }
}

async function handleSyncTime(record: any) {
  const reason = prompt('请输入校时原因（可选）：') || ''
  try {
    const res = await syncDeviceTime(record.id, reason)
    syncResult.value = res
    syncResultOpen.value = true
  } catch { message.error('校时请求失败') }
}

async function handleQuerySingleStatus(record: any) {
  try {
    const res = await queryDeviceStatus(record.id)
    statusMap.value[record.id] = res
    message.success(res.online ? `${record.name} 在线` : `${record.name} 离线`)
  } catch { message.error('状态查询失败') }
}

async function handleBatchQueryStatus() {
  const ids = selectedRowKeys.value.length ? selectedRowKeys.value : dataSource.value.map(d => d.id)
  if (!ids.length) { message.warning('请选择设备'); return }
  batchQueryLoading.value = true
  try {
    const { queryDeviceStatusBatch } = await import('@/api/device')
    const results = await queryDeviceStatusBatch(ids.slice(0, 50))
    results.forEach(s => { if (s) statusMap.value[s.deviceId] = s })
    message.success(`查询完成：${results.length} 台设备`)
  } catch { message.error('批量查询失败') } finally {
    batchQueryLoading.value = false
  }
}

onMounted(() => { loadOptions(); fetchData() })
</script>

<style lang="scss" scoped>
.device-page { background: #fff; border-radius: $border-radius-base; padding: $spacing-lg; }
.query-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: $spacing-lg; flex-wrap: wrap; gap: 8px; }
</style>
