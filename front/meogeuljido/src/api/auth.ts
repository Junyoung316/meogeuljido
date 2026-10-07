import { apiClient } from './client'
import type {
  EmailAvailabilityResponse,
  EmailVerifiedTokenResponse,
  LoginResponse,
  ReissueResponse,
  SignupPayload,
  SignupUserResponse,
} from '@/types/auth'

export const checkEmail = (email: string) => apiClient.get<EmailAvailabilityResponse>('/auth/check-email', { params: { email } })

export const requestSignupVerification = (email: string) => apiClient.post<void>('/auth/signup/verify/request', { email })

export const confirmSignupVerification = (email: string, code: string) => apiClient.post<EmailVerifiedTokenResponse>('/auth/signup/verify/confirm', { email, code })

export const signup = (payload: SignupPayload) => apiClient.post<SignupUserResponse>('/auth/signup', payload)

export const login = (payload: { email: string; password: string; rememberMe: boolean }) => apiClient.post<LoginResponse>('/auth/login', payload)

export const requestLoginUnlock = (email: string) => apiClient.post<void>('/auth/login/unlock/request', { email })

export const confirmLoginUnlock = (email: string, code: string) => apiClient.post<void>('/auth/login/unlock/confirm', { email, code })

export const reissue = () => apiClient.post<ReissueResponse>('/auth/reissue')

export const logout = () => apiClient.post<void>('/auth/logout')
