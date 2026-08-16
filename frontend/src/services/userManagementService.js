import api from './api'

export const userManagementService = {
  async getAll(page = 0, size = 20, sortBy = 'firstName', sortDir = 'ASC') {
    const response = await api.get('/users', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async getAllUsers() {
    const response = await api.get('/users/all')
    return response.data
  },

  /**
   * Get team members who report to the current user (for managers)
   */
  async getMyTeam() {
    const response = await api.get('/users/my-team')
    return response.data
  },

  /**
   * Get employees assigned to the current HR (for HR role only)
   * HR has a static relationship with employees, regardless of projects
   */
  async getMyEmployees() {
    const response = await api.get('/users/my-employees')
    return response.data
  },

  /**
   * Get current user's profile
   */
  async getMyProfile() {
    const response = await api.get('/users/profile')
    return response.data
  },

  /**
   * Submit profile update request to admin for approval
   * @param {Object} updates - Fields to update (firstName, lastName, phoneNumber, address)
   */
  async requestProfileUpdate(updates) {
    const response = await api.post('/users/profile/update-request', updates)
    return response.data
  },

  async createUser(payload) {
    const response = await api.post('/users', payload)
    return response.data
  },

  async updateUser(userId, payload) {
    const response = await api.put(`/users/${userId}`, payload)
    return response.data
  },

  async assignManager(userId, managerId) {
    const response = await api.put(`/users/${userId}/manager`, { managerId })
    return response.data
  },

  async assignHR(userId, hrId) {
    const response = await api.put(`/users/${userId}/hr`, { hrId })
    return response.data
  },

  async updateSalary(userId, monthlySalary) {
    const response = await api.put(`/users/${userId}/salary`, { monthlySalary })
    return response.data
  }
}
