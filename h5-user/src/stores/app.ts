import { defineStore } from 'pinia'
import { ref } from 'vue'

const RECENT_PLATE_KEY = 'h5_recent_plate'

export const useAppStore = defineStore('app', () => {
  // ========== state ==========
  const recentPlate = ref<string>(sessionStorage.getItem(RECENT_PLATE_KEY) || '')

  // ========== actions ==========
  function setRecentPlate(plate: string) {
    recentPlate.value = plate
    sessionStorage.setItem(RECENT_PLATE_KEY, plate)
  }

  return {
    recentPlate,
    setRecentPlate,
  }
})
