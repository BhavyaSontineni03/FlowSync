import api from './api'

export const timesheetService = {
  async getAll(page = 0, size = 20, sortBy = 'date', sortDir = 'DESC', userId = null) {
    const params = { page, size, sortBy, sortDir }
    if (userId) params.userId = userId
    const response = await api.get('/timesheets', { params })
    return response.data
  },

  async getPending() {
    const response = await api.get('/timesheets/pending')
    return response.data
  },

  async create(timesheet) {
    const response = await api.post('/timesheets', timesheet)
    return response.data
  },

  async submit(id) {
    const response = await api.post(`/timesheets/${id}/submit`)
    return response.data
  },

  async approve(id, comments = null) {
    const response = await api.post(`/timesheets/${id}/approve`, { comments })
    return response.data
  },

  async reject(id, comments) {
    const response = await api.post(`/timesheets/${id}/reject`, { comments })
    return response.data
  },

  /**
   * Get weekly timesheet entries (Mon-Fri).
   * Returns existing entries including auto-generated leave entries.
   */
  async getWeekly(weekStart = null) {
    const params = weekStart ? { weekStart } : {}
    const response = await api.get('/timesheets/weekly', { params })
    return response.data
  },

  /**
   * Get weekly summary with status for each day.
   * Shows which days have entries, leaves, or are empty.
   */
  async getWeeklySummary(weekStart = null) {
    const params = weekStart ? { weekStart } : {}
    const response = await api.get('/timesheets/weekly/summary', { params })
    return response.data
  }
}
