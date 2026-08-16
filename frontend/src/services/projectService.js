import api from './api'

export const projectService = {
  async getAll(page = 0, size = 20, sortBy = 'name', sortDir = 'ASC') {
    const response = await api.get('/projects', {
      params: { page, size, sortBy, sortDir }
    })
    return response.data
  },

  async getActive() {
    const response = await api.get('/projects/active')
    return response.data
  },

  /**
   * Get projects managed by the current user (for managers)
   */
  async getMyProjects() {
    const response = await api.get('/projects/my-projects')
    return response.data
  },

  async getById(id) {
    const response = await api.get(`/projects/${id}`)
    return response.data
  },

  async create(project) {
    const response = await api.post('/projects', project)
    return response.data
  },

  async update(id, project) {
    const response = await api.put(`/projects/${id}`, project)
    return response.data
  },

  async delete(id) {
    const response = await api.delete(`/projects/${id}`)
    return response.data
  }
}
