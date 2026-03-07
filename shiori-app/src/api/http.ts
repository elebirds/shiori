import axios, { AxiosError, AxiosHeaders, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'

import { ApiBizError, isResultPayload, unwrapResult } from '@/types/result'

export const ACCESS_TOKEN_KEY = 'shiori_access_token'
export const REFRESH_TOKEN_KEY = 'shiori_refresh_token'

interface TokenPairResponse {
  accessToken: string
  refreshToken: string
}

interface RetryableConfig extends InternalAxiosRequestConfig {
  __retried?: boolean
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
})

let refreshPromise: Promise<string> | null = null
let authFailureHandler: (() => void) | null = null

export function setAuthFailureHandler(handler: (() => void) | null): void {
  authFailureHandler = handler
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setTokenPair(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export function clearTokenPair(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

apiClient.interceptors.request.use((config) => {
  const headers = AxiosHeaders.from(config.headers)
  const token = getAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  } else {
    headers.delete('Authorization')
  }
  config.headers = headers
  return config
})

apiClient.interceptors.response.use(
  (response) => unwrapResult(response.data, response.status),
  async (error: AxiosError) => {
    const status = error.response?.status ?? 500
    const config = error.config as RetryableConfig | undefined

    if (status === 401 && config && shouldRefresh(config) && getRefreshToken()) {
      config.__retried = true

      try {
        const nextAccessToken = await refreshAccessTokenSingleFlight()
        const headers = AxiosHeaders.from(config.headers)
        headers.set('Authorization', `Bearer ${nextAccessToken}`)
        config.headers = headers
        return apiClient(config)
      } catch (refreshError) {
        handleAuthFailure()
        throw refreshError
      }
    }

    if (error.response && isResultPayload(error.response.data)) {
      throw new ApiBizError(error.response.data.code, error.response.data.message, status, error.response.data.data)
    }

    if (error.code === 'ECONNABORTED') {
      throw new ApiBizError(-1, '请求超时，请稍后重试', status)
    }

    if (!error.response) {
      throw new ApiBizError(-1, '网络异常，请检查服务是否启动', status)
    }

    throw new ApiBizError(-1, error.message || '请求失败', status)
  },
)

function shouldRefresh(config: RetryableConfig): boolean {
  if (config.__retried) {
    return false
  }

  const url = config.url || ''
  return !url.includes('/api/user/auth/login') && !url.includes('/api/user/auth/register') && !url.includes('/api/user/auth/refresh')
}

async function refreshAccessTokenSingleFlight(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new ApiBizError(20002, '登录状态已过期，请重新登录', 401)
  }

  const response = await refreshClient.post('/api/user/auth/refresh', { refreshToken })
  const data = unwrapResult<TokenPairResponse>(response.data, response.status)

  if (!data.accessToken || !data.refreshToken) {
    throw new ApiBizError(-1, '刷新令牌返回值不完整', response.status)
  }

  setTokenPair(data.accessToken, data.refreshToken)
  return data.accessToken
}

function handleAuthFailure(): void {
  clearTokenPair()
  if (authFailureHandler) {
    authFailureHandler()
  }
}

export async function httpGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const data = await apiClient.get(url, config)
  return data as T
}

export async function httpPost<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const data = await apiClient.post(url, body, config)
  return data as T
}

export async function httpPut<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const data = await apiClient.put(url, body, config)
  return data as T
}

export async function httpDelete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const data = await apiClient.delete(url, config)
  return data as T
}
