import api from './api'

export const financeService = {
  async getApprovedExpenses(page = 0, size = 20, sortBy = 'createdAt', sortDir = 'DESC') {
    const response = await api.get('/finance/expenses/approved', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async getPaidExpenses(page = 0, size = 20, sortBy = 'createdAt', sortDir = 'DESC') {
    const response = await api.get('/finance/expenses/paid', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async markAsPaid(expenseId) {
    const response = await api.post(`/finance/expenses/${expenseId}/mark-paid`)
    return response.data
  },

  async bulkMarkAsPaid(expenseIds) {
    const response = await api.post('/finance/expenses/bulk-mark-paid', { expenseIds })
    return response.data
  },

  // Payroll methods
  async getProcessedPayrolls(page = 0, size = 20, sortBy = 'periodYear', sortDir = 'DESC') {
    const response = await api.get('/finance/payroll/processed', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async getPaidPayrolls(page = 0, size = 20, sortBy = 'periodYear', sortDir = 'DESC') {
    const response = await api.get('/finance/payroll/paid', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async markPayrollAsPaid(payrollId) {
    const response = await api.post(`/finance/payroll/${payrollId}/mark-paid`)
    return response.data
  },

  async generatePayroll(month, year) {
    const response = await api.post('/finance/payroll/generate', { month, year })
    return response.data
  },

  async processPayroll(payrollId) {
    const response = await api.post(`/finance/payroll/${payrollId}/process`)
    return response.data
  },

  async getAllPayrolls(page = 0, size = 20, sortBy = 'periodYear', sortDir = 'DESC', userId = null) {
    const params = { page, size, sortBy, sortDir }
    if (userId) params.userId = userId
    const response = await api.get('/finance/payroll/all', { params })
    return response.data
  }
}
