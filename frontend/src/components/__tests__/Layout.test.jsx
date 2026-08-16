import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import Layout from '../Layout'
import { AuthProvider } from '../../contexts/AuthContext'
import { ThemeModeProvider } from '../../contexts/ThemeContext'
import { SnackbarProvider } from 'notistack'

// Mock API service first
vi.mock('../../services/api', () => {
  const mockApi = {
    defaults: {
      headers: {
        common: {}
      }
    },
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  }
  return {
    default: mockApi
  }
})

// Mock notification service
vi.mock('../../services/notificationService', () => ({
  notificationService: {
    getUnreadCount: vi.fn().mockResolvedValue(0), // service already unwraps response.data
  },
}))

// Mock localStorage
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: vi.fn((key) => store[key] || null),
    setItem: vi.fn((key, value) => { store[key] = value.toString() }),
    removeItem: vi.fn((key) => { delete store[key] }),
    clear: vi.fn(() => { store = {} })
  }
})()

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  writable: true,
  configurable: true
})

const renderWithAuth = async (user) => {
  // Set up localStorage before render
  localStorageMock.getItem.mockImplementation((key) => {
    if (key === 'user') return JSON.stringify(user)
    if (key === 'token') return 'mock-token'
    return null
  })

  const result = render(
    <BrowserRouter>
      <ThemeModeProvider>
        <SnackbarProvider>
          <AuthProvider>
            <Layout><div>Test Content</div></Layout>
          </AuthProvider>
        </SnackbarProvider>
      </ThemeModeProvider>
    </BrowserRouter>
  )
  
  // Wait for AuthContext to load and component to render
  await waitFor(() => {
    const dashboard = screen.queryAllByText('Dashboard')[0]
    if (!dashboard) {
      // If Dashboard not found, check if loading or error
      const loading = screen.queryByText(/loading/i)
      if (!loading) {
        // Component should be rendered by now
        throw new Error('Dashboard menu item not found')
      }
    }
  }, { timeout: 5000 })
  
  return result
}

describe('Layout - Role-Based Menu Visibility', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
  })

  it('should show all menu items for MANAGER', async () => {
    const managerUser = {
      userId: 1,
      role: 'MANAGER',
      email: 'manager@test.com',
      firstName: 'Jane',
      lastName: 'Manager',
    }

    await renderWithAuth(managerUser)

    expect(screen.getAllByText('Dashboard').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Expenses').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Approvals').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Analytics').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Activity Logs').length).toBeGreaterThan(0)
  })

  it('should show all menu items for ADMIN', async () => {
    const adminUser = {
      userId: 2,
      role: 'ADMIN',
      email: 'admin@test.com',
      firstName: 'Admin',
      lastName: 'User',
    }

    await renderWithAuth(adminUser)

    expect(screen.getAllByText('Dashboard').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Expenses').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Approvals').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Analytics').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Activity Logs').length).toBeGreaterThan(0)
  })

  it('should show all menu items for FINANCE', async () => {
    const financeUser = {
      userId: 3,
      role: 'FINANCE',
      email: 'finance@test.com',
      firstName: 'Finance',
      lastName: 'User',
    }

    await renderWithAuth(financeUser)

    expect(screen.getAllByText('Dashboard').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Expenses').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Approvals').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Analytics').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Activity Logs').length).toBeGreaterThan(0)
  })

  it('should NOT show Approvals menu for EMPLOYEE', async () => {
    const employeeUser = {
      userId: 4,
      role: 'EMPLOYEE',
      email: 'employee@test.com',
      firstName: 'John',
      lastName: 'Employee',
    }

    await renderWithAuth(employeeUser)

    expect(screen.getAllByText('Dashboard').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Expenses').length).toBeGreaterThan(0)
    expect(screen.queryAllByText('Approvals')).toHaveLength(0)
    expect(screen.queryAllByText('Analytics')).toHaveLength(0)
    expect(screen.getAllByText('Activity Logs').length).toBeGreaterThan(0)
  })

  it('should NOT show Analytics menu for EMPLOYEE', async () => {
    const employeeUser = {
      userId: 4,
      role: 'EMPLOYEE',
      email: 'employee@test.com',
      firstName: 'John',
      lastName: 'Employee',
    }

    await renderWithAuth(employeeUser)

    expect(screen.queryAllByText('Analytics')).toHaveLength(0)
  })
})
