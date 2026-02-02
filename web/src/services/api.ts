import axios, { type AxiosInstance, type AxiosError } from 'axios'
import { getToken } from './keycloak'
import type { Realm, RealmDetails, CreateRealmRequest, User, ApiError } from '@/types'

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const apiError: ApiError = {
      message: error.response?.data?.message ?? error.message ?? 'An error occurred',
      status: error.response?.status ?? 500,
      timestamp: new Date().toISOString()
    }
    return Promise.reject(apiError)
  }
)

export const realmApi = {
  list: async (): Promise<Realm[]> => {
    const response = await api.get<Realm[]>('/api/super/realms')
    return response.data
  },

  get: async (name: string): Promise<RealmDetails> => {
    const response = await api.get<RealmDetails>(`/api/super/realms/${name}`)
    return response.data
  },

  create: async (request: CreateRealmRequest): Promise<Realm> => {
    const response = await api.post<Realm>('/api/super/realms', request)
    return response.data
  },

  update: async (name: string, request: Partial<CreateRealmRequest>): Promise<Realm> => {
    const response = await api.put<Realm>(`/api/super/realms/${name}`, request)
    return response.data
  },

  delete: async (name: string): Promise<void> => {
    await api.delete(`/api/super/realms/${name}`)
  },

  enableSpi: async (name: string): Promise<void> => {
    await api.post(`/api/super/realms/${name}/spi`)
  },

  sync: async (name: string): Promise<void> => {
    await api.post(`/api/super/realms/${name}/sync`)
  }
}

export const userApi = {
  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/auth/super/me')
    return response.data
  }
}

export default api
