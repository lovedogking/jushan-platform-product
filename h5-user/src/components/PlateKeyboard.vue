<template>
  <div class="plate-keyboard">
    <!-- 已输入的车牌号 -->
    <div class="plate-display">
      <div
        v-for="(char, i) in plateChars"
        :key="i"
        class="plate-char"
        :class="{
          'plate-char--province': i === 0,
          'plate-char--active': i === cursorIndex,
          'plate-char--new-energy': i === 7,
        }"
        @click="cursorIndex = Math.min(i, plateChars.length - 1)"
      >
        {{ char }}
      </div>
      <!-- 未填的空位 -->
      <div
        v-for="i in emptySlots"
        :key="'e' + i"
        class="plate-char plate-char--empty"
        :class="{ 'plate-char--active': (plateChars.length + i - 1) === cursorIndex }"
        @click="cursorIndex = plateChars.length + i - 1"
      ></div>
      <!-- 新能源切换 -->
      <div
        v-if="plateChars.length >= 7"
        class="plate-char plate-char--toggle"
        :class="{ 'plate-char--new-energy': isNewEnergy }"
        @click="toggleNewEnergy"
      >
        新能源
      </div>
    </div>

    <!-- 省份选择键盘 -->
    <div v-if="selectingProvince" class="keyboard-panel">
      <div class="province-grid">
        <div
          v-for="p in provinces"
          :key="p"
          class="key-btn key-btn--province"
          @click="onProvinceSelect(p)"
        >
          {{ p }}
        </div>
      </div>
    </div>

    <!-- 字母+数字键盘 -->
    <div v-else class="keyboard-panel">
      <div class="key-row">
        <span
          v-for="k in row1"
          :key="k"
          class="key-btn"
          @click="onKeyInput(k)"
        >{{ k }}</span>
      </div>
      <div class="key-row">
        <span
          v-for="k in row2"
          :key="k"
          class="key-btn"
          @click="onKeyInput(k)"
        >{{ k }}</span>
      </div>
      <div class="key-row">
        <span
          v-for="k in row3"
          :key="k"
          class="key-btn"
          @click="onKeyInput(k)"
        >{{ k }}</span>
      </div>
      <div class="key-row key-row--action">
        <span class="key-btn key-btn--back" @click="onBackspace">
          <van-icon name="delete-o" size="22" />
        </span>
        <span
          class="key-btn key-btn--confirm"
          :class="{ 'key-btn--disabled': plateChars.length < 7 }"
          @click="onConfirm"
        >
          确认
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const emit = defineEmits<{
  (e: 'confirm', plate: string): void
}>()

// 省份简称
const provinces = [
  '京', '津', '沪', '渝',
  '冀', '豫', '云', '辽', '黑', '湘',
  '皖', '鲁', '新', '苏', '浙', '赣',
  '鄂', '桂', '甘', '晋', '蒙', '陕',
  '吉', '闽', '贵', '粤', '青', '藏',
  '川', '宁', '琼',
]

// 键盘布局
const row1 = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0']
const row2 = ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P']
const row3 = ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L']

const plateChars = ref<string[]>([])
const cursorIndex = ref(0)
const isNewEnergy = ref(false)
const selectingProvince = ref(true)

const maxLength = computed(() => (isNewEnergy.value ? 8 : 7))
const emptySlots = computed(() => Math.max(0, maxLength.value - plateChars.value.length))

function onProvinceSelect(p: string) {
  plateChars.value = [p]
  cursorIndex.value = 1
  selectingProvince.value = false
}

function onKeyInput(k: string) {
  if (plateChars.value.length < maxLength.value) {
    plateChars.value.push(k)
    cursorIndex.value = plateChars.value.length
  }
}

function onBackspace() {
  if (plateChars.value.length <= 1) {
    // 回到选择省份
    plateChars.value = []
    cursorIndex.value = 0
    selectingProvince.value = true
  } else {
    plateChars.value.pop()
    cursorIndex.value = plateChars.value.length
  }
}

function toggleNewEnergy() {
  isNewEnergy.value = !isNewEnergy.value
  // 如果当前已满 8 位但切回 7 位，截断
  if (!isNewEnergy.value && plateChars.value.length > 7) {
    plateChars.value = plateChars.value.slice(0, 7)
    cursorIndex.value = Math.min(cursorIndex.value, 7)
  }
}

function onConfirm() {
  if (plateChars.value.length >= 7) {
    emit('confirm', plateChars.value.join(''))
  }
}
</script>

<style scoped>
.plate-keyboard {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #f0f1f5;
  z-index: 1000;
  padding-bottom: env(safe-area-inset-bottom);
}

/* ---- 车牌展示区 ---- */
.plate-display {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 16px 12px;
  background: #fff;
  flex-wrap: wrap;
}

.plate-char {
  width: 32px;
  height: 44px;
  border-radius: 4px;
  border: 2px solid #dcdee0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #323233;
  font-family: 'Courier New', monospace;
  background: #f7f8fa;
  transition: border-color 0.15s;
}

.plate-char--province {
  color: #1989fa;
}

.plate-char--empty {
  border-style: dashed;
}

.plate-char--active {
  border-color: #1989fa;
  box-shadow: 0 0 0 1px #1989fa;
}

.plate-char--new-energy {
  border-color: #07c160;
}

.plate-char--toggle {
  width: auto;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  color: #07c160;
  border-color: #07c160;
  cursor: pointer;
  font-family: inherit;
}

/* ---- 键盘面板 ---- */
.keyboard-panel {
  padding: 8px 4px;
}

.key-row {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-bottom: 6px;
}

/* ---- 按键 ---- */
.key-btn {
  width: 30px;
  height: 42px;
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 500;
  color: #323233;
  user-select: none;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  transition: background 0.1s;
}

.key-btn:active {
  background: #e5e5e5;
}

.key-btn--province {
  width: 52px;
  height: 40px;
  font-size: 15px;
  font-weight: 600;
}

.key-btn--back {
  flex: 1;
  max-width: 80px;
  background: #e8e8e8;
}

.key-btn--confirm {
  flex: 1;
  max-width: 200px;
  background: #1989fa;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
}

.key-btn--confirm:active {
  background: #1676d6;
}

.key-btn--disabled {
  opacity: 0.4;
  pointer-events: none;
}

.key-btn--back:active {
  background: #d0d0d0;
}

/* ---- 省份网格 ---- */
.province-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
  padding: 8px 0;
}

.province-grid .key-btn {
  margin: 2px;
}

.key-row--action {
  margin-top: 4px;
  gap: 8px;
}
</style>
