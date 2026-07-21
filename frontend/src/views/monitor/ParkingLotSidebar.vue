<template>
  <div class="lot-sidebar">
    <div class="sidebar-title">停车场列表</div>
    <div class="lot-list">
      <div
        v-for="lot in lots"
        :key="lot.id"
        class="lot-item"
        :class="{ active: lot.id === selectedId }"
        @click="$emit('select', lot.id)"
      >
        <div class="lot-name">
          <span class="dot" :class="lot.id === selectedId ? 'dot-active' : 'dot-inactive'" />
          {{ lot.name }}
        </div>
        <a-badge
          v-if="lot.currentVehicles != null"
          :count="lot.currentVehicles"
          :number-style="{ backgroundColor: '#1890ff' }"
          :overflow-count="999"
        />
      </div>
      <a-empty v-if="lots.length === 0" description="无授权车场" :image-style="{ height: '40px' }" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  lots: { id: number; name: string; status: string; currentVehicles?: number }[]
  selectedId: number | null
}>()

defineEmits<{
  select: [id: number]
}>()
</script>

<style lang="scss" scoped>
.lot-sidebar {
  width: 200px;
  min-width: 200px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.sidebar-title {
  padding: 16px;
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.lot-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.lot-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  transition: background-color 0.15s;
  border-left: 3px solid transparent;

  &:hover {
    background-color: #f0f5ff;
  }

  &.active {
    background-color: #e6f0ff;
    border-left-color: #1890ff;
  }
}

.lot-name {
  font-size: 13px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-active {
  background-color: #1890ff;
}
.dot-inactive {
  background-color: #d1d5db;
}
</style>
