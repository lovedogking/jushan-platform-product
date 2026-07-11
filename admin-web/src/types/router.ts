import type { RouteMeta } from 'vue-router'

export interface AppRouteMeta extends RouteMeta {
  title?: string
  icon?: string
  cache?: boolean
  hidden?: boolean
}

export interface MenuItem {
  key: string
  label: string
  icon?: string
  path: string
  children?: MenuItem[]
}
