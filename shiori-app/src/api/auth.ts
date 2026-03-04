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
  gender: number
  birthDate?: string
  age?: number
  bio?: string
  avatarUrl?: string
}

export interface PublicUserProfile {
  userId: number
  userNo: string
  username: string
  nickname: string
  avatarUrl?: string
  gender: number
  age?: number
  bio?: string
}

export interface UpdateProfileRequest {
  nickname: string
  gender: number
  birthDate?: string
  bio?: string
}

export interface AvatarUploadResponse {
  avatarUrl: string
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

export function getUserProfileByUserNo(userNo: string): Promise<PublicUserProfile> {
  return httpGet<PublicUserProfile>(`/api/user/profiles/${encodeURIComponent(userNo)}`)
}

export function getUserProfilesByUserIds(userIds: number[]): Promise<PublicUserProfile[]> {
  const normalized = Array.from(new Set(userIds.filter((item) => Number.isFinite(item) && item > 0)))
  return httpGet<PublicUserProfile[]>('/api/user/profiles/by-user-ids', {
    params: {
      userIds: normalized.join(','),
    },
  })
}

export function updateMyProfile(payload: UpdateProfileRequest): Promise<UserProfile> {
  return httpPut<UserProfile>('/api/user/me', payload)
}

export async function uploadMyAvatar(file: File): Promise<AvatarUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return httpPost<AvatarUploadResponse>('/api/user/media/avatar', formData)
}
