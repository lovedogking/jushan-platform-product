<template>
  <div class="company-tree">
    <!-- 搜索框：支持按公司名称过滤树节点 -->
    <a-input-search
      v-model:value="searchText"
      placeholder="搜索公司"
      allow-clear
      @search="onSearch"
    />
    <!-- 已选公司展示 -->
    <div v-if="selectedTitle" class="selected-bar">
      已选：<a-tag>{{ selectedTitle }}</a-tag>
    </div>
    <!-- 公司树 -->
    <a-tree
      :tree-data="filteredTreeData"
      :selected-keys="selectedKeys"
      :default-expand-all="true"
      block-node
      @select="handleSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { CompanyVO } from '@/api/company'

interface TreeNode {
  title: string
  key: number
  disabled: boolean
  children: TreeNode[]
  raw: CompanyVO
}

const props = withDefaults(
  defineProps<{
    /** 树数据源 */
    treeData: CompanyVO[]
    /** 当前选中的公司 id */
    value?: number | null
    /** 禁用的节点 id，例如编辑时不能选择自己作为上级 */
    disabledId?: number | null
  }>(),
  {
    value: null,
    disabledId: null,
  },
)

const emit = defineEmits<{
  (e: 'update:value', value: number | null): void
  (e: 'select', value: number, node: CompanyVO): void
}>()

const searchText = ref('')

/**
 * 将后端公司数据转换为 ant-design-vue 树节点。
 */
function convertNode(node: CompanyVO): TreeNode {
  return {
    title: node.name,
    key: node.id,
    disabled: props.disabledId !== null && node.id === props.disabledId,
    children: (node.children || []).map(convertNode),
    raw: node,
  }
}

const treeNodes = computed<TreeNode[]>(() => props.treeData.map(convertNode))

/**
 * 递归判断节点或其子节点是否匹配搜索文本。
 * 注意：过滤时会复制节点，避免修改原始 children。
 */
function matchNode(node: TreeNode, text: string): boolean {
  if (!text) return true
  const title = String(node.title || '').toLowerCase()
  if (title.includes(text.toLowerCase())) return true
  if (node.children?.length) {
    const matchedChildren = node.children.filter((child) => matchNode(child, text))
    node.children = matchedChildren
    return matchedChildren.length > 0
  }
  return false
}

/** 根据搜索文本过滤后的树数据 */
const filteredTreeData = computed<TreeNode[]>(() => {
  const text = searchText.value.trim()
  if (!text) return treeNodes.value
  return treeNodes.value
    .map((node) => ({ ...node, children: node.children ? [...node.children] : [] }))
    .filter((node) => matchNode(node, text))
})

/** 当前选中 keys */
const selectedKeys = computed<number[]>(() => (props.value ? [props.value] : []))

/** 当前选中的公司名称 */
const selectedTitle = computed<string>(() => {
  if (!props.value) return ''
  const node = findNodeById(props.treeData, props.value)
  return node?.name || ''
})

/**
 * 在原始树数据中递归查找指定 id 的节点。
 */
function findNodeById(nodes: CompanyVO[], id: number): CompanyVO | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findNodeById(node.children, id)
      if (found) return found
    }
  }
  return null
}

/**
 * 处理树节点选中事件。
 */
function handleSelect(keys: (string | number)[]) {
  const key = keys.length ? (keys[0] as number) : null
  emit('update:value', key)
  if (key !== null) {
    const node = findNodeById(props.treeData, key)
    if (node) {
      emit('select', key, node)
    }
  }
}

function onSearch() {
  // 搜索逻辑由 computed 自动处理，此处保留入口便于后续扩展防抖等逻辑
}
</script>

<style lang="scss" scoped>
.company-tree {
  width: 100%;
}

.selected-bar {
  margin: 8px 0;
}
</style>
