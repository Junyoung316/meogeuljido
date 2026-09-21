import axios, {type AxiosError } from 'axios'
import { accessToken, setAccessToken } from './token'
import type { ApiErrorBody } from '@/types/common'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  /**
   * refreshToken은 HttpOnly 쿠키로만 오가므로 모든 요청에 필요
   */
  withCredentials: true,
  timeout: 10000,
})

apiClient.interceptors.request.use((config) => {
  if (accessToken.value) {
    config.headers.Authorization= `Bearer ${accessToken.value}`
  }
  return config
})

const REISSUE_URL = '/auth/reissue'

/**
 * 여러 요청이 동시에 401을 맞아도 reissue는 한 번만 호출되도록 진행 중인 Promise를 공유
 */
let refreshPromise: Promise<string> | null = null

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (typeof error.config & { _retried?: boolean }) | undefined
    const hadAuthHeader = Boolean(original?.headers?.Authorization)

    if (error.response?.status !== 401 || !hadAuthHeader || !original || original._retried || original.url === REISSUE_URL) {
      return Promise.reject(error)
    }

    original._retried = true

    try {
      refreshPromise ??= apiClient
      .post<{ accessToken: string}>(REISSUE_URL)
      .then((res) => {
        setAccessToken(res.data.accessToken)
        return res.data.accessToken
      })
      const newToken = await refreshPromise
      original.headers = original.headers ?? {}
      original.headers.Authorization = `Bearer ${newToken}`
      return apiClient(original)
    } catch {
      setAccessToken(null)
      return Promise.reject(error)
    } finally {
      refreshPromise = null
    }
  },
)
