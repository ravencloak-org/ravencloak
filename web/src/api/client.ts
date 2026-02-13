import axios, { type AxiosInstance, type AxiosError } from 'axios'
import { getToken } from '@/services/keycloak'
import type { ApiError } from '@/types'

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
    'API-Version': '1.0'
  }
})

api.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // Ensure API-Version header is always present
    config.headers['API-Version'] = '1.0'

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

export default api
