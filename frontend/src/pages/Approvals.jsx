import React, { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
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
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  CircularProgress,
  useTheme,
  Tabs,
  Tab,
  Chip,
  alpha,
} from '@mui/material'
import { motion } from 'framer-motion'
import { 
  CheckCircle as CheckCircleIcon, 
  Cancel as CancelIcon,
  Receipt as ReceiptIcon,
  EventNote as EventNoteIcon,
  AccessTime as TimesheetIcon,
  TrendingUp as TrendingUpIcon,
  HourglassEmpty as HourglassIcon,
  ThumbUp as ThumbUpIcon,
  ThumbDown as ThumbDownIcon,
} from '@mui/icons-material'
import { approvalService } from '../services/approvalService'
import { leaveRequestService } from '../services/leaveRequestService'
import { timesheetService } from '../services/timesheetService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { useAuth } from '../contexts/AuthContext'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  sectionGap,
  softRadius,
  paletteTone,
} from '../utils/uiTokens'

export default function Approvals() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const initialTab = parseInt(searchParams.get('tab') || '0', 10)
  const [tabValue, setTabValue] = useState(initialTab) // 0 = Expenses, 1 = Leave Requests, 2 = Timesheets
  const [expenseApprovals, setExpenseApprovals] = useState([])
  const [leaveApprovals, setLeaveApprovals] = useState([])
  const [timesheetApprovals, setTimesheetApprovals] = useState([])
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)
  const [actionDialog, setActionDialog] = useState({ 
    open: false, 
    item: null, 
    type: null, // 'expense', 'leave', or 'timesheet'
    action: null 
  })
  const [comments, setComments] = useState('')
  const [processing, setProcessing] = useState(false)
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  
  // Only MANAGER can access this page
  if (user?.role !== 'MANAGER') {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <Typography variant="h6" color="error">
          Access Denied. Only Managers can access the Approvals page.
        </Typography>
      </Box>
    )
  }

  // Custom TextField styling to fix label background for glassmorphic dialogs
  const textFieldSx = {
    '& .MuiInputLabel-root.MuiInputLabel-shrink': {
      backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
      padding: '0 8px',
      borderRadius: 1,
    },
    '& .MuiOutlinedInput-root': {
      borderRadius: softRadius,
    },
  }

  useEffect(() => {
    fetchPendingApprovals()
  }, [user])

  // Update tab value when URL parameter changes
  useEffect(() => {
    const tabParam = parseInt(searchParams.get('tab') || '0', 10)
    if (tabParam !== tabValue && tabParam >= 0 && tabParam <= 2) {
      setTabValue(tabParam)
    }
  }, [searchParams])

  const fetchPendingApprovals = async () => {
    setLoading(true)
    try {
      // Fetch expense, leave request, timesheet approvals, and stats
      const [expenseRes, leaveRes, timesheetRes, statsRes] = await Promise.all([
        approvalService.getPending().catch(() => []),
        leaveRequestService.getPendingLeaveRequests().catch(() => []),
        timesheetService.getPending().catch(() => []),
        approvalService.getStats().catch(() => null)
      ])
      
      setExpenseApprovals(expenseRes || [])
      setLeaveApprovals(leaveRes || [])
      setTimesheetApprovals(timesheetRes || [])
      setStats(statsRes)
    } catch (error) {
      enqueueSnackbar('Error loading approvals', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const handleApprove = async () => {
    if (!actionDialog.item) return
    
    setProcessing(true)
    try {
      if (actionDialog.type === 'expense') {
        await approvalService.approve(actionDialog.item.id, comments)
        enqueueSnackbar('Expense approved successfully', { variant: 'success' })
      } else if (actionDialog.type === 'leave') {
        await leaveRequestService.approveLeaveRequest(actionDialog.item.id, comments)
        enqueueSnackbar('Leave request approved successfully', { variant: 'success' })
      } else if (actionDialog.type === 'timesheet') {
        await timesheetService.approve(actionDialog.item.id, comments)
        enqueueSnackbar('Timesheet approved successfully', { variant: 'success' })
      }
      setActionDialog({ open: false, item: null, type: null, action: null })
      setComments('')
      fetchPendingApprovals()
    } catch (error) {
      const itemTypes = { expense: 'expense', leave: 'leave request', timesheet: 'timesheet' }
      const itemType = itemTypes[actionDialog.type] || 'item'
      enqueueSnackbar(error.response?.data?.message || `Error approving ${itemType}`, { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleReject = async () => {
    if (!actionDialog.item || !comments.trim()) {
      enqueueSnackbar('Please provide rejection comments', { variant: 'warning' })
      return
    }

    setProcessing(true)
    try {
      if (actionDialog.type === 'expense') {
        await approvalService.reject(actionDialog.item.id, comments)
        enqueueSnackbar('Expense rejected successfully', { variant: 'success' })
      } else if (actionDialog.type === 'leave') {
        await leaveRequestService.rejectLeaveRequest(actionDialog.item.id, comments)
        enqueueSnackbar('Leave request rejected successfully', { variant: 'success' })
      } else if (actionDialog.type === 'timesheet') {
        await timesheetService.reject(actionDialog.item.id, comments)
        enqueueSnackbar('Timesheet rejected successfully', { variant: 'success' })
      }
      setActionDialog({ open: false, item: null, type: null, action: null })
      setComments('')
      fetchPendingApprovals()
    } catch (error) {
      const itemTypes = { expense: 'expense', leave: 'leave request', timesheet: 'timesheet' }
      const itemType = itemTypes[actionDialog.type] || 'item'
      enqueueSnackbar(error.response?.data?.message || `Error rejecting ${itemType}`, { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  // Determine which approvals to show based on tab
  const getCurrentApprovals = () => {
    switch (tabValue) {
      case 0:
        return expenseApprovals
      case 1:
        return leaveApprovals
      case 2:
        return timesheetApprovals
      default:
        return []
    }
  }
  
  const currentApprovals = getCurrentApprovals()
  const totalPending = expenseApprovals.length + leaveApprovals.length + timesheetApprovals.length
  
  // Check if user has any pending approvals at all
  const hasAnyApprovals = totalPending > 0

  // Stats Card Component
  const StatsCard = ({ icon: Icon, label, value, subValue, color, bgColor }) => (
    <Card
      sx={{
        ...getSurfaceStyles(isDark, 'card'),
        flex: 1,
        minWidth: 140,
      }}
    >
      <CardContent sx={{ p: 2.5, '&:last-child': { pb: 2.5 } }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 40,
              height: 40,
              borderRadius: 2,
              backgroundColor: bgColor,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Icon sx={{ color, fontSize: 22 }} />
          </Box>
          <Box>
            <Typography variant="h5" sx={{ fontWeight: 700, color, lineHeight: 1.2 }}>
              {value}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ opacity: 0.7 }}>
              {label}
            </Typography>
            {subValue && (
              <Typography variant="caption" sx={{ display: 'block', color, opacity: 0.8, fontWeight: 500 }}>
                {subValue}
              </Typography>
            )}
          </Box>
        </Box>
      </CardContent>
    </Card>
  )

  // Render stats for each tab
  const renderStats = () => {
    if (!stats) return null

    if (tabValue === 0) {
      // Expense Stats
      const expenseStats = stats.expenses || {}
      return (
        <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
          <StatsCard
            icon={HourglassIcon}
            label="Pending"
            value={expenseStats.pending || 0}
            subValue={expenseStats.pendingAmount ? `₹${Number(expenseStats.pendingAmount).toLocaleString()}` : null}
            color="#B8894A"
            bgColor={isDark ? 'rgba(184, 137, 74, 0.15)' : 'rgba(184, 137, 74, 0.1)'}
          />
          <StatsCard
            icon={ThumbUpIcon}
            label="Approved"
            value={expenseStats.approved || 0}
            subValue={expenseStats.approvedAmount ? `₹${Number(expenseStats.approvedAmount).toLocaleString()}` : null}
            color="#4F7A5C"
            bgColor={isDark ? 'rgba(79, 122, 92, 0.15)' : 'rgba(79, 122, 92, 0.1)'}
          />
          <StatsCard
            icon={ThumbDownIcon}
            label="Rejected"
            value={expenseStats.rejected || 0}
            color="#C45B6A"
            bgColor={isDark ? 'rgba(196, 91, 106, 0.15)' : 'rgba(196, 91, 106, 0.1)'}
          />
        </Box>
      )
    }

    if (tabValue === 1) {
      // Leave Request Stats
      const leaveStats = stats.leaveRequests || {}
      return (
        <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
          <StatsCard
            icon={HourglassIcon}
            label="Pending"
            value={leaveStats.pending || 0}
            subValue={leaveStats.pendingDays ? `${leaveStats.pendingDays} days` : null}
            color="#B8894A"
            bgColor={isDark ? 'rgba(184, 137, 74, 0.15)' : 'rgba(184, 137, 74, 0.1)'}
          />
          <StatsCard
            icon={ThumbUpIcon}
            label="Approved"
            value={leaveStats.approved || 0}
            subValue={leaveStats.approvedDays ? `${leaveStats.approvedDays} days` : null}
            color="#4F7A5C"
            bgColor={isDark ? 'rgba(79, 122, 92, 0.15)' : 'rgba(79, 122, 92, 0.1)'}
          />
          <StatsCard
            icon={ThumbDownIcon}
            label="Rejected"
            value={leaveStats.rejected || 0}
            color="#C45B6A"
            bgColor={isDark ? 'rgba(196, 91, 106, 0.15)' : 'rgba(196, 91, 106, 0.1)'}
          />
        </Box>
      )
    }

    if (tabValue === 2) {
      // Timesheet Stats
      const timesheetStats = stats.timesheets || {}
      return (
        <Box sx={{ display: 'flex', gap: 2, mb: 3, flexWrap: 'wrap' }}>
          <StatsCard
            icon={HourglassIcon}
            label="Pending"
            value={timesheetStats.pending || 0}
            subValue={timesheetStats.pendingHours ? `${Number(timesheetStats.pendingHours).toFixed(1)} hrs` : null}
            color="#B8894A"
            bgColor={isDark ? 'rgba(184, 137, 74, 0.15)' : 'rgba(184, 137, 74, 0.1)'}
          />
          <StatsCard
            icon={ThumbUpIcon}
            label="Approved"
            value={timesheetStats.approved || 0}
            subValue={timesheetStats.approvedHours ? `${Number(timesheetStats.approvedHours).toFixed(1)} hrs` : null}
            color="#4F7A5C"
            bgColor={isDark ? 'rgba(79, 122, 92, 0.15)' : 'rgba(79, 122, 92, 0.1)'}
          />
          <StatsCard
            icon={ThumbDownIcon}
            label="Rejected"
            value={timesheetStats.rejected || 0}
            color="#C45B6A"
            bgColor={isDark ? 'rgba(196, 91, 106, 0.15)' : 'rgba(196, 91, 106, 0.1)'}
          />
        </Box>
      )
    }

    return null
  }

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box>
            <Typography
              variant="h4"
              component="h1"
              sx={pageTitleSx}
            >
              Approvals
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Review pending expenses, leave, and timesheets.
            </Typography>
          </Box>
          {totalPending > 0 && (
            <Chip
              label={`${totalPending} Pending`}
              sx={{
                backgroundColor: paletteTone(theme, 'warning').bg,
                color: 'warning.main',
                fontWeight: 600,
                fontSize: '0.8125rem',
                border: `1px solid ${alpha(theme.palette.warning.main, 0.3)}`,
              }}
            />
          )}
        </Box>

        {/* Tabs for Expenses, Leave Requests, and Timesheets */}
        <Box sx={{ mb: sectionGap }}>
          <Tabs
            value={tabValue}
            onChange={(e, newValue) => {
              setTabValue(newValue)
              setSearchParams({ tab: newValue.toString() })
            }}
            sx={{
              mb: 3,
              '& .MuiTabs-indicator': {
                height: 3,
                borderRadius: '3px 3px 0 0',
                backgroundColor: 'primary.main',
              },
              '& .MuiTab-root': {
                minHeight: 64,
                px: 3,
                gap: 1.5,
              },
            }}
          >
            <Tab
              icon={<ReceiptIcon />}
              iconPosition="start"
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                    Expenses
                  </Typography>
                  {expenseApprovals.length > 0 && (
                    <Chip
                      label={expenseApprovals.length}
                      size="small"
                      sx={{
                        backgroundColor: isDark ? 'rgba(79, 122, 92, 0.2)' : 'rgba(79, 122, 92, 0.15)',
                        color: '#4F7A5C',
                        fontWeight: 600,
                        fontSize: '0.75rem',
                        height: 22,
                        border: `1px solid ${alpha('#4F7A5C', 0.3)}`,
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
                  color: '#4F7A5C',
                },
              }}
            />
            <Tab
              icon={<EventNoteIcon />}
              iconPosition="start"
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                    Leave Requests
                  </Typography>
                  {leaveApprovals.length > 0 && (
                    <Chip
                      label={leaveApprovals.length}
                      size="small"
                      sx={{
                        backgroundColor: paletteTone(theme, 'info').bg,
                        color: 'info.main',
                        fontWeight: 600,
                        fontSize: '0.75rem',
                        height: 22,
                        border: `1px solid ${alpha(theme.palette.info.main, 0.3)}`,
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
                  color: '#4A7A8C',
                },
              }}
            />
            <Tab
              icon={<TimesheetIcon />}
              iconPosition="start"
              label={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                    Timesheets
                  </Typography>
                  {timesheetApprovals.length > 0 && (
                    <Chip
                      label={timesheetApprovals.length}
                      size="small"
                      sx={{
                        backgroundColor: isDark ? 'rgba(184, 137, 74, 0.2)' : 'rgba(184, 137, 74, 0.15)',
                        color: '#B8894A',
                        fontWeight: 600,
                        fontSize: '0.75rem',
                        height: 22,
                        border: `1px solid ${alpha('#B8894A', 0.3)}`,
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
                  color: '#B8894A',
                },
              }}
            />
          </Tabs>
        </Box>
      </motion.div>

      {/* Stats Cards */}
      {!loading && renderStats()}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
          <CircularProgress />
        </Box>
      ) : currentApprovals.length === 0 ? (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
        >
          <Card
            sx={{
              ...getSurfaceStyles(isDark, 'card'),
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
                {tabValue === 0 
                  ? 'No pending expense approvals' 
                  : tabValue === 1 
                    ? 'No pending leave request approvals'
                    : 'No pending timesheet approvals'}
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
          {/* Expense Approvals Table */}
          {tabValue === 0 && (
            <TableContainer
              component={Paper}
              sx={{
                ...getSurfaceStyles(isDark, 'card'),
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
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Description
                    </TableCell>
                    <TableCell 
                      align="right"
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
                      Amount
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
                      Date
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
                      Category
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
                      Submitted By
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
                      Actions
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {expenseApprovals.map((approval) => {
                    const expense = approval.expense || {}
                    return (
                      <TableRow
                        key={approval.id}
                        hover
                        sx={{
                          '&:hover': {
                            backgroundColor: isDark 
                              ? 'rgba(255, 255, 255, 0.05)'
                              : 'rgba(0, 0, 0, 0.02)',
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
                              fontWeight: 500,
                              fontSize: '0.9375rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            {expense.description}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" sx={{ py: 2, px: 3 }}>
                          <Typography 
                            variant="body2" 
                            sx={{ 
                              fontWeight: 600,
                              fontSize: '0.9375rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            ${expense.amount?.toFixed(2)}
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
                            {expense.expenseDate
                              ? format(new Date(expense.expenseDate), 'MMM dd, yyyy')
                              : '-'}
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
                            {expense.category?.replace('_', ' ') || 'OTHER'}
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
                            {expense.userName || '-'}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'center' }}>
                            <Button
                              size="small"
                              variant="contained"
                              color="success"
                              startIcon={<CheckCircleIcon />}
                              onClick={() =>
                                setActionDialog({ open: true, item: expense, type: 'expense', action: 'approve' })
                              }
                              sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                            >
                              Approve
                            </Button>
                            <Button
                              size="small"
                              variant="contained"
                              color="error"
                              startIcon={<CancelIcon />}
                              onClick={() =>
                                setActionDialog({ open: true, item: expense, type: 'expense', action: 'reject' })
                              }
                              sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                            >
                              Reject
                            </Button>
                          </Box>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          
          {/* Leave Request Approvals Table */}
          {tabValue === 1 && (
            <TableContainer
              component={Paper}
              sx={{
                ...getSurfaceStyles(isDark, 'card'),
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
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Employee
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
                      Leave Type
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
                      Start Date
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
                      End Date
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
                      Days
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
                      Actions
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {leaveApprovals.map((leave) => (
                    <TableRow
                      key={leave.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark 
                            ? 'rgba(255, 255, 255, 0.05)'
                            : 'rgba(0, 0, 0, 0.02)',
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
                            fontWeight: 500,
                            fontSize: '0.9375rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          {leave.userName}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={leave.leaveType?.replace('_', ' ') || 'N/A'}
                          size="small"
                          sx={{
                            backgroundColor: paletteTone(theme, 'info').bg,
                            color: 'info.main',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            border: `1px solid ${alpha(theme.palette.info.main, 0.3)}`,
                          }}
                        />
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          {leave.startDate ? format(new Date(leave.startDate), 'MMM dd, yyyy') : '-'}
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
                          {leave.endDate ? format(new Date(leave.endDate), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                            fontWeight: 600,
                          }}
                        >
                          {leave.numberOfDays} {leave.numberOfDays === 1 ? 'day' : 'days'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'center' }}>
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<CheckCircleIcon />}
                            onClick={() =>
                              setActionDialog({ open: true, item: leave, type: 'leave', action: 'approve' })
                            }
                            sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                          >
                            Approve
                          </Button>
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<CancelIcon />}
                            onClick={() =>
                              setActionDialog({ open: true, item: leave, type: 'leave', action: 'reject' })
                            }
                            sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                          >
                            Reject
                          </Button>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          
          {/* Timesheet Approvals Table */}
          {tabValue === 2 && (
            <TableContainer
              component={Paper}
              sx={{
                ...getSurfaceStyles(isDark, 'card'),
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
                        opacity: 0.7,
                        borderBottom: isDark 
                          ? '1px solid rgba(255, 255, 255, 0.1)'
                          : '1px solid rgba(0, 0, 0, 0.08)',
                      }}
                    >
                      Employee
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
                      Date
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
                      Project
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
                      Hours
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
                      Actions
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {timesheetApprovals.map((timesheet) => (
                    <TableRow
                      key={timesheet.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark 
                            ? 'rgba(255, 255, 255, 0.05)'
                            : 'rgba(0, 0, 0, 0.02)',
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
                            fontWeight: 500,
                            fontSize: '0.9375rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          {timesheet.userName}
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
                          {timesheet.date ? format(new Date(timesheet.date), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={timesheet.projectCode || 'N/A'}
                          size="small"
                          sx={{
                            backgroundColor: isDark ? 'rgba(184, 137, 74, 0.2)' : 'rgba(184, 137, 74, 0.15)',
                            color: '#B8894A',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            border: `1px solid ${alpha('#B8894A', 0.3)}`,
                          }}
                        />
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                            fontWeight: 600,
                          }}
                        >
                          {timesheet.hours}h
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3, maxWidth: 200 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {timesheet.description || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'center' }}>
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<CheckCircleIcon />}
                            onClick={() =>
                              setActionDialog({ open: true, item: timesheet, type: 'timesheet', action: 'approve' })
                            }
                            sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                          >
                            Approve
                          </Button>
                          <Button
                            size="small"
                            variant="contained"
                            startIcon={<CancelIcon />}
                            onClick={() =>
                              setActionDialog({ open: true, item: timesheet, type: 'timesheet', action: 'reject' })
                            }
                            sx={{
                                px: 2,
                                py: 0.75,
                                fontWeight: 600,
                                fontSize: '0.8125rem',
                                textTransform: 'none',
                              }}
                          >
                            Reject
                          </Button>
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
          </motion.div>
        )}

      <Dialog
        open={actionDialog.open}
        onClose={() => setActionDialog({ open: false, item: null, type: null, action: null })}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            ...getSurfaceStyles(isDark, 'card'),
            borderRadius: 2,
          },
        }}
      >
        <DialogTitle sx={{ fontWeight: 700, letterSpacing: '-0.01em', px: 3.5, pt: 3.5, pb: 2, color: 'text.primary', fontSize: '1.25rem' }}>
          {actionDialog.action === 'approve' 
            ? `Approve ${actionDialog.type === 'expense' ? 'Expense' : actionDialog.type === 'leave' ? 'Leave Request' : 'Timesheet'}` 
            : `Reject ${actionDialog.type === 'expense' ? 'Expense' : actionDialog.type === 'leave' ? 'Leave Request' : 'Timesheet'}`}
        </DialogTitle>
        <DialogContent sx={{ px: 3.5, pt: 2 }}>
          {actionDialog.item && (
            <Box sx={{ mb: 3.5 }}>
              {actionDialog.type === 'expense' ? (
                <>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Description:</strong> {actionDialog.item.description}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Amount:</strong> ${actionDialog.item.amount?.toFixed(2)}
                  </Typography>
                </>
              ) : actionDialog.type === 'leave' ? (
                <>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Employee:</strong> {actionDialog.item.userName}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Leave Type:</strong> {actionDialog.item.leaveType?.replace('_', ' ')}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Duration:</strong> {actionDialog.item.numberOfDays} {actionDialog.item.numberOfDays === 1 ? 'day' : 'days'} 
                    ({format(new Date(actionDialog.item.startDate), 'MMM dd')} - {format(new Date(actionDialog.item.endDate), 'MMM dd, yyyy')})
                  </Typography>
                </>
              ) : (
                <>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Employee:</strong> {actionDialog.item.userName}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Date:</strong> {actionDialog.item.date ? format(new Date(actionDialog.item.date), 'MMM dd, yyyy') : '-'}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      mb: 1.5,
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Project:</strong> {actionDialog.item.projectCode}
                  </Typography>
                  <Typography 
                    variant="body2" 
                    sx={{ 
                      fontSize: '0.9375rem',
                      letterSpacing: '-0.01em',
                      color: 'text.primary',
                      fontWeight: 500,
                    }}
                  >
                    <strong>Hours:</strong> {actionDialog.item.hours}h
                  </Typography>
                </>
              )}
            </Box>
          )}
          <TextField
            fullWidth
            multiline
            rows={4}
            label={actionDialog.action === 'approve' ? 'Comments (Optional)' : 'Rejection Comments *'}
            value={comments}
            onChange={(e) => setComments(e.target.value)}
            required={actionDialog.action === 'reject'}
            sx={{
              '& .MuiInputLabel-root': {
                color: 'text.primary',
                fontWeight: 600,
                '&.MuiInputLabel-shrink': {
                  backgroundColor: isDark ? 'rgba(28, 28, 30, 0.25)' : 'rgba(255, 255, 255, 0.3)',
                  padding: '0 8px',
                  borderRadius: 1,
                },
              },
              '& .MuiOutlinedInput-root': {
                borderRadius: 2,
                backgroundColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.05)',
                '& fieldset': {
                  borderColor: isDark ? 'rgba(255, 255, 255, 0.3)' : 'rgba(0, 0, 0, 0.2)',
                },
                '&:hover fieldset': {
                  borderColor: isDark ? 'rgba(255, 255, 255, 0.4)' : 'rgba(0, 0, 0, 0.3)',
                },
                '&.Mui-focused fieldset': {
                  borderColor: isDark ? 'rgba(255, 255, 255, 0.6)' : 'rgba(0, 0, 0, 0.5)',
                  borderWidth: '1.5px',
                },
                '& .MuiInputBase-input': {
                  color: 'text.primary',
                  fontWeight: 400,
                },
              },
            }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3.5, py: 2.5, gap: 2 }}>
          <Button
            onClick={() => {
              setActionDialog({ open: false, item: null, type: null, action: null })
              setComments('')
            }}
            disabled={processing}
            sx={{ borderRadius: 2, px: 3.5, py: 1.25 }}
          >
            Cancel
          </Button>
          <Button
            onClick={actionDialog.action === 'approve' ? handleApprove : handleReject}
            variant="contained"
            disabled={processing}
            color={actionDialog.action === 'approve' ? 'success' : 'error'}
            sx={{ px: 3, py: 1.25 }}
          >
            {processing ? (
              <CircularProgress size={24} />
            ) : actionDialog.action === 'approve' ? (
              'Approve'
            ) : (
              'Reject'
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
