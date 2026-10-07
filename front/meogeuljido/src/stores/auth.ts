import { defineStore } from "pinia"
import { computed, ref } from "vue"
import { accessToken, setAccessToken } from "@/api/token"
import * as authApi from "@/api/auth"
import type { AuthUser } from "@/types/auth"

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const isLoggedIn = computed(() => accessToken.value !== null)

  async function login(email: string, password: string, rememberMe: boolean) {
    const { data } = await authApi.login({ email, password, rememberMe })
    setAccessToken(data.accessToken)
    user.value = data.user
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      setAccessToken(null)
      user.value = null
    }
  }

  /**
   * 새로고침 등으로 accessToken(메모리 상태)이 사라졌을 때 RT 쿠키로 세션을 복원
   * reissue 응답에는 user 정보가 없어 닉네임/role은 비어 있는 채로 시작
   * - 마이페이지(GET /api/users/me) 연동 시 채우는 것을 후속 과제로 남김
   */
  async function restoreSession() {
    try {
      const { data } = await authApi.reissue()
      setAccessToken(data.accessToken)
    } catch {
      setAccessToken(null)
    }
  }

  return { user, isLoggedIn, login, logout, restoreSession }
})
