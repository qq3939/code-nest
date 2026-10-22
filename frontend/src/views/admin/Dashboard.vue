<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { adminApi } from '@/api'
import type { Dashboard } from '@/types'

// 按需引入，只打包折线图需要的部分
echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const data = ref<Dashboard | null>(null)
const chartEl = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts>()

function render(d: Dashboard) {
  if (!chartEl.value) return
  chart.value ??= echarts.init(chartEl.value)
  const days = d.days.map((x) => x.date.slice(5))
  const series = (name: string, values: number[]) => ({ name, type: 'line' as const, smooth: true, data: values })
  chart.value.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      series('日活', d.days.map((x) => x.dau)),
      series('新用户', d.days.map((x) => x.newUsers)),
      series('新文章', d.days.map((x) => x.newArticles)),
      series('阅读', d.days.map((x) => x.reads)),
      series('点赞', d.days.map((x) => x.likes)),
    ],
  })
}

const onResize = () => chart.value?.resize()

onMounted(async () => {
  data.value = await adminApi.dashboard()
  render(data.value)
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart.value?.dispose()
})
</script>

<template>
  <div>
    <div class="cards">
      <el-card shadow="never"><el-statistic title="用户总数" :value="data?.totalUsers ?? 0" /></el-card>
      <el-card shadow="never"><el-statistic title="文章总数" :value="data?.totalArticles ?? 0" /></el-card>
      <el-card shadow="never">
        <el-statistic title="待审核" :value="data?.pendingArticles ?? 0" />
        <RouterLink v-if="data?.pendingArticles" :to="{ name: 'admin-review' }" class="link">去审核</RouterLink>
      </el-card>
      <el-card shadow="never"><el-statistic title="今日日活" :value="data?.days.at(-1)?.dau ?? 0" /></el-card>
    </div>
    <el-card shadow="never" header="最近 30 天趋势">
      <div ref="chartEl" class="chart" />
      <p class="pa-muted">当天数据实时来自 Redis（HyperLogLog 统计日活），历史数据来自每日凌晨归档的 daily_stat 表。</p>
    </el-card>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.chart {
  height: 360px;
}
.link {
  color: var(--pa-primary);
  font-size: 13px;
}
</style>
