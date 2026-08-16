import api from './api'

export const adminRequestService = {
  /**
   * Submit a user-related admin request (create, update, disable)
   */
  async submitUserRequest(payload) {
    const response = await api.post('/admin-requests/user', payload)
    return response.data
  },

  /**
   * Submit a project-related admin request (create, update, delete)
   */
  async submitProjectRequest(payload) {
    const response = await api.post('/admin-requests/project', payload)
    return response.data
  },

  /**
   * Submit an assignment-related admin request (assign, unassign employee to/from project)
   */
  async submitAssignmentRequest(payload) {
    const response = await api.post('/admin-requests/assignment', payload)
    return response.data
  },

  /**
   * Get all pending requests (Admin only)
   */
  async getPending() {
    const response = await api.get('/admin-requests/pending')
    return response.data
  },

  /**
   * Get all requests with optional status filter
   */
  async getAll(status = null) {
    const params = status ? { status } : {}
    const response = await api.get('/admin-requests', { params })
    return response.data
  },

  /**
   * Approve a pending request (Admin only)
   */
  async approve(id) {
    const response = await api.post(`/admin-requests/${id}/approve`)
    return response.data
  },

  /**
   * Reject a pending request with optional comments (Admin only)
   */
  async reject(id, comments) {
    const response = await api.post(`/admin-requests/${id}/reject`, { comments })
    return response.data
  }
}
