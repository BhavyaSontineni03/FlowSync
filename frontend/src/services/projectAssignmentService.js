import api from './api'

export const projectAssignmentService = {
  async assignEmployee(userId, projectId, role = 'Team Member') {
    const response = await api.post('/project-assignments', { userId, projectId, role })
    return response.data
  },

  async unassignEmployee(userId, projectId) {
    const response = await api.delete(`/project-assignments/${userId}/${projectId}`)
    return response.data
  },

  async getByProject(projectId) {
    const response = await api.get(`/project-assignments/project/${projectId}`)
    return response.data
  },

  async getByUser(userId) {
    const response = await api.get(`/project-assignments/user/${userId}`)
    return response.data
  }
}
