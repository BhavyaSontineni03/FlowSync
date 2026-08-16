import api from './api'

export const analyticsService = {
  async getAnalytics(startDate, endDate, module = 'expenses') {
    const params = { module }
    
    if (startDate) {
      // Handle both Date objects and string dates
      if (startDate instanceof Date) {
        params.startDate = startDate.toISOString().split('T')[0]
      } else if (typeof startDate === 'string') {
        params.startDate = startDate
      }
    }
    
    if (endDate) {
      // Handle both Date objects and string dates
      if (endDate instanceof Date) {
        params.endDate = endDate.toISOString().split('T')[0]
      } else if (typeof endDate === 'string') {
        params.endDate = endDate
      }
    }
    
    const response = await api.get('/analytics', { params })
    return response.data
  },
  
  async getExpenseAnalytics(startDate, endDate) {
    return analyticsService.getAnalytics(startDate, endDate, 'expenses')
  },
  
  async getLeaveAnalytics(startDate, endDate) {
    return analyticsService.getAnalytics(startDate, endDate, 'leave-requests')
  },
}

