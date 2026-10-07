import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 새로고침 등으로 accessToken(메모리)이 사라진 상태일 수 있으므로,
// 화면을 그리기 전에 RT 쿠키로 세션 복원을 먼저 시도한다.
useAuthStore().restoreSession().finally(() => {
  app.mount('#app')
})
