<template>
  <span v-if="hasAuth">
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores'

const props = defineProps<{
  value: string | string[]
}>()

const authStore = useAuthStore()

const hasAuth = computed(() => authStore.hasPermission(props.value))
</script>
