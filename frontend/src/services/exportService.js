import api from './api'

export const exportService = {
  exportExpenses: (startDate, endDate) => {
    const params = {}
    if (startDate) params.startDate = startDate.toISOString().split('T')[0]
    if (endDate) params.endDate = endDate.toISOString().split('T')[0]
    
    return api.get('/export/expenses/csv', {
      params,
      responseType: 'blob',
    }).then((response) => {
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', `expenses_${new Date().toISOString().split('T')[0]}.csv`)
      document.body.appendChild(link)
      link.click()
      link.remove()
    })
  },
}

