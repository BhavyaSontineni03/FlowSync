import api from './api'

export const payrollService = {
  async getAll(page = 0, size = 20, sortBy = 'periodYear', sortDir = 'DESC', userId = null) {
    const params = { page, size, sortBy, sortDir }
    if (userId) params.userId = userId
    const response = await api.get('/payroll', { params })
    return response.data
  },

  async getById(id) {
    const response = await api.get(`/payroll/${id}`)
    return response.data
  },

  async calculate(userId, month, year) {
    const response = await api.post('/payroll/calculate', { userId, month, year })
    return response.data
  },

  async generateAll(month, year) {
    const response = await api.post('/payroll/generate-all', { month, year })
    return response.data
  },

  async process(id) {
    const response = await api.post(`/payroll/${id}/process`)
    return response.data
  }
}
