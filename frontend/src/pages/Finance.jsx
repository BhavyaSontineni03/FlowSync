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
  Chip,
  Pagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  CircularProgress,
  useTheme,
  alpha,
  Checkbox,
} from '@mui/material'
import {
  Payment as PaymentIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon,
  AccountBalance as AccountBalanceIcon,
  Calculate as CalculateIcon,
  PlayArrow as PlayArrowIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { financeService } from '../services/financeService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { toCssColor, pageSx, pageHeaderSx, pageTitleSx, pageSubtitleSx, softRadius } from '../utils/uiTokens'

const expenseStatusColors = {
  APPROVED: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  PAID: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
}

const payrollStatusColors = {
  DRAFT: { color: 'warning.main', bg: 'rgba(184, 137, 74, 0.15)' },
  PROCESSED: { color: 'info.main', bg: 'rgba(74, 122, 140, 0.12)' },
  PAID: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  CANCELLED: { color: 'error.main', bg: 'rgba(196, 91, 106, 0.15)' },
}

export default function Finance() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialSection = parseInt(searchParams.get('section') || '0', 10)
  // Section: 0 = Expenses, 1 = Payroll
  const [section, setSection] = useState(initialSection)
  
  // Expenses state
  const [approvedExpenses, setApprovedExpenses] = useState([])
  const [paidExpenses, setPaidExpenses] = useState([])
  const [expenseTabValue, setExpenseTabValue] = useState(0) // 0 = Approved, 1 = Paid
  const [selectedExpenses, setSelectedExpenses] = useState([])
  
  // Payroll state
  const [processedPayrolls, setProcessedPayrolls] = useState([])
  const [paidPayrolls, setPaidPayrolls] = useState([])
  const [allPayrolls, setAllPayrolls] = useState([])
  const [payrollTabValue, setPayrollTabValue] = useState(0) // 0 = Processed, 1 = Paid, 2 = All
  const [selectedPayrolls, setSelectedPayrolls] = useState([])
  const [generateDialog, setGenerateDialog] = useState({ open: false })
  const [generateData, setGenerateData] = useState({
    month: new Date().getMonth() + 1,
    year: new Date().getFullYear(),
  })
  
  // Common state
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [processing, setProcessing] = useState(false)
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null })
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  // Update section when URL parameter changes
  useEffect(() => {
    const sectionParam = parseInt(searchParams.get('section') || '0', 10)
    if (sectionParam !== section && sectionParam >= 0 && sectionParam <= 1) {
      setSection(sectionParam)
      setPage(0) // Reset page when section changes
    }
  }, [searchParams])

  useEffect(() => {
    fetchData()
  }, [page, section, expenseTabValue, payrollTabValue])

  const fetchData = async () => {
    setLoading(true)
    try {
      if (section === 0) {
        // Expenses section
        if (expenseTabValue === 0) {
          const response = await financeService.getApprovedExpenses(page, 20)
          setApprovedExpenses(response.content || [])
          setTotalPages(response.totalPages || 0)
        } else {
          const response = await financeService.getPaidExpenses(page, 20)
          setPaidExpenses(response.content || [])
          setTotalPages(response.totalPages || 0)
        }
      } else {
        // Payroll section
        if (payrollTabValue === 0) {
          const response = await financeService.getProcessedPayrolls(page, 20)
          setProcessedPayrolls(response.content || [])
          setTotalPages(response.totalPages || 0)
        } else if (payrollTabValue === 1) {
          const response = await financeService.getPaidPayrolls(page, 20)
          setPaidPayrolls(response.content || [])
          setTotalPages(response.totalPages || 0)
        } else {
          const response = await financeService.getAllPayrolls(page, 20)
          setAllPayrolls(response.content || [])
          setTotalPages(response.totalPages || 0)
        }
      }
    } catch (error) {
      enqueueSnackbar('Error loading data', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  // Expense handlers
  const handleSelectAllExpenses = (event) => {
    if (event.target.checked) {
      setSelectedExpenses(approvedExpenses.map(e => e.id))
    } else {
      setSelectedExpenses([])
    }
  }

  const handleSelectExpense = (expenseId) => {
    setSelectedExpenses(prev =>
      prev.includes(expenseId)
        ? prev.filter(id => id !== expenseId)
        : [...prev, expenseId]
    )
  }

  const handleMarkExpenseAsPaid = async (expenseId = null) => {
    setProcessing(true)
    try {
      const ids = expenseId ? [expenseId] : selectedExpenses
      if (ids.length === 0) {
        enqueueSnackbar('Please select at least one expense', { variant: 'warning' })
        return
      }

      if (ids.length === 1) {
        await financeService.markAsPaid(ids[0])
        enqueueSnackbar('Expense marked as paid successfully', { variant: 'success' })
      } else {
        await financeService.bulkMarkAsPaid(ids)
        enqueueSnackbar(`${ids.length} expenses marked as paid successfully`, { variant: 'success' })
      }

      setSelectedExpenses([])
      setConfirmDialog({ open: false, type: null })
      fetchData()
    } catch (error) {
      enqueueSnackbar('Error marking expenses as paid', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  // Payroll handlers
  const handleGeneratePayroll = async () => {
    setProcessing(true)
    try {
      await financeService.generatePayroll(generateData.month, generateData.year)
      enqueueSnackbar('Payroll generated successfully for all employees', { variant: 'success' })
      setGenerateDialog({ open: false })
      fetchData()
    } catch (error) {
      enqueueSnackbar('Error generating payroll', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleProcessPayroll = async (payrollId) => {
    setProcessing(true)
    try {
      await financeService.processPayroll(payrollId)
      enqueueSnackbar('Payroll processed successfully', { variant: 'success' })
      fetchData()
    } catch (error) {
      enqueueSnackbar('Error processing payroll', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleMarkPayrollAsPaid = async (payrollId) => {
    setProcessing(true)
    try {
      await financeService.markPayrollAsPaid(payrollId)
      enqueueSnackbar('Payroll marked as paid successfully', { variant: 'success' })
      fetchData()
    } catch (error) {
      enqueueSnackbar('Error marking payroll as paid', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const getMonthName = (month) => {
    const months = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'
    ]
    return months[month - 1] || ''
  }

  const currentExpenses = expenseTabValue === 0 ? approvedExpenses : paidExpenses
  const currentPayrolls = payrollTabValue === 0 ? processedPayrolls : (payrollTabValue === 1 ? paidPayrolls : allPayrolls)

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
            <AccountBalanceWalletIcon sx={{ fontSize: 40, color: 'primary.main', mt: 0.5 }} />
            <Box>
              <Typography
                variant="h4"
                component="h1"
                sx={pageTitleSx}
              >
                Finance
              </Typography>
              <Typography variant="body2" sx={pageSubtitleSx}>
                Pay approved expenses and run payroll.
              </Typography>
            </Box>
          </Box>
          {section === 0 && expenseTabValue === 0 && selectedExpenses.length > 0 && (
            <Button
              variant="contained"
              startIcon={<PaymentIcon />}
              onClick={() => setConfirmDialog({ open: true, type: 'expense' })}
              sx={{
                borderRadius: softRadius,
                px: 3,
                py: 1.5,
                backgroundColor: 'primary.main',
                fontWeight: 600,
                textTransform: 'none',
              }}
            >
              Mark {selectedExpenses.length} as Paid
            </Button>
          )}
          {section === 1 && (
            <Button
              variant="contained"
              startIcon={<CalculateIcon />}
              onClick={() => setGenerateDialog({ open: true })}
              sx={{
                borderRadius: softRadius,
                px: 3,
                py: 1.5,
                backgroundColor: 'primary.main',
                fontWeight: 600,
                textTransform: 'none',
              }}
            >
              Generate Payroll
            </Button>
          )}
        </Box>

        {/* Section Tabs */}
        <Box sx={{ mb: 4 }}>
          <Box sx={{ display: 'flex', gap: 2, borderBottom: `2px solid ${isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'}` }}>
            <Button
              onClick={() => { 
                setSection(0); 
                setPage(0); 
                setSelectedExpenses([]); 
                setExpenseTabValue(0);
                setSearchParams({ section: '0' })
              }}
              sx={{
                px: 3,
                py: 1.5,
                borderRadius: 0,
                borderBottom: section === 0 ? `3px solid ${theme.palette.primary.main}` : 'none',
                color: section === 0 ? theme.palette.primary.main : 'text.secondary',
                fontWeight: 600,
                textTransform: 'none',
              }}
            >
              Expenses
            </Button>
            <Button
              onClick={() => { 
                setSection(1); 
                setPage(0); 
                setSelectedPayrolls([]); 
                setPayrollTabValue(0);
                setSearchParams({ section: '1' })
              }}
              sx={{
                px: 3,
                py: 1.5,
                borderRadius: 0,
                borderBottom: section === 1 ? '3px solid #B8894A' : 'none',
                color: section === 1 ? theme.palette.warning.main : 'text.secondary',
                fontWeight: 600,
                textTransform: 'none',
              }}
            >
              Payroll
            </Button>
          </Box>
        </Box>

        {/* Expenses Section */}
        {section === 0 && (
          <>
            {/* Expense Sub-tabs */}
            <Box sx={{ mb: 4 }}>
              <Box sx={{ display: 'flex', gap: 2, borderBottom: `2px solid ${isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'}` }}>
                <Button
                  onClick={() => { setExpenseTabValue(0); setPage(0); setSelectedExpenses([]) }}
                  sx={{
                    px: 3,
                    py: 1.5,
                    borderRadius: 0,
                    borderBottom: expenseTabValue === 0 ? `3px solid ${theme.palette.primary.main}` : 'none',
                    color: expenseTabValue === 0 ? theme.palette.primary.main : 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  Approved Expenses ({approvedExpenses.length})
                </Button>
                <Button
                  onClick={() => { setExpenseTabValue(1); setPage(0); setSelectedExpenses([]) }}
                  sx={{
                    px: 3,
                    py: 1.5,
                    borderRadius: 0,
                    borderBottom: expenseTabValue === 1 ? `3px solid ${theme.palette.primary.main}` : 'none',
                    color: expenseTabValue === 1 ? theme.palette.primary.main : 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  Paid Expenses ({paidExpenses.length})
                </Button>
              </Box>
            </Box>

            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
                <CircularProgress />
              </Box>
            ) : currentExpenses.length === 0 ? (
              <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
                <CardContent sx={{ p: 4, textAlign: 'center' }}>
                  <Typography color="text.secondary" sx={{ opacity: 0.6 }}>
                    {expenseTabValue === 0 ? 'No approved expenses ready for payment' : 'No paid expenses'}
                  </Typography>
                </CardContent>
              </Card>
            ) : (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1, duration: 0.25, ease: [0.25, 0.1, 0.25, 1] }}
              >
                <TableContainer component={Paper} sx={{ ...getSurfaceStyles(isDark, 'navigation'), overflowX: 'auto' }}>
                  <Table>
                    <TableHead>
                      <TableRow>
                        {expenseTabValue === 0 && (
                          <TableCell padding="checkbox" sx={{ py: 2, px: 3 }}>
                            <Checkbox
                              checked={selectedExpenses.length === approvedExpenses.length && approvedExpenses.length > 0}
                              indeterminate={selectedExpenses.length > 0 && selectedExpenses.length < approvedExpenses.length}
                              onChange={handleSelectAllExpenses}
                            />
                          </TableCell>
                        )}
                        <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Description
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Amount
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Employee
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Date
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Status
                        </TableCell>
                        {expenseTabValue === 0 && (
                          <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                            Actions
                          </TableCell>
                        )}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {currentExpenses.map((expense) => (
                        <TableRow
                          key={expense.id}
                          hover
                          sx={{
                            '&:hover': {
                              backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                            },
                          }}
                        >
                          {expenseTabValue === 0 && (
                            <TableCell padding="checkbox" sx={{ py: 2, px: 3 }}>
                              <Checkbox
                                checked={selectedExpenses.includes(expense.id)}
                                onChange={() => handleSelectExpense(expense.id)}
                              />
                            </TableCell>
                          )}
                          <TableCell sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.9375rem' }}>
                              {expense.description}
                            </Typography>
                          </TableCell>
                          <TableCell align="right" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                              ${expense.amount?.toFixed(2)}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                              {expense.userName || '-'}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                              {expense.expenseDate ? format(new Date(expense.expenseDate), 'MMM dd, yyyy') : '-'}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Chip
                              label={expense.status}
                              size="small"
                              sx={{
                                backgroundColor: expenseStatusColors[expense.status]?.bg || 'rgba(0, 0, 0, 0.1)',
                                color: expenseStatusColors[expense.status]?.color || 'text.primary',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                                border: `1px solid ${alpha(toCssColor(theme, expenseStatusColors[expense.status]?.color || '#000'), 0.3)}`,
                              }}
                            />
                          </TableCell>
                          {expenseTabValue === 0 && (
                            <TableCell align="center" sx={{ py: 2, px: 3 }}>
                              <Button
                                size="small"
                                variant="contained"
                                startIcon={<PaymentIcon />}
                                onClick={() => handleMarkExpenseAsPaid(expense.id)}
                                disabled={processing}
                                sx={{
                                  borderRadius: 10,
                                  px: 2.5,
                                  py: 0.75,
                                  backgroundColor: 'primary.main',
                                  fontWeight: 600,
                                  fontSize: '0.8125rem',
                                  textTransform: 'none',
                                }}
                              >
                                Mark Paid
                              </Button>
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
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
                    />
                  </Box>
                )}
              </motion.div>
            )}
          </>
        )}

        {/* Payroll Section */}
        {section === 1 && (
          <>
            {/* Payroll Sub-tabs */}
            <Box sx={{ mb: 4 }}>
              <Box sx={{ display: 'flex', gap: 2, borderBottom: `2px solid ${isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)'}` }}>
                <Button
                  onClick={() => { setPayrollTabValue(0); setPage(0); setSelectedPayrolls([]) }}
                  sx={{
                    px: 3,
                    py: 1.5,
                    borderRadius: 0,
                    borderBottom: payrollTabValue === 0 ? '3px solid #4A7A8C' : 'none',
                    color: payrollTabValue === 0 ? theme.palette.info.main : 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  Processed Payrolls ({processedPayrolls.length})
                </Button>
                <Button
                  onClick={() => { setPayrollTabValue(1); setPage(0); setSelectedPayrolls([]) }}
                  sx={{
                    px: 3,
                    py: 1.5,
                    borderRadius: 0,
                    borderBottom: payrollTabValue === 1 ? `3px solid ${theme.palette.primary.main}` : 'none',
                    color: payrollTabValue === 1 ? theme.palette.primary.main : 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  Paid Payrolls ({paidPayrolls.length})
                </Button>
                <Button
                  onClick={() => { setPayrollTabValue(2); setPage(0); setSelectedPayrolls([]) }}
                  sx={{
                    px: 3,
                    py: 1.5,
                    borderRadius: 0,
                    borderBottom: payrollTabValue === 2 ? '3px solid #B8894A' : 'none',
                    color: payrollTabValue === 2 ? theme.palette.warning.main : 'text.secondary',
                    fontWeight: 600,
                    textTransform: 'none',
                  }}
                >
                  All Payrolls ({allPayrolls.length})
                </Button>
              </Box>
            </Box>

            {loading ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
                <CircularProgress />
              </Box>
            ) : currentPayrolls.length === 0 ? (
              <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
                <CardContent sx={{ p: 4, textAlign: 'center' }}>
                  <Typography color="text.secondary" sx={{ opacity: 0.6 }}>
                    {payrollTabValue === 0 ? 'No processed payrolls ready for payment' : 
                     payrollTabValue === 1 ? 'No paid payrolls' : 'No payroll records found'}
                  </Typography>
                </CardContent>
              </Card>
            ) : (
              <motion.div
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1, duration: 0.25, ease: [0.25, 0.1, 0.25, 1] }}
              >
                <TableContainer component={Paper} sx={{ ...getSurfaceStyles(isDark, 'navigation'), overflowX: 'auto' }}>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Employee
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Period
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Days Worked
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Paid Leaves
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Unpaid Leaves
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Base Salary
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Deductions
                        </TableCell>
                        <TableCell align="right" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Net Salary
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Status
                        </TableCell>
                        <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                          Actions
                        </TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {currentPayrolls.map((payroll) => (
                        <TableRow
                          key={payroll.id}
                          hover
                          sx={{
                            '&:hover': {
                              backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                            },
                          }}
                        >
                          <TableCell sx={{ py: 2, px: 3 }}>
                            <Box>
                              <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.9375rem' }}>
                                {payroll.userName}
                              </Typography>
                              <Typography variant="caption" sx={{ fontSize: '0.75rem', opacity: 0.6 }}>
                                {payroll.userEmail}
                              </Typography>
                            </Box>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontSize: '0.875rem', fontWeight: 500 }}>
                              {getMonthName(payroll.periodMonth)} {payroll.periodYear}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                              {payroll.daysWorked} / {payroll.totalDaysInMonth}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Chip
                              label={payroll.paidLeavesUsed}
                              size="small"
                              sx={{
                                backgroundColor: 'rgba(79, 122, 92, 0.12)',
                                color: 'primary.main',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                              }}
                            />
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Chip
                              label={payroll.unpaidLeavesUsed}
                              size="small"
                              sx={{
                                backgroundColor: 'rgba(196, 91, 106, 0.15)',
                                color: 'error.main',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                              }}
                            />
                          </TableCell>
                          <TableCell align="right" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                              ${payroll.baseSalary?.toFixed(2)}
                            </Typography>
                          </TableCell>
                          <TableCell align="right" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontSize: '0.875rem', color: 'error.main' }}>
                              -${payroll.deductions?.toFixed(2)}
                            </Typography>
                          </TableCell>
                          <TableCell align="right" sx={{ py: 2, px: 3 }}>
                            <Typography variant="body2" sx={{ fontWeight: 700, fontSize: '1rem', color: 'primary.main' }}>
                              ${payroll.netSalary?.toFixed(2)}
                            </Typography>
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Chip
                              label={payroll.status}
                              size="small"
                              sx={{
                                backgroundColor: payrollStatusColors[payroll.status]?.bg || 'rgba(0, 0, 0, 0.1)',
                                color: payrollStatusColors[payroll.status]?.color || 'text.primary',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                                border: `1px solid ${alpha(toCssColor(theme, payrollStatusColors[payroll.status]?.color || '#000'), 0.3)}`,
                              }}
                            />
                          </TableCell>
                          <TableCell align="center" sx={{ py: 2, px: 3 }}>
                            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                              {payroll.status === 'DRAFT' && (
                                <Button
                                  size="small"
                                  variant="contained"
                                  startIcon={<PlayArrowIcon />}
                                  onClick={() => handleProcessPayroll(payroll.id)}
                                  disabled={processing}
                                  sx={{
                                    borderRadius: 10,
                                    px: 2,
                                    py: 0.5,
                                    backgroundColor: 'primary.main',
                                    fontWeight: 600,
                                    fontSize: '0.75rem',
                                    textTransform: 'none',
                                  }}
                                >
                                  Process
                                </Button>
                              )}
                              {payroll.status === 'PROCESSED' && (
                                <Button
                                  size="small"
                                  variant="contained"
                                  startIcon={<PaymentIcon />}
                                  onClick={() => handleMarkPayrollAsPaid(payroll.id)}
                                  disabled={processing}
                                  sx={{
                                    borderRadius: 10,
                                    px: 2,
                                    py: 0.5,
                                    backgroundColor: 'primary.main',
                                    fontWeight: 600,
                                    fontSize: '0.75rem',
                                    textTransform: 'none',
                                  }}
                                >
                                  Mark Paid
                                </Button>
                              )}
                            </Box>
                          </TableCell>
                        </TableRow>
                      ))}
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
                    />
                  </Box>
                )}
              </motion.div>
            )}
          </>
        )}

        {/* Confirmation Dialog for Expenses */}
        <Dialog
          open={confirmDialog.open && confirmDialog.type === 'expense'}
          onClose={() => setConfirmDialog({ open: false, type: null })}
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600 }}>Confirm Payment</DialogTitle>
          <DialogContent>
            <Typography>
              Are you sure you want to mark {selectedExpenses.length} expense(s) as paid?
            </Typography>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setConfirmDialog({ open: false, type: null })}>Cancel</Button>
            <Button
              onClick={() => handleMarkExpenseAsPaid()}
              variant="contained"
              disabled={processing}
              sx={{
                backgroundColor: 'primary.main',
              }}
            >
              {processing ? <CircularProgress size={24} /> : 'Confirm'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Generate Payroll Dialog */}
        <Dialog
          open={generateDialog.open}
          onClose={() => setGenerateDialog({ open: false })}
          maxWidth="sm"
          fullWidth
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600 }}>Generate Payroll</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Month</InputLabel>
                <Select
                  value={generateData.month}
                  onChange={(e) => setGenerateData({ ...generateData, month: e.target.value })}
                  label="Month"
                >
                  {Array.from({ length: 12 }, (_, i) => i + 1).map((month) => (
                    <MenuItem key={month} value={month}>
                      {getMonthName(month)}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Year"
                type="number"
                value={generateData.year}
                onChange={(e) => setGenerateData({ ...generateData, year: parseInt(e.target.value) })}
                fullWidth
                inputProps={{ min: 2020, max: 2100 }}
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setGenerateDialog({ open: false })}>Cancel</Button>
            <Button
              onClick={handleGeneratePayroll}
              variant="contained"
              disabled={processing}
              sx={{
                backgroundColor: 'primary.main',
              }}
            >
              {processing ? <CircularProgress size={24} /> : 'Generate'}
            </Button>
          </DialogActions>
        </Dialog>
      </motion.div>
    </Box>
  )
}
