import api from './client'
import type { User } from '@/types'

export const usersApi = {
  getCurrentUser: async (): Promise<User> => {
    const response = await api.get<User>('/auth/super/me')
    return response.data
  }
}
