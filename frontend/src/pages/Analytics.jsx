import React, { useState, useEffect, useMemo, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Button,
  CircularProgress,
  useTheme,
  useMediaQuery,
  Tabs,
  Tab,
  Chip,
  alpha,
} from '@mui/material'
import {
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  LineChart,
  Line,
} from 'recharts'
import { motion } from 'framer-motion'
import {
  Receipt as ReceiptIcon,
  EventNote as EventNoteIcon,
  Download as DownloadIcon,
} from '@mui/icons-material'
import { analyticsService } from '../services/analyticsService'
import { exportService } from '../services/exportService'
import { useSnackbar } from 'notistack'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { MODULES, canViewModuleAnalytics } from '../config/modules'
import { useAuth } from '../contexts/AuthContext'
import { canAccessExpenseAnalytics, canAccessLeaveAnalytics } from '../utils/roleUtils'
import { chartColors, pageTitleSx, pageSubtitleSx, quietTabsSx, pageSx, sectionGap, softRadius } from '../utils/uiTokens'

export default function Analytics() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  
  // Determine initial module based on user role
  const getInitialModule = () => {
    const moduleParam = searchParams.get('module')
    if (moduleParam) return moduleParam
    
    // Finance defaults to expenses (finance activity)
    if (user?.role === 'FINANCE') return 'expenses'
    
    // Manager defaults to expenses (for approvals analytics)
    if (user?.role === 'MANAGER') return 'expenses'
    
    return 'expenses'
  }
  
  const initialModule = getInitialModule()
  const [activeModule, setActiveModule] = useState(initialModule)
  const [analytics, setAnalytics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [startDate, setStartDate] = useState(null)
  const [endDate, setEndDate] = useState(null)
  const [exporting, setExporting] = useState(false)
  const [error, setError] = useState(null)
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
  const chartHeight = isMobile ? 250 : 300

  // Safely get current module with fallback
  const currentModule = useMemo(() => {
    try {
      const moduleKey = Object.keys(MODULES).find(key => MODULES[key]?.id === activeModule)
      return moduleKey ? MODULES[moduleKey] : MODULES.EXPENSES
    } catch (err) {
      console.error('Error getting module:', err)
      return MODULES.EXPENSES
    }
  }, [activeModule])
  
  const isExpenseModule = activeModule === 'expenses'

  // Memoize fetchAnalytics to avoid infinite loops
  const fetchAnalytics = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await analyticsService.getAnalytics(startDate, endDate, activeModule)
      if (data) {
        setAnalytics(data)
      } else {
        throw new Error('Invalid response from server')
      }
    } catch (err) {
      console.error('Analytics error:', err)
      const errorMessage = err.response?.data?.message || 
                          err.message || 
                          'Error loading analytics. Please check your permissions and try again.'
      setError(errorMessage)
      enqueueSnackbar(errorMessage, { variant: 'error' })
      setAnalytics(null)
    } finally {
      setLoading(false)
    }
  }, [startDate, endDate, activeModule, enqueueSnackbar])

  useEffect(() => {
    fetchAnalytics()
  }, [fetchAnalytics])

  const handleModuleChange = (event, newValue) => {
    setActiveModule(newValue)
    setSearchParams({ module: newValue })
    setAnalytics(null) // Clear previous analytics
    setLoading(true) // Show loading when switching modules
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      if (isExpenseModule) {
        await exportService.exportExpenses(startDate, endDate)
      } else {
        // Leave export can be added later
        enqueueSnackbar('Export feature coming soon for leave requests', { variant: 'info' })
      }
      enqueueSnackbar('Export started', { variant: 'success' })
    } catch (error) {
      enqueueSnackbar('Error exporting data', { variant: 'error' })
    } finally {
      setExporting(false)
    }
  }

  // Prepare data based on module type - memoized for performance
  // MUST be called before any conditional returns (Rules of Hooks)
  const { categoryData, monthlyData, statusData, stats } = useMemo(() => {
    if (!analytics) {
      return { categoryData: [], monthlyData: [], statusData: [], stats: {} }
    }

    if (isExpenseModule) {
      // Expense Analytics
      const categoryData = analytics.expensesByCategory?.map((item) => ({
        name: item.category.replace('_', ' '),
        value: parseFloat(item.amount),
      })) || []

      const monthlyData = analytics.expensesByMonth?.map((item) => ({
        month: item.month,
        amount: parseFloat(item.amount),
      })) || []

      const statusData = Object.entries(analytics.expensesByStatus || {}).map(([key, value]) => ({
        name: key,
        value: parseFloat(value),
      }))

      const stats = {
        total: analytics.totalExpenses,
        pending: analytics.pendingExpenses,
        approved: analytics.approvedExpenses,
        rejected: analytics.rejectedExpenses,
        totalCount: analytics.totalCount,
        pendingCount: analytics.pendingCount,
        approvedCount: analytics.approvedCount,
        rejectedCount: analytics.rejectedCount,
      }

      return { categoryData, monthlyData, statusData, stats }
    } else {
      // Leave Request Analytics
      const categoryData = analytics.leavesByType?.map((item) => ({
        name: item.leaveType?.replace(/_/g, ' ') || 'Unknown',
        value: item.count || 0,
        days: item.totalDays || 0,
      })) || []

      const monthlyData = analytics.leavesByMonth?.map((item) => ({
        month: item.month,
        count: item.count,
        days: item.totalDays,
      })) || []

      const statusData = Object.entries(analytics.leavesByStatus || {}).map(([key, value]) => ({
        name: key,
        value: value,
      }))

      const stats = {
        total: analytics.totalLeaves,
        pending: analytics.pendingLeaves,
        approved: analytics.approvedLeaves,
        rejected: analytics.rejectedLeaves,
        cancelled: analytics.cancelledLeaves,
      }

      return { categoryData, monthlyData, statusData, stats }
    }
  }, [analytics, isExpenseModule])

  const seriesColors = chartColors(theme)
  const tooltipStyle = {
    borderRadius: 10,
    backgroundColor: theme.palette.background.paper,
    border: `1px solid ${theme.palette.divider}`,
    padding: '8px 12px',
    boxShadow: 'none',
  }

  const StatCard = React.memo(({ title, value, subtitle, color, delay, isExpenseModule: isExpense }) => {
    const cardTheme = useTheme()
    const cardDark = cardTheme.palette.mode === 'dark'

    return (
      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay, duration: 0.24, ease: [0.22, 1, 0.36, 1] }}
      >
        <Card
          sx={{
            ...getSurfaceStyles(cardDark, 'stat'),
            transition: 'border-color 0.2s ease, transform 0.2s ease',
            '&:hover': {
              transform: 'translateY(-2px)',
              borderColor: alpha(color, 0.35),
            },
          }}
        >
          <CardContent sx={{ p: 2.75, textAlign: 'center' }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                mb: 1,
                display: 'block',
                fontWeight: 600,
                letterSpacing: '0.04em',
                textTransform: 'uppercase',
              }}
            >
              {title}
            </Typography>
            <Typography
              variant="h4"
              sx={{
                fontWeight: 600,
                fontSize: '1.75rem',
                letterSpacing: '-0.02em',
                mb: 0.5,
                color,
              }}
            >
              {isExpense
                ? `$${typeof value === 'number' ? value.toFixed(2) : (parseFloat(value) || 0).toFixed(2)}`
                : value || 0}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary">
                {subtitle}
              </Typography>
            )}
          </CardContent>
        </Card>
      </motion.div>
    )
  })

  // Now we can do conditional returns AFTER all hooks
  if (loading) {
    return (
      <Box sx={{ ...pageSx, display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
        <CircularProgress />
      </Box>
    )
  }

  if (error && !analytics) {
    return (
      <Box sx={pageSx}>
        <Card
          sx={{
            ...getSurfaceStyles(isDark, 'navigation'),
          }}
        >
          <CardContent sx={{ p: 4, textAlign: 'center' }}>
            <Typography sx={{ opacity: 0.6, mb: 2 }}>{error}</Typography>
            <Button 
              variant="outlined" 
              onClick={() => fetchAnalytics()}
              sx={{ borderRadius: softRadius }}
            >
              Retry
            </Button>
          </CardContent>
        </Card>
      </Box>
    )
  }

  if (!analytics || !stats || Object.keys(stats).length === 0) {
    return (
      <Box sx={pageSx}>
        <Card
          sx={{
            ...getSurfaceStyles(isDark, 'navigation'),
          }}
        >
          <CardContent sx={{ p: 4, textAlign: 'center' }}>
            <Typography sx={{ opacity: 0.6 }}>No analytics data available</Typography>
          </CardContent>
        </Card>
      </Box>
    )
  }

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={{ mb: sectionGap }}>
          <Typography variant="h4" component="h1" sx={pageTitleSx}>
            Analytics
          </Typography>
          <Typography variant="body2" sx={{ ...pageSubtitleSx, mb: sectionGap }}>
            Spend and leave trends for the ranges you care about.
          </Typography>

          {/* Module Tabs - Role-based visibility */}
          {(canAccessExpenseAnalytics(user) || canAccessLeaveAnalytics(user)) && (
            <Tabs
              value={activeModule}
              onChange={handleModuleChange}
              sx={quietTabsSx(theme)}
            >
              {canAccessExpenseAnalytics(user) && (
                <Tab
                  icon={<ReceiptIcon />}
                  iconPosition="start"
                  label={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                        Expense Analytics
                      </Typography>
                    </Box>
                  }
                  value="expenses"
                  sx={{
                    '&.Mui-selected': {
                      color: 'primary.main',
                    },
                  }}
                />
              )}
              {canAccessLeaveAnalytics(user) && (
                <Tab
                  icon={<EventNoteIcon />}
                  iconPosition="start"
                  label={
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Typography sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                        Leave Request Analytics
                      </Typography>
                    </Box>
                  }
                  value="leave-requests"
                  sx={{
                    '&.Mui-selected': {
                      color: 'info.main',
                    },
                  }}
                />
              )}
            </Tabs>
          )}
          
          {/* Single module header if user can only access one module */}
          {canAccessExpenseAnalytics(user) && !canAccessLeaveAnalytics(user) && (
            <Box sx={{ mb: 4 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <ReceiptIcon sx={{ fontSize: 32, color: 'primary.main' }} />
                <Typography
                  variant="h5"
                  sx={{
                    fontWeight: 700,
                    fontSize: '1.5rem',
                    letterSpacing: '-0.02em',
                    color: 'text.primary',
                  }}
                >
                  Expense Analytics
                </Typography>
              </Box>
            </Box>
          )}
          
          {canAccessLeaveAnalytics(user) && !canAccessExpenseAnalytics(user) && (
            <Box sx={{ mb: 4 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <EventNoteIcon sx={{ fontSize: 32, color: 'info.main' }} />
                <Typography
                  variant="h5"
                  sx={{
                    fontWeight: 700,
                    fontSize: '1.5rem',
                    letterSpacing: '-0.02em',
                    color: 'text.primary',
                  }}
                >
                  Leave Request Analytics
                </Typography>
              </Box>
            </Box>
          )}

          {/* Date Range and Export */}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: sectionGap, flexWrap: 'wrap', gap: 2 }}>
            <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
              <LocalizationProvider dateAdapter={AdapterDateFns}>
                <DatePicker
                  label="Start Date"
                  value={startDate}
                  onChange={setStartDate}
                  slotProps={{ 
                    textField: { 
                      size: 'small',
                      sx: {
                        '& .MuiOutlinedInput-root': {
                          borderRadius: softRadius,
                        },
                      },
                    } 
                  }}
                />
                <DatePicker
                  label="End Date"
                  value={endDate}
                  onChange={setEndDate}
                  slotProps={{ 
                    textField: { 
                      size: 'small',
                      sx: {
                        '& .MuiOutlinedInput-root': {
                          borderRadius: softRadius,
                        },
                      },
                    } 
                  }}
                />
              </LocalizationProvider>
            </Box>
            {isExpenseModule && (
              <Button
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={handleExport}
                disabled={exporting}
                sx={{
                  borderRadius: softRadius,
                  px: 3,
                  py: 1.5,
                }}
              >
                Export CSV
              </Button>
            )}
          </Box>
        </Box>
      </motion.div>

      {/* Stats Cards */}
      <Grid container spacing={3.5} sx={{ mb: 4 }}>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title={isExpenseModule ? "Total Expenses" : "Total Requests"}
            value={stats.total}
            subtitle={isExpenseModule 
              ? `${stats.totalCount || 0} expenses`
              : `${stats.total || 0} requests`}
            color={currentModule.color}
            delay={0.1}
            isExpenseModule={isExpenseModule}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Pending"
            value={stats.pending}
            subtitle={isExpenseModule 
              ? `${stats.pendingCount || 0} expenses`
              : `${stats.pending || 0} requests`}
            color={theme.palette.warning.main}
            delay={0.2}
            isExpenseModule={isExpenseModule}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Approved"
            value={stats.approved}
            subtitle={isExpenseModule 
              ? `${stats.approvedCount || 0} expenses`
              : `${stats.approved || 0} requests`}
            color={theme.palette.success.main}
            delay={0.3}
            isExpenseModule={isExpenseModule}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <StatCard
            title="Rejected"
            value={stats.rejected}
            subtitle={isExpenseModule 
              ? `${stats.rejectedCount || 0} expenses`
              : `${stats.rejected || 0} requests`}
            color={theme.palette.error.main}
            delay={0.4}
            isExpenseModule={isExpenseModule}
          />
        </Grid>
      </Grid>

      {/* Charts */}
      <Grid container spacing={3.5}>
        {/* Category/Type Chart */}
        <Grid item xs={12} md={6}>
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4, duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
            style={{ willChange: 'transform' }}
          >
            <Card
              sx={{
                ...getSurfaceStyles(isDark, 'navigation'),
              }}
            >
              <CardContent sx={{ p: 3.5 }}>
                <Typography 
                  variant="h6" 
                  gutterBottom 
                  sx={{ 
                    fontWeight: 600,
                    mb: { xs: 2, sm: 3 },
                    fontSize: { xs: '1rem', sm: '1.125rem' },
                    letterSpacing: '-0.01em',
                    textAlign: 'center',
                  }}
                >
                  {isExpenseModule ? 'Expenses by Category' : 'Leaves by Type'}
                </Typography>
                {categoryData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={chartHeight}>
                    <PieChart>
                      <Pie
                        data={categoryData}
                        cx="50%"
                        cy="50%"
                        labelLine={false}
                        label={({ name, percent, value }) => {
                          const label = isExpenseModule
                            ? `${name} ${(percent * 100).toFixed(0)}%`
                            : `${name} (${value})`
                          return label.length > 20 ? `${name.substring(0, 15)}... ${(percent * 100).toFixed(0)}%` : label
                        }}
                        outerRadius={90}
                        fill={theme.palette.primary.main}
                        dataKey="value"
                      >
                        {categoryData.map((entry, index) => (
                          <Cell 
                            key={`cell-${index}`} 
                            fill={seriesColors[index % seriesColors.length]} 
                          />
                        ))}
                      </Pie>
                      <Tooltip 
                        contentStyle={tooltipStyle}
                        formatter={(value) => isExpenseModule ? `$${value.toFixed(2)}` : `${value} requests`}
                      />
                    </PieChart>
                  </ResponsiveContainer>
                ) : (
                  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: chartHeight }}>
                    <Typography sx={{ opacity: 0.6 }}>No data available</Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </Grid>

        {/* Status Chart */}
        <Grid item xs={12} md={6}>
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5, duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
            style={{ willChange: 'transform' }}
          >
            <Card
              sx={{
                ...getSurfaceStyles(isDark, 'navigation'),
              }}
            >
              <CardContent sx={{ p: 3.5 }}>
                <Typography 
                  variant="h6" 
                  gutterBottom 
                  sx={{ 
                    fontWeight: 600,
                    mb: { xs: 2, sm: 3 },
                    fontSize: { xs: '1rem', sm: '1.125rem' },
                    letterSpacing: '-0.01em',
                    textAlign: 'center',
                  }}
                >
                  {isExpenseModule ? 'Expenses by Status' : 'Leaves by Status'}
                </Typography>
                {statusData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={chartHeight}>
                    <BarChart data={statusData}>
                      <CartesianGrid strokeDasharray="3 3" stroke={isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'} />
                      <XAxis 
                        dataKey="name" 
                        tick={{ fill: isDark ? '#A8B5AE' : '#5A6577', fontSize: 12 }}
                      />
                      <YAxis 
                        tick={{ fill: isDark ? '#A8B5AE' : '#5A6577', fontSize: 12 }}
                      />
                      <Tooltip 
                        contentStyle={tooltipStyle}
                        formatter={(value) => isExpenseModule ? `$${value.toFixed(2)}` : `${value} requests`}
                      />
                      <Bar 
                        dataKey="value" 
                        fill={currentModule.color}
                        radius={[8, 8, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                ) : (
                  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: chartHeight }}>
                    <Typography sx={{ opacity: 0.6 }}>No data available</Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </Grid>

        {/* Monthly Trend */}
        <Grid item xs={12}>
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.6, duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
            style={{ willChange: 'transform' }}
          >
            <Card
              sx={{
                ...getSurfaceStyles(isDark, 'navigation'),
              }}
            >
              <CardContent sx={{ p: 3.5 }}>
                <Typography 
                  variant="h6" 
                  gutterBottom 
                  sx={{ 
                    fontWeight: 600,
                    mb: { xs: 2, sm: 3 },
                    fontSize: { xs: '1rem', sm: '1.125rem' },
                    letterSpacing: '-0.01em',
                    textAlign: 'center',
                  }}
                >
                  {isExpenseModule ? 'Monthly Expense Trend' : 'Monthly Leave Request Trend'}
                </Typography>
                {monthlyData.length > 0 ? (
                  <ResponsiveContainer width="100%" height={chartHeight}>
                    <LineChart data={monthlyData}>
                      <CartesianGrid strokeDasharray="3 3" stroke={isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'} />
                      <XAxis 
                        dataKey="month" 
                        tick={{ fill: isDark ? '#A8B5AE' : '#5A6577', fontSize: 12 }}
                      />
                      <YAxis 
                        tick={{ fill: isDark ? '#A8B5AE' : '#5A6577', fontSize: 12 }}
                      />
                      <Tooltip 
                        contentStyle={tooltipStyle}
                        formatter={(value) => isExpenseModule ? `$${value.toFixed(2)}` : `${value} requests`}
                      />
                      <Legend />
                      <Line 
                        type="monotone" 
                        dataKey={isExpenseModule ? "amount" : "count"} 
                        stroke={currentModule.color} 
                        strokeWidth={3}
                        dot={{ fill: currentModule.color, r: 4 }}
                        activeDot={{ r: 6 }}
                        name={isExpenseModule ? "Amount" : "Count"}
                      />
                      {!isExpenseModule && (
                        <Line 
                          type="monotone" 
                          dataKey="days" 
                          stroke={theme.palette.primary.main} 
                          strokeWidth={3}
                          dot={{ fill: theme.palette.primary.main, r: 4 }}
                          activeDot={{ r: 6 }}
                          name="Days"
                        />
                      )}
                    </LineChart>
                  </ResponsiveContainer>
                ) : (
                  <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: chartHeight }}>
                    <Typography sx={{ opacity: 0.6 }}>No data available</Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
          </motion.div>
        </Grid>
      </Grid>
    </Box>
  )
}
