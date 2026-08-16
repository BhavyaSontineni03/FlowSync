import React, { Suspense, lazy } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Box, CircularProgress } from '@mui/material'
import { useAuth } from './contexts/AuthContext'
import Layout from './components/Layout'
import { canApprove, canAccessAnalytics } from './utils/roleUtils'

// Every route is its own chunk. Nobody opens this app and needs Payroll,
// Analytics, and User Management all in the same page load -- splitting
// per-route keeps the first paint (almost always Login or Dashboard) small
// and fast, and every other page loads on demand in the background.
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Expenses = lazy(() => import('./pages/Expenses'))
const ExpenseForm = lazy(() => import('./pages/ExpenseForm'))
const Approvals = lazy(() => import('./pages/Approvals'))
const Analytics = lazy(() => import('./pages/Analytics'))
const ActivityLogs = lazy(() => import('./pages/ActivityLogs'))
const Notifications = lazy(() => import('./pages/Notifications'))
const LeaveRequests = lazy(() => import('./pages/LeaveRequests'))
const LeaveRequestForm = lazy(() => import('./pages/LeaveRequestForm'))
const Timesheets = lazy(() => import('./pages/Timesheets'))
const Projects = lazy(() => import('./pages/Projects'))
const Payroll = lazy(() => import('./pages/Payroll'))
const Finance = lazy(() => import('./pages/Finance'))
const UserManagement = lazy(() => import('./pages/UserManagement'))
const AdminRequests = lazy(() => import('./pages/AdminRequests'))
const MyTeam = lazy(() => import('./pages/MyTeam'))
const MyEmployees = lazy(() => import('./pages/MyEmployees'))
const MyProjects = lazy(() => import('./pages/MyProjects'))
const Profile = lazy(() => import('./pages/Profile'))

// Protected Route Component
const ProtectedRoute = ({ children, allowedRoles }) => {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" />
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to="/dashboard" />
  }

  return children
}

// Shown while a lazy-loaded route chunk is fetched. Brief and unobtrusive
// by design -- on a warm cache this never even gets a chance to render.
const RouteFallback = () => (
  <Box
    sx={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '60vh',
    }}
  >
    <CircularProgress size={32} thickness={4} />
  </Box>
)

function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh' }}>
        <CircularProgress size={32} thickness={4} />
      </Box>
    )
  }

  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/login" element={!user ? <Login /> : <Navigate to="/dashboard" />} />
        <Route path="/register" element={!user ? <Register /> : <Navigate to="/dashboard" />} />
        <Route
          path="/*"
          element={
            user ? (
              <Layout>
                <Suspense fallback={<RouteFallback />}>
                  <Routes>
                    <Route path="/dashboard" element={<Dashboard />} />
                    <Route path="/expenses" element={<Expenses />} />
                    <Route path="/expenses/new" element={<ExpenseForm />} />
                    <Route path="/expenses/:id/edit" element={<ExpenseForm />} />
                    <Route
                      path="/approvals"
                      element={
                        <ProtectedRoute allowedRoles={['MANAGER', 'ADMIN', 'FINANCE', 'HR']}>
                          <Approvals />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/analytics"
                      element={
                        <ProtectedRoute allowedRoles={['MANAGER', 'ADMIN', 'FINANCE', 'HR']}>
                          <Analytics />
                        </ProtectedRoute>
                      }
                    />
                    <Route path="/activity-logs" element={<ActivityLogs />} />
                    <Route path="/notifications" element={<Notifications />} />
                    <Route path="/leave-requests" element={<LeaveRequests />} />
                    <Route path="/leave-requests/new" element={<LeaveRequestForm />} />
                    <Route path="/timesheets" element={<Timesheets />} />
                    <Route
                      path="/projects"
                      element={
                        <ProtectedRoute allowedRoles={['ADMIN']}>
                          <Projects />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/my-team"
                      element={
                        <ProtectedRoute allowedRoles={['MANAGER']}>
                          <MyTeam />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/my-employees"
                      element={
                        <ProtectedRoute allowedRoles={['HR']}>
                          <MyEmployees />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/my-projects"
                      element={
                        <ProtectedRoute allowedRoles={['EMPLOYEE']}>
                          <MyProjects />
                        </ProtectedRoute>
                      }
                    />
                    <Route path="/profile" element={<Profile />} />
                    <Route
                      path="/payroll"
                      element={
                        <ProtectedRoute allowedRoles={['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE']}>
                          <Payroll />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/finance"
                      element={
                        <ProtectedRoute allowedRoles={['FINANCE', 'ADMIN']}>
                          <Finance />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/user-management"
                      element={
                        <ProtectedRoute allowedRoles={['ADMIN']}>
                          <UserManagement />
                        </ProtectedRoute>
                      }
                    />
                    <Route
                      path="/admin-requests"
                      element={
                        <ProtectedRoute allowedRoles={['ADMIN']}>
                          <AdminRequests />
                        </ProtectedRoute>
                      }
                    />
                    <Route path="/" element={<Navigate to="/dashboard" />} />
                  </Routes>
                </Suspense>
              </Layout>
            ) : (
              <Navigate to="/login" />
            )
          }
        />
      </Routes>
    </Suspense>
  )
}

export default App
