import api from './api'

/**
 * Service for bulk operations on expenses.
 */
export const bulkOperationsService = {
  /**
   * Import expenses from CSV file.
   */
  importExpenses: async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/bulk/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return response.data
  },

  /**
   * Export expenses to CSV.
   */
  exportExpenses: async (expenseIds = null) => {
    const response = await api.post(
      '/bulk/export',
      expenseIds ? { expenseIds } : {},
      {
        responseType: 'blob',
      }
    )
    return response.data
  },

  /**
   * Bulk approve expenses.
   */
  bulkApprove: async (expenseIds, comments = null) => {
    const response = await api.post('/bulk/approve', {
      expenseIds,
      comments,
    })
    return response.data
  },

  /**
   * Bulk reject expenses.
   */
  bulkReject: async (expenseIds, comments) => {
    const response = await api.post('/bulk/reject', {
      expenseIds,
      comments,
    })
    return response.data
  },
}

