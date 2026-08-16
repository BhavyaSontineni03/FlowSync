import React, { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  useTheme,
  alpha,
} from '@mui/material'
import {
  Receipt as ReceiptIcon,
  EventNote as EventNoteIcon,
  AccessTime as AccessTimeIcon,
  AccountBalance as AccountBalanceIcon,
  Folder as FolderIcon,
  ArrowForward as ArrowForwardIcon,
  Analytics as AnalyticsIcon,
  Approval as ApprovalIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { expenseService } from '../services/expenseService'
import { leaveRequestService } from '../services/leaveRequestService'
import { analyticsService } from '../services/analyticsService'
import { financeService } from '../services/financeService'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { canApprove, canAccessAnalytics } from '../utils/roleUtils'
import { StatCardSkeleton } from '../components/SkeletonLoader'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { getModulesForRole } from '../config/modules'
import {
  pageSx,
  pageTitleSx,
  pageSubtitleSx,
  sectionGap,
  stackGapLoose,
  cardPadComfortableSx,
} from '../utils/uiTokens'

const MODULE_ICONS = {
  expenses: ReceiptIcon,
  'leave-requests': EventNoteIcon,
  timesheets: AccessTimeIcon,
  payroll: AccountBalanceIcon,
  projects: FolderIcon,
}

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.06, delayChildren: 0.05 },
  },
}

const itemVariants = {
  hidden: { opacity: 0, y: 10 },
  visible: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.28, ease: [0.22, 1, 0.36, 1] },
  },
}

const ModuleCard = React.memo(({ module, stats, loading, userRole }) => {
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const navigate = useNavigate()
  const IconComponent = MODULE_ICONS[module.id] || ReceiptIcon

  const pendingLabel =
    userRole === 'FINANCE' && module.id === 'payroll' ? 'Processed' : 'Pending'
  const approvedLabel =
    userRole === 'FINANCE' && (module.id === 'expenses' || module.id === 'payroll')
      ? 'Paid'
      : 'Approved'

  const pendingValue =
    module.id === 'expenses'
      ? `$${(stats?.pending || 0).toFixed(2)}`
      : userRole === 'FINANCE' && module.id === 'payroll'
        ? stats?.processed || 0
        : stats?.pending || 0

  const approvedValue =
    module.id === 'expenses'
      ? `$${((userRole === 'FINANCE' ? stats?.paid : stats?.approved) || 0).toFixed(2)}`
      : userRole === 'FINANCE' && module.id === 'payroll'
        ? stats?.paid || 0
        : stats?.approved || 0

  return (
    <motion.div variants={itemVariants}>
      <Card
        sx={{
          height: '100%',
          minHeight: 280,
          cursor: 'pointer',
          display: 'flex',
          flexDirection: 'column',
          ...getSurfaceStyles(isDark, 'card'),
          '&:hover': {
            borderColor: alpha(module.color, 0.45),
            transform: 'translateY(-2px)',
          },
        }}
        onClick={() => navigate(module.route)}
      >
        <CardContent
          sx={{
            ...cardPadComfortableSx,
            p: { xs: 2.5, sm: 3 },
            display: 'flex',
            flexDirection: 'column',
            flex: 1,
            gap: stackGapLoose,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
            <Box
              sx={{
                width: 48,
                height: 48,
                borderRadius: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: alpha(module.color, isDark ? 0.2 : 0.12),
                flexShrink: 0,
              }}
            >
              <IconComponent sx={{ color: module.color, fontSize: 24 }} />
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography
                variant="h5"
                sx={{
                  fontWeight: 600,
                  fontSize: '1.2rem',
                  mb: 0.5,
                  color: 'text.primary',
                }}
              >
                {module.name}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.45 }}>
                {module.description}
              </Typography>
            </Box>
          </Box>

          {loading ? (
            <Box sx={{ display: 'flex', gap: 1.5 }}>
              <Box
                sx={{
                  flex: 1,
                  height: 64,
                  borderRadius: 1,
                  backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.04)',
                }}
              />
              <Box
                sx={{
                  flex: 1,
                  height: 64,
                  borderRadius: 1,
                  backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.04)',
                }}
              />
            </Box>
          ) : stats ? (
            <Grid container spacing={1.5}>
              <Grid item xs={6}>
                <Box
                  sx={{
                    p: 1.75,
                    borderRadius: 1,
                    backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.03)',
                  }}
                >
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600 }}
                  >
                    {pendingLabel}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700, mt: 0.5, color: module.color }}>
                    {pendingValue}
                  </Typography>
                </Box>
              </Grid>
              <Grid item xs={6}>
                <Box
                  sx={{
                    p: 1.75,
                    borderRadius: 1,
                    backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.03)',
                  }}
                >
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    sx={{ textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600 }}
                  >
                    {approvedLabel}
                  </Typography>
                  <Typography
                    variant="h6"
                    sx={{ fontWeight: 700, mt: 0.5, color: 'success.main' }}
                  >
                    {approvedValue}
                  </Typography>
                </Box>
              </Grid>
            </Grid>
          ) : null}

          <Box sx={{ mt: 'auto', display: 'flex', gap: 1 }}>
            <Button
              variant="contained"
              color="primary"
              fullWidth
              endIcon={<ArrowForwardIcon />}
              onClick={(e) => {
                e.stopPropagation()
                navigate(module.route)
              }}
            >
              Open
            </Button>
            {canAccessAnalytics({ role: userRole }) && module.analyticsRoute && (
              <Button
                variant="outlined"
                color="secondary"
                onClick={(e) => {
                  e.stopPropagation()
                  navigate(module.analyticsRoute)
                }}
                sx={{ minWidth: 48, px: 1.5 }}
                aria-label={`${module.name} analytics`}
              >
                <AnalyticsIcon sx={{ fontSize: 20 }} />
              </Button>
            )}
          </Box>
        </CardContent>
      </Card>
    </motion.div>
  )
}, (prev, next) =>
  prev.module.id === next.module.id &&
  prev.loading === next.loading &&
  prev.userRole === next.userRole &&
  JSON.stringify(prev.stats) === JSON.stringify(next.stats)
)

export default function Dashboard() {
  const [moduleStats, setModuleStats] = useState({})
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  const accessibleModules = useMemo(() => getModulesForRole(user?.role), [user?.role])

  useEffect(() => {
    fetchModuleStats()
  }, [user?.role])

  const fetchModuleStats = async () => {
    try {
      setLoading(true)

      if (user?.role === 'FINANCE') {
        try {
          const [approvedRes, paidRes] = await Promise.all([
            financeService.getApprovedExpenses(0, 1000).catch(() => ({ content: [], totalElements: 0 })),
            financeService.getPaidExpenses(0, 1000).catch(() => ({ content: [], totalElements: 0 })),
          ])
          const approvedExpenses = approvedRes.content || []
          const paidExpenses = paidRes.content || []

          const expenseStats = {
            pending: approvedExpenses.reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
            paid: paidExpenses.reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
            total:
              approvedExpenses.reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0) +
              paidExpenses.reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
          }

          const leaveRes = await leaveRequestService.getLeaveRequests(0, 1000)
          const leaves = leaveRes.content || []

          const leaveStats = {
            pending: leaves.filter((l) => l.status === 'PENDING').length,
            approved: leaves.filter((l) => l.status === 'APPROVED').length,
            total: leaves.length,
            rejected: leaves.filter((l) => l.status === 'REJECTED').length,
          }

          try {
            const [processedRes, paidPayrollRes] = await Promise.all([
              financeService.getProcessedPayrolls(0, 1000).catch(() => ({ content: [], totalElements: 0 })),
              financeService.getPaidPayrolls(0, 1000).catch(() => ({ content: [], totalElements: 0 })),
            ])
            const processedPayrolls = processedRes.content || []
            const paidPayrolls = paidPayrollRes.content || []

            setModuleStats({
              expenses: expenseStats,
              'leave-requests': leaveStats,
              payroll: {
                processed: processedPayrolls.length,
                paid: paidPayrolls.length,
                total: processedPayrolls.length + paidPayrolls.length,
              },
            })
          } catch (e) {
            setModuleStats({
              expenses: expenseStats,
              'leave-requests': leaveStats,
            })
          }
        } catch (e) {
          enqueueSnackbar('Error loading Finance stats', { variant: 'error' })
          setModuleStats({})
        }
        return
      }

      const expensesRes = await expenseService.getAll({ page: 0, size: 1000 })
      const expenses = expensesRes.content || []

      let expenseStats = {
        total: expenses.reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
        pending: expenses
          .filter((e) => e.status === 'PENDING' || e.status === 'SUBMITTED')
          .reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
        approved: expenses
          .filter((e) => e.status === 'APPROVED' || e.status === 'PAID')
          .reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
        rejected: expenses
          .filter((e) => e.status === 'REJECTED')
          .reduce((sum, exp) => sum + (parseFloat(exp.amount) || 0), 0),
      }

      if (canAccessAnalytics(user)) {
        try {
          const analyticsData = await analyticsService.getAnalytics()
          if (analyticsData) {
            expenseStats = {
              total: analyticsData.totalExpenses || 0,
              pending: analyticsData.pendingExpenses || 0,
              approved: analyticsData.approvedExpenses || 0,
              rejected: analyticsData.rejectedExpenses || 0,
            }
          }
        } catch (e) {
          // keep calculated stats
        }
      }

      const leaveRes = await leaveRequestService.getLeaveRequests(0, 1000)
      const leaves = leaveRes.content || []

      setModuleStats({
        expenses: expenseStats,
        'leave-requests': {
          total: leaves.length,
          pending: leaves.filter((l) => l.status === 'PENDING').length,
          approved: leaves.filter((l) => l.status === 'APPROVED').length,
          rejected: leaves.filter((l) => l.status === 'REJECTED').length,
        },
      })
    } catch (error) {
      enqueueSnackbar('Error loading dashboard data', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return (
      <Box sx={pageSx}>
        <Grid container spacing={stackGapLoose}>
          {[1, 2].map((i) => (
            <Grid item xs={12} md={6} key={i}>
              <StatCardSkeleton />
            </Grid>
          ))}
        </Grid>
      </Box>
    )
  }

  const firstName = user?.firstName || 'there'

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={{ mb: sectionGap }}>
          <Typography
            variant="h4"
            component="h1"
            sx={pageTitleSx}
          >
            Hello, {firstName}
          </Typography>
          <Typography variant="body1" sx={pageSubtitleSx}>
            Jump into the workflows available for your role.
          </Typography>
        </Box>
      </motion.div>

      <motion.div variants={containerVariants} initial="hidden" animate="visible">
        <Grid container spacing={stackGapLoose}>
          {accessibleModules.map((module) => (
            <Grid item xs={12} md={6} key={module.id}>
              <ModuleCard
                module={module}
                stats={moduleStats[module.id]}
                loading={false}
                userRole={user?.role}
              />
            </Grid>
          ))}
        </Grid>

        {canApprove(user) && (
          <motion.div variants={itemVariants}>
            <Box
              sx={{
                mt: sectionGap,
                py: 2.5,
                px: { xs: 2.5, sm: 3 },
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                flexWrap: 'wrap',
                gap: stackGapLoose,
                borderRadius: 2,
                ...getSurfaceStyles(isDark, 'card'),
              }}
            >
              <Box>
                <Typography variant="h6" sx={{ fontWeight: 600, fontSize: '1.05rem', mb: 0.25 }}>
                  Manager queue
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Review pending submissions or open analytics.
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', gap: 1.25, flexWrap: 'wrap' }}>
                <Button
                  variant="outlined"
                  color="secondary"
                  startIcon={<ApprovalIcon />}
                  onClick={() => navigate('/approvals')}
                >
                  Approvals
                </Button>
                {canAccessAnalytics(user) && (
                  <Button
                    variant="contained"
                    color="primary"
                    startIcon={<AnalyticsIcon />}
                    onClick={() => navigate('/analytics')}
                  >
                    Analytics
                  </Button>
                )}
              </Box>
            </Box>
          </motion.div>
        )}
      </motion.div>
    </Box>
  )
}
