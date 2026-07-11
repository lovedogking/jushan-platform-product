import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TabItem {
  key: string
  title: string
  path: string
  closable: boolean
}

const HOME_TAB: TabItem = {
  key: '/dashboard',
  title: '经营驾驶舱',
  path: '/dashboard',
  closable: false,
}

export const useTabsStore = defineStore('tabs', () => {
  const tabs = ref<TabItem[]>([HOME_TAB])
  const activeKey = ref('/dashboard')

  function addTab(route: RouteLocationNormalized) {
    const { path, meta } = route
    const title = (meta?.title as string) || '未命名'

    if (path === '/dashboard') {
      activeKey.value = path
      return
    }

    const exist = tabs.value.find(tab => tab.path === path)
    if (exist) {
      activeKey.value = path
      return
    }

    tabs.value.push({ key: path, title, path, closable: true })
    activeKey.value = path
  }

  function closeTab(path: string) {
    const index = tabs.value.findIndex(tab => tab.path === path)
    if (index === -1) return
    if (!tabs.value[index].closable) return

    tabs.value.splice(index, 1)

    if (activeKey.value === path) {
      const newIndex = Math.min(index, tabs.value.length - 1)
      activeKey.value = tabs.value[newIndex].path
    }
    return activeKey.value
  }

  function closeOtherTabs(path: string) {
    tabs.value = tabs.value.filter(tab => !tab.closable || tab.path === path)
    activeKey.value = path
  }

  function closeAllTabs() {
    tabs.value = tabs.value.filter(tab => !tab.closable)
    activeKey.value = HOME_TAB.path
    return activeKey.value
  }

  return { tabs, activeKey, addTab, closeTab, closeOtherTabs, closeAllTabs }
})
