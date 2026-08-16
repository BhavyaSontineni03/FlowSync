import api from './api'

/**
 * Service for leave request operations.
 */
export const leaveRequestService = {
  /**
   * Create a new leave request.
   */
  createLeaveRequest: async (leaveRequest) => {
    const response = await api.post('/leave-requests', leaveRequest)
    return response.data
  },

  /**
   * Get leave requests with pagination.
   */
  getLeaveRequests: async (page = 0, size = 20, sortBy = 'createdAt', sortDir = 'DESC', userId = null) => {
    const params = { page, size, sortBy, sortDir }
    if (userId) {
      params.userId = userId
    }
    const response = await api.get('/leave-requests', { params })
    return response.data
  },

  /**
   * Get pending leave requests for approval.
   */
  getPendingLeaveRequests: async () => {
    const response = await api.get('/leave-requests/pending')
    return response.data
  },

  /**
   * Approve a leave request.
   */
  approveLeaveRequest: async (leaveRequestId, comments = null) => {
    const response = await api.post(`/leave-requests/${leaveRequestId}/approve`, { comments })
    return response.data
  },

  /**
   * Reject a leave request.
   */
  rejectLeaveRequest: async (leaveRequestId, comments) => {
    const response = await api.post(`/leave-requests/${leaveRequestId}/reject`, { comments })
    return response.data
  },

  /**
   * Get leave balance for a user (specific type).
   */
  getLeaveBalance: async (leaveType = 'VACATION', year = null) => {
    const params = { leaveType }
    if (year) {
      params.year = year
    }
    const response = await api.get('/leave-requests/balance', { params })
    return response.data
  },

  /**
   * Get complete leave balance summary for a user.
   * Returns all leave types with allocated/used/remaining counts.
   */
  getLeaveBalanceSummary: async () => {
    const response = await api.get('/leave-requests/balance/summary')
    return response.data
  }
}

