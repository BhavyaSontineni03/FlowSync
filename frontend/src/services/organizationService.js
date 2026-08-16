import api from './api'

export const organizationService = {
  async register(data) {
    const response = await api.post('/organizations/register', data)
    return response.data
  },
  async getBySubdomain(subdomain) {
    const response = await api.get(`/organizations/subdomain/${subdomain}`)
    return response.data
  },
}

