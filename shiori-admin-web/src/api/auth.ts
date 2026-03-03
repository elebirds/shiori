import { httpPost } from '@/api/http'

export interface AuthUserInfo {
  userId: number
  userNo: string
  username: string
  roles: string[]
}

export interface TokenPairResponse {
  accessToken: string
  accessTokenExpiresIn: number
  refreshToken: string
  refreshTokenExpiresIn: number
  tokenType: string
  user: AuthUserInfo
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LogoutRequest {
  refreshToken: string
}

export function login(payload: LoginRequest): Promise<TokenPairResponse> {
  return httpPost('/api/user/auth/login', payload)
}

export function refresh(refreshToken: string): Promise<TokenPairResponse> {
  return httpPost('/api/user/auth/refresh', { refreshToken })
}

export function logout(payload: LogoutRequest): Promise<{ success: boolean }> {
  return httpPost('/api/user/auth/logout', payload)
}
