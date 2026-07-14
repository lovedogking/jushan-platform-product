import { useAuthStore } from '@/stores'
import type { DirectiveBinding } from 'vue'

/**
 * 权限指令 v-permission。
 * <p>
 * 用法：
 * <ul>
 *   <li><button v-permission="'company:create'">新增</button></li>
 *   <li><button v-permission="['company:update', 'company:delete']">操作</button></li>
 * </ul>
 * 当前用户持有任意一个所需权限时显示元素，否则移除元素。
 */
export const vPermission = {
  mounted(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
    const authStore = useAuthStore()
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!authStore.hasPermission(required)) {
      el.remove()
    }
  },
}
