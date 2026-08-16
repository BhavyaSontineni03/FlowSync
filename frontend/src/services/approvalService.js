import api from './api'

export const approvalService = {
  getPending: async () => {
    const response = await api.get('/approvals/pending')
    return response.data
  },
  approve: (expenseId, comments) => api.post(`/approvals/${expenseId}/approve`, { comments }),
  reject: (expenseId, comments) => api.post(`/approvals/${expenseId}/reject`, { comments }),
  getByExpense: (expenseId) => api.get(`/approvals/expense/${expenseId}`),
  
  /**
   * Get manager approval stats for expenses, leave requests, and timesheets
   * Returns counts and totals for pending/approved/rejected items from team members
   */
  getStats: async () => {
    const response = await api.get('/approvals/stats')
    return response.data
  },
}

