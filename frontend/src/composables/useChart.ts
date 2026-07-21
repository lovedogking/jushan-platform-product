import * as echarts from 'echarts'
import { ref, onMounted, onBeforeUnmount, watch, type Ref } from 'vue'

export function useChart(
  containerRef: Ref<HTMLElement | null>,
  optionsFn: () => echarts.EChartsOption
) {
  const chart = ref<echarts.ECharts | null>(null)

  function initChart() {
    if (!containerRef.value) return
    chart.value = echarts.init(containerRef.value)
    chart.value.setOption(optionsFn())
  }

  function resize() {
    chart.value?.resize()
  }

  function updateChart() {
    chart.value?.setOption(optionsFn(), true)
  }

  onMounted(() => {
    initChart()
    window.addEventListener('resize', resize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    chart.value?.dispose()
  })

  return { chart, updateChart }
}
