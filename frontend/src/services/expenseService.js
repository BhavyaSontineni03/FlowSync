import api from './api'

export const expenseService = {
  async getAll(params) {
    const response = await api.get('/expenses', { params })
    return response.data
  },
  async getById(id) {
    const response = await api.get(`/expenses/${id}`)
    return response.data
  },
  async create(data, receiptFile) {
    const formData = new FormData()
    formData.append('expense', JSON.stringify(data))
    if (receiptFile) {
      formData.append('receipt', receiptFile)
    }
    const response = await api.post('/expenses', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return response.data
  },
  async update(id, data) {
    const response = await api.put(`/expenses/${id}`, data)
    return response.data
  },
  async delete(id) {
    const response = await api.delete(`/expenses/${id}`)
    return response.data
  },
  async submit(id) {
    const response = await api.post(`/expenses/${id}/submit`)
    return response.data
  },
}

