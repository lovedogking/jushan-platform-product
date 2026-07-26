<template>
  <div class="vehicles-page">
    <div class="page-header">
      <h3>固定车管理</h3>
      <a-space>
        <template v-if="isTenantOnly && lotOptions.length === 1">
          <a-tag color="blue">{{ lotOptions[0]!.name }}</a-tag>
        </template>
        <a-select v-else v-model:value="selectedLotId" placeholder="全部车场" style="width:180px" @change="onLotChange" :loading="lotLoading" allow-clear>
          <a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option>
        </a-select>
        <a-input-search v-model:value="plateFilter" placeholder="搜索车牌" @search="fetchData" allow-clear style="width:160px" />
        <a-button type="primary" @click="showAddModal"><PlusOutlined /> 新增车辆</a-button>
      </a-space>
    </div>
    <a-row :gutter="16">
      <a-col :span="6">
        <div class="list-sidebar">
          <div v-for="item in types" :key="item.key" :class="['list-item', { active: activeType === item.key }]" @click="selectType(item.key)">
            <div class="item-title">{{ item.label }}<a-tag :color="typeColor(item.key)" style="margin-left:8px;font-size:12px">{{ item.count }}</a-tag></div>
            <div class="item-desc">{{ typeDesc(item.key) }}</div>
          </div>
        </div>
      </a-col>
      <a-col :span="18">
        <div v-if="!activeType" class="empty-state"><a-empty description="请从左侧选择车辆类型" /></div>
        <a-table v-else :columns="currentCols" :data-source="data" :loading="loading" :pagination="pag" @change="onPage" row-key="id" size="middle">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'plate'">
              <PlateTag :plate-number="record.plateNumber" :plate-color="record.plateColor" size="small" />
            </template>
            <template v-if="column.key === 'vehicleType'">
              <a-tag :color="typeColor(activeType!)">{{ typeLabel(activeType!) }}</a-tag>
            </template>
            <template v-if="column.key === 'remainDays'">
              <span :style="{ color: String(record.remainDays).startsWith('已过期') ? 'red' : 'inherit' }">{{ record.remainDays }}</span>
            </template>
            <template v-if="column.key === 'actions'">
              <a-space>
                <a @click="showEditModal(record)">编辑</a>
                <a-popconfirm title="确定删除？" @confirm="handleDelete(record.id)"><a style="color:red">删除</a></a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-col>
    </a-row>
    <a-modal v-model:open="formVisible" :title="editingId ? '编辑车辆' : '添加车辆'" :confirm-loading="formSaving" @ok="handleSave" width="560px">
      <a-form layout="vertical">
        <a-form-item label="车辆类型" required>
          <a-radio-group v-model:value="form.vehicleType" :disabled="!!editingId">
            <a-radio-button value="BLACKLIST">黑名单</a-radio-button>
            <a-radio-button value="FREE">免费车</a-radio-button>
            <a-radio-button value="MONTHLY">月租车</a-radio-button>
            <a-radio-button value="PREPAID">储值车</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="车牌号" required><a-input v-model:value="form.plateNumber" placeholder="如 川A88888" :maxlength="8" style="text-transform:uppercase" /></a-form-item>
        <a-form-item label="车牌类型">
          <a-radio-group v-model:value="form.plateColor">
            <a-radio-button value="BLUE">蓝牌</a-radio-button>
            <a-radio-button value="GREEN">绿牌</a-radio-button>
            <a-radio-button value="YELLOW">黄牌</a-radio-button>
            <a-radio-button value="BLACK">黑牌</a-radio-button>
            <a-radio-button value="WHITE">白牌</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="生效时间" required><a-date-picker v-model:value="form.validStartDate" style="width:100%" placeholder="选择生效日期" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="到期时间"><a-date-picker v-model:value="form.validEndDate" style="width:100%" placeholder="到期日期" /></a-form-item></a-col>
        </a-row>
        <a-form-item v-if="form.vehicleType === 'PREPAID'" label="初始余额（元）"><a-input-number v-model:value="form.prepaidBalance" :min="0" :precision="2" style="width:100%" placeholder="选填，后续可充值" /></a-form-item>
        <a-row :gutter="12">
          <a-col :span="12"><a-form-item label="车主姓名"><a-input v-model:value="form.ownerName" placeholder="选填" :maxlength="30" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="手机号码"><a-input v-model:value="form.ownerPhone" placeholder="选填" :maxlength="20" /></a-form-item></a-col>
        </a-row>
        <a-form-item label="生效车场" required><a-select v-model:value="form.parkingLotId" placeholder="选择车场" @change="onFormLotChange"><a-select-option v-for="lot in lotOptions" :key="lot.id" :value="lot.id">{{ lot.name }}</a-select-option></a-select></a-form-item>
        <a-form-item label="生效车道" required><a-select v-model:value="form.laneIds" mode="multiple" placeholder="选择车道（可多选）" :loading="laneLoading"><a-select-option v-for="lane in laneOptions" :key="lane.id" :value="lane.id">{{ lane.name }}</a-select-option></a-select></a-form-item>
        <a-form-item label="备注"><a-input v-model:value="form.remark" placeholder="选填" :maxlength="200" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import { getVehicles, createVehicle, updateVehicle, deleteVehicle, type VehicleVO, type VehicleCreateCmd } from '@/api/vehicle'
import { getBoothParkingLots } from '@/api/parking-lot'
import { getParkingLots } from '@/api/parking-manage'
import PlateTag from '@/components/PlateTag.vue'

const ROLES_KEY = 'jushan_roles'
function getUserRoles(): string[] { try { const r = sessionStorage.getItem(ROLES_KEY); return r ? JSON.parse(r) : [] } catch { return [] } }
const isPlatform = computed(() => getUserRoles().includes('platform'))
const isTenantOnly = computed(() => !getUserRoles().includes('platform') && getUserRoles().includes('tenant'))
const types = reactive([{ key: 'BLACKLIST', label: '黑名单', count: 0 },{ key: 'FREE', label: '免费车', count: 0 },{ key: 'MONTHLY', label: '月租车', count: 0 },{ key: 'PREPAID', label: '储值车', count: 0 }])
const activeType = ref<string|null>('BLACKLIST'); const loading=ref(false); const plateFilter=ref(''); const lotLoading=ref(false)
const selectedLotId=ref<number|undefined>(undefined); const lotOptions=ref<{id:number;name:string}[]>([]); const data=ref<any[]>([])
const pag=reactive({current:1,pageSize:10,total:0})
const cols_base={plate:{title:'车牌号',key:'plate',width:130},type:{title:'类型',key:'vehicleType',width:70},owner:{title:'车主',dataIndex:'ownerName',key:'owner',width:80},phone:{title:'手机号',dataIndex:'ownerPhone',key:'phone',width:120},lanes:{title:'生效车道',key:'lanes'},act:{title:'操作',key:'actions',width:100}}
const monthlyCols=[cols_base.plate,cols_base.type,{title:'有效期起',key:'validStart',width:110},{title:'有效期止',key:'validEnd',width:110},{title:'剩余天数',key:'remainDays',width:80},cols_base.owner,cols_base.phone,cols_base.lanes,cols_base.act]
const prepaidCols=[cols_base.plate,cols_base.type,{title:'余额(元)',key:'balance',width:100},cols_base.owner,cols_base.phone,cols_base.lanes,cols_base.act]
const freeCols=[cols_base.plate,cols_base.type,{title:'有效期起',key:'validStart',width:110},{title:'有效期止',key:'validEnd',width:110},{title:'剩余天数',key:'remainDays',width:80},cols_base.owner,cols_base.phone,cols_base.lanes,cols_base.act]
const blacklistCols=[cols_base.plate,cols_base.type,cols_base.owner,cols_base.phone,cols_base.lanes,cols_base.act]
const currentCols=computed(()=>{if(activeType.value==='PREPAID')return prepaidCols;if(activeType.value==='FREE')return freeCols;if(activeType.value==='BLACKLIST')return blacklistCols;return monthlyCols})
function typeLabel(t:string){const m:Record<string,string>={BLACKLIST:'黑名单',FREE:'免费车',MONTHLY:'月租车',PREPAID:'储值车'};return m[t]||t}
function typeColor(t:string){const m:Record<string,string>={BLACKLIST:'red',FREE:'green',MONTHLY:'blue',PREPAID:'orange'};return m[t]||'default'}
function typeDesc(t:string){const m:Record<string,string>={BLACKLIST:'禁止通行',FREE:'免费通行',MONTHLY:'有效期内不限次数',PREPAID:'余额扣费'};return m[t]||''}
const formVisible=ref(false);const formSaving=ref(false);const editingId=ref<number|null>(null)
const form=reactive({vehicleType:'BLACKLIST',plateNumber:'',plateColor:'',ownerName:'',ownerPhone:'',parkingLotId:undefined as number|undefined,validStartDate:null as Dayjs|null,validEndDate:null as Dayjs|null,prepaidBalance:undefined as number|undefined,laneIds:[] as number[],remark:''})
const laneLoading=ref(false);const laneOptions=ref<{id:number;name:string}[]>([])
function selectType(key:string){activeType.value=key;pag.current=1;fetchData()}
async function fetchData(){loading.value=true;try{const res=await getVehicles({page:pag.current,size:pag.pageSize,plateNumber:plateFilter.value||undefined,vehicleType:activeType.value||undefined,parkingLotId:selectedLotId.value});data.value=(res.records||[]).map(r=>{const e=r.validEndDate?dayjs(r.validEndDate):null;const d=e?e.diff(dayjs(),'day'):null;return{...r,validStart:r.validStartDate||'-',validEnd:r.validEndDate||'长期',remainDays:d!=null?(d>=0?`${d}天`:`已过期${Math.abs(d)}天`):'-',balance:r.prepaidBalance!=null?(r.prepaidBalance/100).toFixed(2):'-',lanes:(r.laneNames||[]).join('、')||'-'}});pag.total=res.total;const i=types.findIndex(t=>t.key===activeType.value);if(i>=0)types[i].count=res.total}finally{loading.value=false}}
async function handleDelete(id:number){try{await deleteVehicle(id);message.success('已删除');fetchData()}catch(e:any){message.error(e.message)}}
function onPage(p:{current:number;pageSize:number}){pag.current=p.current;pag.pageSize=p.pageSize;fetchData()}
function onLotChange(){pag.current=1;fetchData()}
async function loadLotOptions(){lotLoading.value=true;try{if(isPlatform.value){const r=await getParkingLots({page:1,size:1000});lotOptions.value=(r.records||[]).map(l=>({id:l.id,name:l.name}))}else{const lots=await getBoothParkingLots();lotOptions.value=(lots||[]).map(l=>({id:l.id,name:l.name}))}}finally{lotLoading.value=false}}
function showAddModal(){editingId.value=null;form.vehicleType=activeType.value||'BLACKLIST';form.plateNumber='';form.plateColor='';form.ownerName='';form.ownerPhone='';form.parkingLotId=selectedLotId.value;form.validStartDate=null;form.validEndDate=null;form.prepaidBalance=undefined;form.laneIds=[];form.remark='';laneOptions.value=[];formVisible.value=true;if(form.parkingLotId)loadLanes(form.parkingLotId)}
function showEditModal(record:VehicleVO){editingId.value=record.id;form.vehicleType=record.vehicleType;form.plateNumber=record.plateNumber;form.plateColor=record.plateColor||'';form.ownerName=record.ownerName||'';form.ownerPhone=record.ownerPhone||'';form.parkingLotId=record.parkingLotId;form.validStartDate=record.validStartDate?dayjs(record.validStartDate):null;form.validEndDate=record.validEndDate?dayjs(record.validEndDate):null;form.prepaidBalance=record.prepaidBalance!=null?record.prepaidBalance/100:undefined;form.laneIds=record.laneIds||[];form.remark=record.remark||'';laneOptions.value=[];formVisible.value=true;if(record.parkingLotId)loadLanes(record.parkingLotId)}
watch(()=>form.vehicleType,()=>{if(form.vehicleType!=='PREPAID')form.prepaidBalance=undefined})
async function onFormLotChange(lotId:number|undefined){form.laneIds=[];if(lotId)await loadLanes(lotId)}
async function loadLanes(parkingLotId:number){laneLoading.value=true;try{const{getParkingLanes}=await import('@/api/parking-manage');const r=await getParkingLanes({page:1,size:200,parkingLotId});laneOptions.value=(r.records||[]).map(l=>({id:l.id,name:l.name||`车道${l.id}`}))}catch{laneOptions.value=[]}finally{laneLoading.value=false}}
async function handleSave(){const p=form.plateNumber.trim().toUpperCase();if(!p){message.warning('请输入车牌号');return}if(!form.parkingLotId){message.warning('请选择生效车场');return}if(!form.validStartDate){message.warning('请选择生效时间');return}if(form.laneIds.length===0){message.warning('请选择生效车道');return}formSaving.value=true;try{const cmd:VehicleCreateCmd={plateNumber:p,plateColor:form.plateColor||undefined,vehicleType:form.vehicleType,ownerName:form.ownerName||undefined,ownerPhone:form.ownerPhone||undefined,parkingLotId:form.parkingLotId,validStartDate:dayjs(form.validStartDate).format('YYYY-MM-DD'),validEndDate:form.validEndDate?dayjs(form.validEndDate).format('YYYY-MM-DD'):undefined,prepaidBalance:form.prepaidBalance!=null?Math.round(form.prepaidBalance*100):undefined,laneIds:form.laneIds,remark:form.remark||undefined};if(editingId.value){await updateVehicle(editingId.value,cmd);message.success('更新成功')}else{await createVehicle(cmd);message.success('添加成功')}formVisible.value=false;fetchData()}catch(e:any){message.error(e?.response?.data?.message||e?.message||'保存失败')}finally{formSaving.value=false}}
onMounted(()=>{loadLotOptions();fetchData()})
</script>

<style lang="scss" scoped>
.vehicles-page{height:100%;display:flex;flex-direction:column}.empty-state{display:flex;justify-content:center;align-items:center;height:300px}
</style>
