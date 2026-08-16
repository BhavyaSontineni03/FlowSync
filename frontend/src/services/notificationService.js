import api from './api'

export const notificationService = {
  async getAll(params) {
    const response = await api.get('/notifications', { params })
    return response.data
  },
  async getUnreadCount() {
    const response = await api.get('/notifications/unread-count')
    return response.data
  },
  async markAsRead(id) {
    const response = await api.put(`/notifications/${id}/read`)
    return response.data
  },
  async markAllAsRead() {
    const response = await api.put('/notifications/read-all')
    return response.data
  },
}

