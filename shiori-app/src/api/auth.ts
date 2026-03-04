import { httpGet, httpPost, httpPut } from '@/api/http'

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface LogoutRequest {
  refreshToken: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface AuthUserInfo {
  userId: number
  userNo: string
  username: string
  roles: string[]
  mustChangePassword?: boolean
}

export interface TokenPairResponse {
  accessToken: string
  accessTokenExpiresIn: number
  refreshToken: string
  refreshTokenExpiresIn: number
  tokenType: string
  user: AuthUserInfo
}

export interface RegisterResponse {
  userId: number
  userNo: string
  username: string
  nickname: string
}

export interface UserProfile {
  userId: number
  userNo: string
  username: string
  nickname: string
  avatarUrl?: string
}

export interface UpdateProfileRequest {
  nickname: string
  avatarUrl?: string
}

export interface SimpleSuccessResponse {
  success: boolean
}

export function register(payload: RegisterRequest): Promise<RegisterResponse> {
  return httpPost<RegisterResponse>('/api/user/auth/register', payload)
}

export function login(payload: LoginRequest): Promise<TokenPairResponse> {
  return httpPost<TokenPairResponse>('/api/user/auth/login', payload)
}

export function refresh(payload: RefreshRequest): Promise<TokenPairResponse> {
  return httpPost<TokenPairResponse>('/api/user/auth/refresh', payload)
}

export function logout(payload: LogoutRequest): Promise<SimpleSuccessResponse> {
  return httpPost<SimpleSuccessResponse>('/api/user/auth/logout', payload)
}

export function changePassword(payload: ChangePasswordRequest): Promise<SimpleSuccessResponse> {
  return httpPost<SimpleSuccessResponse>('/api/user/auth/change-password', payload)
}

export function getMyProfile(): Promise<UserProfile> {
  return httpGet<UserProfile>('/api/user/me')
}

export function updateMyProfile(payload: UpdateProfileRequest): Promise<UserProfile> {
  return httpPut<UserProfile>('/api/user/me', payload)
}
