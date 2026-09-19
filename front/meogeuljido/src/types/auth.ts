export type UserRole = 'USER' | 'ADMIN'

export interface AuthUser {
  id: number
  nickname:string
  role: UserRole
}

export interface LoginResponse {
  accessToken: string
  user: AuthUser
}

export interface EmailAvailabilityResponse {
  available: boolean
}

export interface EmailVerifiedTokenResponse {
  emailVerifiedToken: string
}

export interface ReissueResponse {
  accessToken: string
}
export interface SignupPayload {
  email: string
  nickname: string
  password: string
  emailVerifiedToken: string
}

// POST /api/auth/signup 응답 - User 스키마(자동 로그인 X)
export interface SignupUserResponse {
  id: number
  email: string
  nickname: string
  role: UserRole
  createdAt: string
}
