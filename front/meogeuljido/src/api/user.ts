import { apiClient } from './client'
import type { NicknameAvailabilityResponse } from '@/types/user'

export const checkNickname = (nickname: string) =>
  apiClient.get<NicknameAvailabilityResponse>('/users/check-nickname', { params: { nickname } })
