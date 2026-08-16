import React, { useState, useEffect, useMemo } from 'react'
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  CircularProgress,
  Pagination,
  useTheme,
  Chip,
  alpha,
  Tabs,
  Tab,
} from '@mui/material'
import {
  Receipt as ReceiptIcon,
  EventNote as EventNoteIcon,
  AccessTime as AccessTimeIcon,
  AccountBalance as AccountBalanceIcon,
  Folder as FolderIcon,
  MoreHoriz as MoreHorizIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import api from '../services/api'
import { format } from 'date-fns'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { toCssColor } from '../utils/uiTokens'

const activityTypeConfig = {
  // Expense activities
  EXPENSE_CREATED: { color: 'primary.main', label: 'Expense Created' },
  EXPENSE_UPDATED: { color: 'info.main', label: 'Expense Updated' },
  EXPENSE_DELETED: { color: 'error.main', label: 'Expense Deleted' },
  EXPENSE_SUBMITTED: { color: 'warning.main', label: 'Expense Submitted' },
  EXPENSE_APPROVED: { color: 'primary.main', label: 'Expense Approved' },
  EXPENSE_REJECTED: { color: 'error.main', label: 'Expense Rejected' },
  EXPENSE_PAID: { color: 'primary.main', label: 'Expense Paid' },
  RECEIPT_UPLOADED: { color: 'info.main', label: 'Receipt Uploaded' },
  OCR_PROCESSED: { color: 'primary.main', label: 'OCR Processed' },
  // Leave request activities
  LEAVE_REQUEST_CREATED: { color: 'primary.main', label: 'Leave Request Created' },
  LEAVE_REQUEST_UPDATED: { color: 'info.main', label: 'Leave Request Updated' },
  LEAVE_REQUEST_APPROVED: { color: 'primary.main', label: 'Leave Request Approved' },
  LEAVE_REQUEST_REJECTED: { color: 'error.main', label: 'Leave Request Rejected' },
  LEAVE_REQUEST_CANCELLED: { color: 'warning.main', label: 'Leave Request Cancelled' },
  // Timesheet activities
  TIMESHEET_CREATED: { color: 'primary.main', label: 'Timesheet Created' },
  TIMESHEET_SUBMITTED: { color: 'warning.main', label: 'Timesheet Submitted' },
  TIMESHEET_APPROVED: { color: 'primary.main', label: 'Timesheet Approved' },
  TIMESHEET_REJECTED: { color: 'error.main', label: 'Timesheet Rejected' },
  // Payroll activities
  PAYROLL_CALCULATED: { color: 'primary.main', label: 'Payroll Calculated' },
  PAYROLL_PROCESSED: { color: 'primary.main', label: 'Payroll Processed' },
  // Project activities
  PROJECT_CREATED: { color: 'primary.main', label: 'Project Created' },
  PROJECT_UPDATED: { color: 'info.main', label: 'Project Updated' },
  PROJECT_DELETED: { color: 'error.main', label: 'Project Deleted' },
  PROJECT_EMPLOYEE_ASSIGNED: { color: 'primary.main', label: 'Employee Assigned to Project' },
  PROJECT_EMPLOYEE_UNASSIGNED: { color: 'warning.main', label: 'Employee Unassigned from Project' },
  // User activities
  USER_CREATED: { color: 'primary.main', label: 'User Created' },
  USER_UPDATED: { color: 'info.main', label: 'User Updated' },
  USER_DELETED: { color: 'error.main', label: 'User Deleted' },
  USER_LOGGED_IN: { color: 'primary.main', label: 'User Logged In' },
  // Organization activities
  ORGANIZATION_CREATED: { color: 'secondary.main', label: 'Organization Created' },
  ORGANIZATION_UPDATED: { color: 'info.main', label: 'Organization Updated' },
  // Admin request activities
  ADMIN_REQUEST_CREATED: { color: 'primary.main', label: 'Admin Request Created' },
  ADMIN_REQUEST_APPROVED: { color: 'primary.main', label: 'Admin Request Approved' },
  ADMIN_REQUEST_REJECTED: { color: 'error.main', label: 'Admin Request Rejected' },
}

export default function ActivityLogs() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [tabValue, setTabValue] = useState(0) // 0 = Expenses, 1 = Leave Requests, 2 = Timesheets, 3 = Payroll, 4 = Projects, 5 = Other
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    fetchLogs()
  }, [page])

  const fetchLogs = async () => {
    setLoading(true)
    try {
      const response = await api.get('/activity-logs', {
        params: { page, size: 20 },
      })
      setLogs(response.data.content || [])
      setTotalPages(response.data.totalPages || 0)
    } catch (error) {
      if (error.response?.status === 404) {
        setLogs([])
      } else {
        enqueueSnackbar('Error loading activity logs', { variant: 'error' })
      }
    } finally {
      setLoading(false)
    }
  }

  // Group logs by type
  const groupedLogs = useMemo(() => {
    const expenseLogs = logs.filter(log => 
      log.activityType?.startsWith('EXPENSE_') || 
      log.activityType === 'RECEIPT_UPLOADED' || 
      log.activityType === 'OCR_PROCESSED'
    )
    const leaveLogs = logs.filter(log => 
      log.activityType?.startsWith('LEAVE_REQUEST_')
    )
    const timesheetLogs = logs.filter(log => 
      log.activityType?.startsWith('TIMESHEET_')
    )
    const payrollLogs = logs.filter(log => 
      log.activityType?.startsWith('PAYROLL_')
    )
    const projectLogs = logs.filter(log => 
      log.activityType?.startsWith('PROJECT_')
    )
    const otherLogs = logs.filter(log => 
      !log.activityType?.startsWith('EXPENSE_') && 
      !log.activityType?.startsWith('LEAVE_REQUEST_') &&
      !log.activityType?.startsWith('TIMESHEET_') &&
      !log.activityType?.startsWith('PAYROLL_') &&
      !log.activityType?.startsWith('PROJECT_') &&
      log.activityType !== 'RECEIPT_UPLOADED' &&
      log.activityType !== 'OCR_PROCESSED'
    )
    return { expenseLogs, leaveLogs, timesheetLogs, payrollLogs, projectLogs, otherLogs }
  }, [logs])

  // Get current logs based on selected tab
  const getCurrentLogs = () => {
    switch (tabValue) {
      case 0:
        return groupedLogs.expenseLogs
      case 1:
        return groupedLogs.leaveLogs
      case 2:
        return groupedLogs.timesheetLogs
      case 3:
        return groupedLogs.payrollLogs
      case 4:
        return groupedLogs.projectLogs
      case 5:
        return groupedLogs.otherLogs
      default:
        return []
    }
  }

  const currentLogs = getCurrentLogs()

  const tabColors = [
    theme.palette.primary.main,
    theme.palette.info.main,
    theme.palette.warning.main,
    theme.palette.primary.main,
    theme.palette.secondary.main,
    theme.palette.text.secondary,
  ]
  const getTabColor = (index) => tabColors[index]
  const getTabIcon = (index) => {
    const icons = [
      <ReceiptIcon />,
      <EventNoteIcon />,
      <AccessTimeIcon />,
      <AccountBalanceIcon />,
      <FolderIcon />,
      <MoreHorizIcon />,
    ]
    return icons[index]
  }

  const getTabLabel = (index) => {
    const labels = ['Expenses', 'Leave Requests', 'Timesheets', 'Payroll', 'Projects', 'Other']
    return labels[index]
  }

  const getTabCount = (index) => {
    const counts = [
      groupedLogs.expenseLogs.length,
      groupedLogs.leaveLogs.length,
      groupedLogs.timesheetLogs.length,
      groupedLogs.payrollLogs.length,
      groupedLogs.projectLogs.length,
      groupedLogs.otherLogs.length,
    ]
    return counts[index]
  }

  return (
    <Box sx={{ p: { xs: 2, sm: 3, md: 4 } }}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 3, flexWrap: 'wrap', gap: 2 }}>
          <Box>
            <Typography
              variant="h4"
              component="h1"
              sx={{
                fontWeight: 600,
                fontSize: { xs: '1.75rem', md: '2rem' },
                mb: 0.5,
                color: 'text.primary',
              }}
            >
              Activity
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {user?.role === 'EMPLOYEE'
                ? 'Your own trail of changes.'
                : 'A quiet trail of what changed.'}
            </Typography>
          </Box>
        </Box>

        {/* Tabs */}
        <Box sx={{ mb: 4 }}>
          <Tabs
            value={tabValue}
            onChange={(e, newValue) => { setTabValue(newValue); setPage(0) }}
            sx={{
              mb: 3,
              '& .MuiTabs-indicator': {
                height: 3,
                borderRadius: '3px 3px 0 0',
                              },
              '& .MuiTab-root': {
                minHeight: 64,
                px: 3,
                gap: 1.5,
              },
            }}
            variant="scrollable"
            scrollButtons="auto"
          >
            {[0, 1, 2, 3, 4, 5].map((index) => (
              <Tab
                key={index}
                icon={getTabIcon(index)}
                iconPosition="start"
                label={
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                    <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                      {getTabLabel(index)}
                    </Typography>
                    {getTabCount(index) > 0 && (
                      <Chip
                        label={getTabCount(index)}
                        size="small"
                        sx={{
                          backgroundColor: isDark ? alpha(getTabColor(index), 0.2) : alpha(getTabColor(index), 0.15),
                          color: getTabColor(index),
                          fontWeight: 600,
                          fontSize: '0.75rem',
                          height: 22,
                          border: `1px solid ${alpha(getTabColor(index), 0.3)}`,
                        }}
                      />
                    )}
                  </Box>
                }
                sx={{
                  textTransform: 'none',
                  fontWeight: 600,
                  fontSize: '0.9375rem',
                  '&.Mui-selected': {
                    color: getTabColor(index),
                  },
                }}
              />
            ))}
          </Tabs>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
            <CircularProgress />
          </Box>
        ) : currentLogs.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
            style={{ willChange: 'transform' }}
          >
            <Card
              sx={{
                ...getSurfaceStyles(isDark, 'navigation'),
              }}
            >
              <CardContent sx={{ p: 4, textAlign: 'center' }}>
                <Typography 
                  color="text.secondary" 
                  sx={{ 
                    opacity: 0.6,
                    fontSize: '0.9375rem',
                  }}
                >
                  No {getTabLabel(tabValue).toLowerCase()} activity logs available
                </Typography>
              </CardContent>
            </Card>
          </motion.div>
        ) : (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1, duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
            style={{ willChange: 'transform' }}
          >
            <TableContainer
              component={Paper}
              sx={{
                ...getSurfaceStyles(isDark, 'navigation'),
                overflowX: 'auto',
              }}
            >
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell 
                      sx={{ 
                        fontWeight: 600, 
                        letterSpacing: '-0.01em',
                        py: 2,
                        px: 3,
                        fontSize: '0.75rem',
                        textTransform: 'uppercase',
                        opacity: isDark ? 0.85 : 0.75,
                        color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.12)'
                          : '1px solid rgba(0, 0, 0, 0.12)',
                      }}
                    >
                      Activity Type
                    </TableCell>
                    <TableCell 
                      sx={{ 
                        fontWeight: 600, 
                        letterSpacing: '-0.01em',
                        py: 2,
                        px: 3,
                        fontSize: '0.75rem',
                        textTransform: 'uppercase',
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Description
                    </TableCell>
                    <TableCell 
                      align="center"
                      sx={{ 
                        fontWeight: 600, 
                        letterSpacing: '-0.01em',
                        py: 2,
                        px: 3,
                        fontSize: '0.75rem',
                        textTransform: 'uppercase',
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      User
                    </TableCell>
                    <TableCell 
                      align="center"
                      sx={{ 
                        fontWeight: 600, 
                        letterSpacing: '-0.01em',
                        py: 2,
                        px: 3,
                        fontSize: '0.75rem',
                        textTransform: 'uppercase',
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Entity
                    </TableCell>
                    <TableCell 
                      align="center"
                      sx={{ 
                        fontWeight: 600, 
                        letterSpacing: '-0.01em',
                        py: 2,
                        px: 3,
                        fontSize: '0.75rem',
                        textTransform: 'uppercase',
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Timestamp
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {currentLogs.map((log) => {
                    const config = activityTypeConfig[log.activityType] || {
                      color: 'text.secondary',
                      label: log.activityType?.replace(/_/g, ' ') || 'Unknown',
                    }
                    
                    return (
                      <TableRow
                        key={log.id}
                        hover
                        sx={{
                          '&:hover': {
                            backgroundColor: isDark 
                              ? alpha(toCssColor(theme, config.color), 0.08)
                              : alpha(toCssColor(theme, config.color), 0.04),
                          },
                          '& td': {
                            borderBottom: isDark 
                              ? '1px solid rgba(255, 255, 255, 0.05)'
                              : '1px solid rgba(0, 0, 0, 0.05)',
                          },
                        }}
                      >
                        <TableCell sx={{ py: 2, px: 3 }}>
                          <Typography 
                            variant="body2"
                            sx={{
                              fontSize: '0.875rem',
                              letterSpacing: '-0.01em',
                              fontWeight: 600,
                              color: config.color,
                            }}
                          >
                            {config.label}
                          </Typography>
                        </TableCell>
                        <TableCell sx={{ py: 2, px: 3 }}>
                          <Typography 
                            variant="body2"
                            sx={{
                              fontSize: '0.875rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            {log.description}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Typography 
                            variant="body2"
                            sx={{
                              fontSize: '0.875rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            {log.user 
                              ? `${log.user.firstName || ''} ${log.user.lastName || ''}`.trim() || log.user.email || 'System'
                              : 'System'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          {log.entityType && log.entityId ? (
                            <Typography 
                              variant="body2" 
                              color="text.secondary"
                              sx={{
                                fontSize: '0.875rem',
                                letterSpacing: '-0.01em',
                                opacity: 0.7,
                              }}
                            >
                              {log.entityType} #{log.entityId}
                            </Typography>
                          ) : (
                            <Typography 
                              variant="body2" 
                              color="text.secondary"
                              sx={{
                                fontSize: '0.875rem',
                                opacity: 0.5,
                              }}
                            >
                              -
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Typography 
                            variant="body2"
                            sx={{
                              fontSize: '0.875rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            {log.createdAt
                              ? format(new Date(log.createdAt), 'MMM dd, yyyy HH:mm')
                              : '-'}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>

            {totalPages > 1 && (
              <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
                <Pagination
                  count={totalPages}
                  page={page + 1}
                  onChange={(e, value) => setPage(value - 1)}
                  color="primary"
                  sx={{
                    '& .MuiPaginationItem-root': {
                      borderRadius: 10,
                      fontWeight: 500,
                    },
                  }}
                />
              </Box>
            )}
          </motion.div>
        )}
      </motion.div>
    </Box>
  )
}
