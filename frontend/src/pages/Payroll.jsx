import React, { useState, useEffect } from 'react'
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
  Chip,
  Pagination,
  CircularProgress,
  useTheme,
  alpha,
} from '@mui/material'
import { AccountBalance as AccountBalanceIcon } from '@mui/icons-material'
import { motion } from 'framer-motion'
import { payrollService } from '../services/payrollService'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  tableHeadCellSx,
  iconWellSx,
  statusTone,
  paletteTone,
} from '../utils/uiTokens'

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export default function Payroll() {
  const [payrolls, setPayrolls] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const success = paletteTone(theme, 'success')
  const error = paletteTone(theme, 'error')
  const headSx = tableHeadCellSx(theme)

  useEffect(() => {
    fetchPayrolls()
  }, [page])

  const fetchPayrolls = async () => {
    setLoading(true)
    try {
      const response = await payrollService.getAll(page, 20)
      setPayrolls(response.content || [])
      setTotalPages(response.totalPages || 0)
    } catch (err) {
      enqueueSnackbar('Error loading payroll', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
            <Box sx={iconWellSx(theme, 'warning')}>
              <AccountBalanceIcon />
            </Box>
            <Box>
              <Typography variant="h4" component="h1" sx={pageTitleSx}>
                Payroll
              </Typography>
              <Typography variant="body2" sx={pageSubtitleSx}>
                Your monthly pay slips and deductions.
              </Typography>
            </Box>
          </Box>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 320 }}>
            <CircularProgress color="primary" />
          </Box>
        ) : payrolls.length === 0 ? (
          <Card sx={getSurfaceStyles(isDark, 'card')}>
            <CardContent sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary">No payroll records yet.</Typography>
            </CardContent>
          </Card>
        ) : (
          <motion.div
            initial={motionPresets.cardInitial}
            animate={motionPresets.cardAnimate}
            transition={motionPresets.cardTransition}
          >
            <TableContainer
              component={Paper}
              sx={{ ...getSurfaceStyles(isDark, 'card'), overflowX: 'auto' }}
            >
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell align="center" sx={headSx}>Period</TableCell>
                    <TableCell align="center" sx={headSx}>Days worked</TableCell>
                    <TableCell align="center" sx={headSx}>Paid leave</TableCell>
                    <TableCell align="center" sx={headSx}>Unpaid leave</TableCell>
                    <TableCell align="right" sx={headSx}>Base</TableCell>
                    <TableCell align="right" sx={headSx}>Deductions</TableCell>
                    <TableCell align="right" sx={headSx}>Net</TableCell>
                    <TableCell align="center" sx={headSx}>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {payrolls.map((payroll) => {
                    const tone = statusTone(theme, payroll.status)
                    return (
                      <TableRow
                        key={payroll.id}
                        hover
                        sx={{
                          '&:hover': {
                            backgroundColor: isDark
                              ? 'rgba(255,255,255,0.04)'
                              : 'rgba(30,41,59,0.03)',
                          },
                        }}
                      >
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {MONTHS[payroll.periodMonth - 1] || ''} {payroll.periodYear}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Typography variant="body2">
                            {payroll.daysWorked} / {payroll.totalDaysInMonth}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Chip
                            label={payroll.paidLeavesUsed}
                            size="small"
                            sx={{ backgroundColor: success.bg, color: success.color, fontWeight: 600 }}
                          />
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Chip
                            label={payroll.unpaidLeavesUsed}
                            size="small"
                            sx={{ backgroundColor: error.bg, color: error.color, fontWeight: 600 }}
                          />
                        </TableCell>
                        <TableCell align="right" sx={{ py: 2, px: 3 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            ${payroll.baseSalary?.toFixed(2)}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" sx={{ py: 2, px: 3 }}>
                          <Typography variant="body2" color="error.main">
                            -${payroll.deductions?.toFixed(2)}
                          </Typography>
                        </TableCell>
                        <TableCell align="right" sx={{ py: 2, px: 3 }}>
                          <Typography variant="body2" sx={{ fontWeight: 700, color: 'primary.main' }}>
                            ${payroll.netSalary?.toFixed(2)}
                          </Typography>
                        </TableCell>
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Chip
                            label={payroll.status}
                            size="small"
                            sx={{
                              backgroundColor: tone.bg,
                              color: tone.color,
                              fontWeight: 600,
                              border: `1px solid ${alpha(tone.color, 0.28)}`,
                            }}
                          />
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
                />
              </Box>
            )}
          </motion.div>
        )}
      </motion.div>
    </Box>
  )
}
