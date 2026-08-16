import React, { useState, useEffect } from 'react'
import {
  Box,
  Button,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
  useTheme,
  Pagination,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import { motion } from 'framer-motion'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { leaveRequestService } from '../services/leaveRequestService'
import { useSnackbar } from 'notistack'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  tableHeadCellSx,
  chipSx,
  sectionGap,
  stackGapLoose,
} from '../utils/uiTokens'

const LeaveRequests = () => {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const headSx = tableHeadCellSx(theme)

  const [leaveRequests, setLeaveRequests] = useState([])
  const [leaveBalance, setLeaveBalance] = useState(null)
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  useEffect(() => {
    fetchLeaveRequests()
    fetchLeaveBalance()
  }, [page])

  const fetchLeaveBalance = async () => {
    try {
      const data = await leaveRequestService.getLeaveBalanceSummary()
      setLeaveBalance(data)
    } catch (error) {
      console.error('Failed to fetch leave balance', error)
    }
  }

  const fetchLeaveRequests = async () => {
    try {
      setLoading(true)
      const data = await leaveRequestService.getLeaveRequests(page, 20)
      setLeaveRequests(data.content || [])
      setTotalPages(data.totalPages || 0)
    } catch (error) {
      enqueueSnackbar('Failed to fetch leave requests', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const balanceCards = leaveBalance
    ? [
        {
          label: 'Paid leave',
          remaining: leaveBalance.paidLeave?.remaining || 0,
          allocated: leaveBalance.paidLeave?.allocated || 0,
          used: leaveBalance.paidLeave?.used || 0,
          color: 'primary.main',
        },
        {
          label: 'Unpaid leave',
          remaining: leaveBalance.unpaidLeave?.remaining || 0,
          allocated: leaveBalance.unpaidLeave?.allocated || 12,
          used: leaveBalance.unpaidLeave?.used || 0,
          color: 'warning.main',
        },
        {
          label: 'Sick leave',
          remaining: leaveBalance.sickLeave?.remaining || 0,
          allocated: leaveBalance.sickLeave?.allocated || 0,
          used: leaveBalance.sickLeave?.used || 0,
          color: 'error.main',
        },
        {
          label: 'Personal leave',
          remaining: leaveBalance.personalLeave?.remaining || 0,
          allocated: leaveBalance.personalLeave?.allocated || 0,
          used: leaveBalance.personalLeave?.used || 0,
          color: 'info.main',
        },
      ]
    : []

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box>
            <Typography variant="h4" component="h1" sx={pageTitleSx}>
              Leave
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Balances and requests for time away.
            </Typography>
          </Box>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={() => navigate('/leave-requests/new')}
            sx={{ px: 2.5, py: 1.25 }}
          >
            Request leave
          </Button>
        </Box>

        {leaveBalance && (
          <Box
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
              gap: stackGapLoose,
              mb: sectionGap,
            }}
          >
            {balanceCards.map((card) => (
              <Card key={card.label} sx={{ ...getSurfaceStyles(isDark, 'stat'), p: 2 }}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ textTransform: 'uppercase', letterSpacing: '0.04em', fontWeight: 600 }}
                >
                  {card.label}
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mt: 1 }}>
                  <Typography variant="h4" sx={{ fontWeight: 600, color: card.color }}>
                    {card.remaining}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    / {card.allocated} days
                  </Typography>
                </Box>
                <Typography variant="caption" color="text.secondary">
                  Used {card.used}
                </Typography>
              </Card>
            ))}
          </Box>
        )}

        <Card sx={getSurfaceStyles(isDark, 'card')}>
          <CardContent sx={{ p: { xs: 1, sm: 2 }, '&:last-child': { pb: { xs: 1, sm: 2 } } }}>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    {['Type', 'Start', 'End', 'Days', 'Status', 'Reason'].map((label) => (
                      <TableCell key={label} sx={headSx}>
                        {label}
                      </TableCell>
                    ))}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">Loading…</Typography>
                      </TableCell>
                    </TableRow>
                  ) : leaveRequests.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">No leave requests yet.</Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    leaveRequests.map((leave) => (
                      <TableRow
                        key={leave.id}
                        hover
                        sx={{
                          '&:hover': {
                            backgroundColor: isDark
                              ? 'rgba(255,255,255,0.04)'
                              : 'rgba(30,41,59,0.03)',
                          },
                        }}
                      >
                        <TableCell>
                          <Chip
                            label={leave.leaveType.replace(/_/g, ' ')}
                            size="small"
                            sx={chipSx(theme, 'info')}
                          />
                        </TableCell>
                        <TableCell>{new Date(leave.startDate).toLocaleDateString()}</TableCell>
                        <TableCell>{new Date(leave.endDate).toLocaleDateString()}</TableCell>
                        <TableCell>{leave.numberOfDays}</TableCell>
                        <TableCell>
                          <Chip
                            label={leave.status}
                            size="small"
                            sx={chipSx(theme, leave.status)}
                          />
                        </TableCell>
                        <TableCell
                          sx={{
                            maxWidth: 200,
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            color: 'text.secondary',
                          }}
                        >
                          {leave.reason || '-'}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
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
          </CardContent>
        </Card>
      </motion.div>
    </Box>
  )
}

export default LeaveRequests
