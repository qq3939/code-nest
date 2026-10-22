import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import { setAuthExpiredHandler } from './api/http'
import router from './router'
import { useNotifyStore } from './stores/notify'
import { useUserStore } from './stores/user'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

setAuthExpiredHandler(() => {
  useUserStore().clear()
  useNotifyStore().disconnect()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
