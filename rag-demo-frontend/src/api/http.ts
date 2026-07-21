import axios from 'axios'
import type {ApiResponse} from '@/types'

export const http = axios.create({baseURL: '/api', withCredentials: true, timeout: 20_000})
let accessToken = ''
let refreshing: Promise<string> | null = null

/** 更新 Axios 与 Fetch 共享的内存 Access Token。 */
export function setAccessToken(token: string) {
  accessToken = token
}

/** 读取流式 Fetch 使用的 Access Token。 */
export function getAccessToken() {
  return accessToken
}

http.interceptors.request.use(config => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config
})
http.interceptors.response.use(r => r, async error => {
  const config = error.config;
  if (error.response?.status !== 401 || config?._retried || config?.url === '/auth/refresh') throw error;
  config._retried = true;
  refreshing ??= http.post<ApiResponse<{ accessToken: string }>>('/auth/refresh').then(r => {
    setAccessToken(r.data.data.accessToken);
    return r.data.data.accessToken
  }).finally(() => {
    refreshing = null
  });
  const token = await refreshing;
  config.headers.Authorization = `Bearer ${token}`;
  return http(config)
})

/** 解包统一响应并把后端提示转为异常。 */
export async function api<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  try {
    const response = await promise;
    if (response.data.code !== '0') throw new Error(response.data.message);
    return response.data.data
  } catch (error: unknown) {
    if (axios.isAxiosError(error)) throw new Error(error.response?.data?.message ?? '网络请求失败');
    throw error
  }
}
