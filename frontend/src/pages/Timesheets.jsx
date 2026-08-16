import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  Typography,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Pagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  CircularProgress,
  useTheme,
  alpha,
  IconButton,
} from '@mui/material'
import {
  Add as AddIcon,
  CheckCircle as CheckCircleIcon,
  Cancel as CancelIcon,
  AccessTime as AccessTimeIcon,
  ChevronLeft as ChevronLeftIcon,
  ChevronRight as ChevronRightIcon,
  EventBusy as LeaveIcon,
  Work as WorkIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { timesheetService } from '../services/timesheetService'
import { projectService } from '../services/projectService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { useAuth } from '../contexts/AuthContext'
import { canApprove } from '../utils/roleUtils'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { toCssColor, pageSx, pageHeaderSx, pageTitleSx, pageSubtitleSx, softRadius, actionIconSx, paletteTone, sectionGap, cardPad } from '../utils/uiTokens'

const statusColors = {
  DRAFT: { color: 'warning.main', bg: 'rgba(184, 137, 74, 0.15)' },
  SUBMITTED: { color: 'info.main', bg: 'rgba(74, 122, 140, 0.12)' },
  APPROVED: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  REJECTED: { color: 'error.main', bg: 'rgba(196, 91, 106, 0.15)' },
  EMPTY: { color: 'text.secondary', bg: 'rgba(142, 142, 147, 0.15)' },
}

const entryTypeColors = {
  WORK: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)', icon: WorkIcon },
  LEAVE: { color: 'text.secondary', bg: 'rgba(74, 85, 104, 0.15)', icon: LeaveIcon },
}

// Helper to get Monday of a given week
const getMonday = (date) => {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  return new Date(d.setDate(diff))
}

export default function Timesheets() {
  const [timesheets, setTimesheets] = useState([])
  const [projects, setProjects] = useState([])
  const [weeklySummary, setWeeklySummary] = useState(null)
  const [selectedWeek, setSelectedWeek] = useState(getMonday(new Date()))
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [actionDialog, setActionDialog] = useState({ open: false, timesheet: null, action: null })
  const [comments, setComments] = useState('')
  const [processing, setProcessing] = useState(false)
  const [createDialog, setCreateDialog] = useState({ open: false })
  const [newTimesheet, setNewTimesheet] = useState({
    date: format(new Date(), 'yyyy-MM-dd'),
    projectCode: '',
    hours: 8,
    description: '',
  })
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    fetchTimesheets()
    fetchProjects()
    fetchWeeklySummary()
  }, [page, selectedWeek])

  const fetchWeeklySummary = async () => {
    try {
      const weekStart = format(selectedWeek, 'yyyy-MM-dd')
      const summary = await timesheetService.getWeeklySummary(weekStart)
      setWeeklySummary(summary)
    } catch (error) {
      console.error('Error loading weekly summary', error)
    }
  }

  const navigateWeek = (direction) => {
    const newWeek = new Date(selectedWeek)
    newWeek.setDate(newWeek.getDate() + (direction * 7))
    setSelectedWeek(newWeek)
  }

  const fetchTimesheets = async () => {
    setLoading(true)
    try {
      const response = await timesheetService.getAll(page, 20)
      setTimesheets(response.content || [])
      setTotalPages(response.totalPages || 0)
    } catch (error) {
      enqueueSnackbar('Error loading timesheets', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const fetchProjects = async () => {
    try {
      const projectsData = await projectService.getActive()
      setProjects(projectsData || [])
    } catch (error) {
      // Projects might not be accessible to all users
    }
  }

  const handleCreate = async () => {
    if (!newTimesheet.date || !newTimesheet.projectCode || !newTimesheet.hours) {
      enqueueSnackbar('Please fill all required fields', { variant: 'warning' })
      return
    }

    setProcessing(true)
    try {
      await timesheetService.create(newTimesheet)
      enqueueSnackbar('Timesheet created successfully', { variant: 'success' })
      setCreateDialog({ open: false })
      setNewTimesheet({ date: format(new Date(), 'yyyy-MM-dd'), projectCode: '', hours: 8, description: '' })
      fetchTimesheets()
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Error creating timesheet', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleSubmit = async (id) => {
    setProcessing(true)
    try {
      await timesheetService.submit(id)
      enqueueSnackbar('Timesheet submitted successfully', { variant: 'success' })
      fetchTimesheets()
    } catch (error) {
      enqueueSnackbar('Error submitting timesheet', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleApprove = async () => {
    if (!actionDialog.timesheet) return
    setProcessing(true)
    try {
      await timesheetService.approve(actionDialog.timesheet.id, comments)
      enqueueSnackbar('Timesheet approved successfully', { variant: 'success' })
      setActionDialog({ open: false, timesheet: null, action: null })
      setComments('')
      fetchTimesheets()
    } catch (error) {
      enqueueSnackbar('Error approving timesheet', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleReject = async () => {
    if (!actionDialog.timesheet || !comments.trim()) {
      enqueueSnackbar('Please provide rejection comments', { variant: 'warning' })
      return
    }
    setProcessing(true)
    try {
      await timesheetService.reject(actionDialog.timesheet.id, comments)
      enqueueSnackbar('Timesheet rejected successfully', { variant: 'success' })
      setActionDialog({ open: false, timesheet: null, action: null })
      setComments('')
      fetchTimesheets()
    } catch (error) {
      enqueueSnackbar('Error rejecting timesheet', { variant: 'error' })
    } finally {
      setProcessing(false)
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
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <AccessTimeIcon sx={{ fontSize: 40, color: 'primary.light' }} />
            <Box>
            <Typography
              variant="h4"
              component="h1"
              sx={pageTitleSx}
            >Timesheets</Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Log work days and send them for review.
            </Typography>
          </Box>
          </Box>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateDialog({ open: true })}
            sx={{
              borderRadius: softRadius,
              px: 3,
              py: 1.5,
              backgroundColor: 'primary.main',
              fontWeight: 600,
              textTransform: 'none',
            }}
          >
            Create Timesheet
          </Button>
        </Box>

        {/* Weekly View */}
        {weeklySummary && (
          <Card sx={{ ...getSurfaceStyles(isDark, 'card'), mb: sectionGap }}>
            <CardContent sx={{ p: cardPad }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 3 }}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Weekly Timesheet
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <IconButton onClick={() => navigateWeek(-1)} size="small">
                    <ChevronLeftIcon />
                  </IconButton>
                  <Typography variant="body2" sx={{ minWidth: 200, textAlign: 'center' }}>
                    {format(selectedWeek, 'MMM d')} - {format(new Date(selectedWeek.getTime() + 4 * 24 * 60 * 60 * 1000), 'MMM d, yyyy')}
                  </Typography>
                  <IconButton onClick={() => navigateWeek(1)} size="small">
                    <ChevronRightIcon />
                  </IconButton>
                </Box>
              </Box>
              
              <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 2 }}>
                {weeklySummary.days?.map((day, index) => {
                  const isLeave = day.entryType === 'LEAVE'
                  const isEmpty = day.status === 'EMPTY'
                  const colors = isEmpty ? statusColors.EMPTY : (isLeave ? entryTypeColors.LEAVE : statusColors[day.status] || statusColors.DRAFT)
                  
                  return (
                    <Card 
                      key={index}
                      sx={{
                        ...getSurfaceStyles(isDark, 'card'),
                        p: 2,
                        cursor: day.isEditable && isEmpty ? 'pointer' : 'default',
                        opacity: isEmpty ? 0.7 : 1,
                        borderLeft: `4px solid ${colors.color}`,
                        '&:hover': day.isEditable && isEmpty ? {
                          transform: 'translateY(-2px)',
                          border: `1px solid ${alpha(toCssColor(theme, colors.color), 0.28)}`,
                        } : {},
                      }}
                      onClick={() => {
                        if (day.isEditable && isEmpty) {
                          setNewTimesheet(prev => ({ ...prev, date: day.date }))
                          setCreateDialog({ open: true })
                        }
                      }}
                    >
                      <Typography variant="caption" sx={{ opacity: 0.7, textTransform: 'uppercase', fontSize: '0.65rem' }}>
                        {day.dayOfWeek?.slice(0, 3)}
                      </Typography>
                      <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                        {format(new Date(day.date), 'MMM d')}
                      </Typography>
                      
                      {isLeave ? (
                        <Box>
                          <Chip
                            icon={<LeaveIcon sx={{ fontSize: 14 }} />}
                            label={day.leaveType?.replace('_', ' ') || 'Leave'}
                            size="small"
                            sx={{
                              backgroundColor: colors.bg,
                              color: colors.color,
                              fontSize: '0.65rem',
                              height: 24,
                              mb: 1,
                            }}
                          />
                          <Typography variant="caption" display="block" sx={{ opacity: 0.6 }}>
                            {day.isPaidLeave ? 'Paid' : 'Unpaid'}
                          </Typography>
                        </Box>
                      ) : isEmpty ? (
                        <Typography variant="caption" sx={{ opacity: 0.5 }}>
                          Click to add
                        </Typography>
                      ) : (
                        <Box>
                          <Chip
                            label={day.status}
                            size="small"
                            sx={{
                              backgroundColor: colors.bg,
                              color: colors.color,
                              fontSize: '0.65rem',
                              height: 24,
                              mb: 1,
                            }}
                          />
                          <Typography variant="caption" display="block" sx={{ fontWeight: 600 }}>
                            {day.projectCode}
                          </Typography>
                          <Typography variant="caption" sx={{ opacity: 0.6 }}>
                            {day.hours}h
                          </Typography>
                        </Box>
                      )}
                    </Card>
                  )
                })}
              </Box>
            </CardContent>
          </Card>
        )}

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
            <CircularProgress />
          </Box>
        ) : timesheets.length === 0 ? (
          <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
            <CardContent sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary" sx={{ opacity: 0.6 }}>
                No timesheets found
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
                      Date
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Project Code
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Hours
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Description
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
                  {timesheets.map((timesheet) => (
                    <TableRow
                      key={timesheet.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {timesheet.date ? format(new Date(timesheet.date), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        {timesheet.entryType === 'LEAVE' ? (
                          <Chip
                            icon={<LeaveIcon sx={{ fontSize: 14 }} />}
                            label={timesheet.leaveType?.replace('_', ' ') || 'Leave'}
                            size="small"
                            sx={{
                              backgroundColor: 'rgba(74, 85, 104, 0.15)',
                              color: 'text.secondary',
                              fontWeight: 600,
                              fontSize: '0.75rem',
                            }}
                          />
                        ) : (
                          <Chip
                            label={timesheet.projectCode || 'BENCH'}
                            size="small"
                            sx={{
                              backgroundColor: timesheet.projectCode === 'BENCH' 
                                ? paletteTone(theme, 'warning').bg
                                : paletteTone(theme, 'success').bg,
                              color: timesheet.projectCode === 'BENCH' ? 'warning.main' : 'success.main',
                              fontWeight: 600,
                              fontSize: '0.75rem',
                            }}
                          />
                        )}
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                          {timesheet.entryType === 'LEAVE' ? (
                            <span style={{ color: timesheet.isPaidLeave ? theme.palette.primary.main : theme.palette.warning.main }}>
                              {timesheet.isPaidLeave ? 'Paid' : 'Unpaid'}
                            </span>
                          ) : (
                            `${timesheet.hours}h`
                          )}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem', opacity: 0.8 }}>
                          {timesheet.description || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={timesheet.status}
                          size="small"
                          sx={{
                            backgroundColor: statusColors[timesheet.status]?.bg || 'rgba(0, 0, 0, 0.1)',
                            color: statusColors[timesheet.status]?.color || 'text.primary',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            border: `1px solid ${alpha(toCssColor(theme, statusColors[timesheet.status]?.color || '#000'), 0.3)}`,
                          }}
                        />
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                          {timesheet.status === 'DRAFT' && (
                            <Button
                              size="small"
                              variant="contained"
                              onClick={() => handleSubmit(timesheet.id)}
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
                              Submit
                            </Button>
                          )}
                          {canApprove(user) && timesheet.status === 'SUBMITTED' && (
                            <>
                              <IconButton
                                size="small"
                                onClick={() => setActionDialog({ open: true, timesheet, action: 'approve' })}
                                sx={actionIconSx(theme, 'success')}
                              >
                                <CheckCircleIcon fontSize="small" />
                              </IconButton>
                              <IconButton
                                size="small"
                                onClick={() => setActionDialog({ open: true, timesheet, action: 'reject' })}
                                sx={actionIconSx(theme, 'error')}
                              >
                                <CancelIcon fontSize="small" />
                              </IconButton>
                            </>
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

        {/* Create Timesheet Dialog */}
        <Dialog
          open={createDialog.open}
          onClose={() => setCreateDialog({ open: false })}
          maxWidth="sm"
          fullWidth
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600 }}>Create Timesheet</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              <TextField
                label="Date"
                type="date"
                value={newTimesheet.date}
                onChange={(e) => setNewTimesheet({ ...newTimesheet, date: e.target.value })}
                InputLabelProps={{ shrink: true }}
                fullWidth
                required
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <TextField
                label="Project Code"
                value={newTimesheet.projectCode}
                onChange={(e) => setNewTimesheet({ ...newTimesheet, projectCode: e.target.value })}
                fullWidth
                required
                placeholder="Enter project code or BENCH"
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <TextField
                label="Hours"
                type="number"
                value={newTimesheet.hours}
                onChange={(e) => setNewTimesheet({ ...newTimesheet, hours: parseFloat(e.target.value) || 0 })}
                fullWidth
                required
                inputProps={{ min: 0, max: 24, step: 0.5 }}
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <TextField
                label="Description"
                multiline
                rows={3}
                value={newTimesheet.description}
                onChange={(e) => setNewTimesheet({ ...newTimesheet, description: e.target.value })}
                fullWidth
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
            <Button onClick={() => setCreateDialog({ open: false })}>Cancel</Button>
            <Button
              onClick={handleCreate}
              variant="contained"
              disabled={processing}
              sx={{
                backgroundColor: 'primary.main',
              }}
            >
              {processing ? <CircularProgress size={24} /> : 'Create'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Approve/Reject Dialog */}
        <Dialog
          open={actionDialog.open}
          onClose={() => setActionDialog({ open: false, timesheet: null, action: null })}
          maxWidth="sm"
          fullWidth
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600 }}>
            {actionDialog.action === 'approve' ? 'Approve Timesheet' : 'Reject Timesheet'}
          </DialogTitle>
          <DialogContent>
            {actionDialog.timesheet && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  Date: {format(new Date(actionDialog.timesheet.date), 'MMM dd, yyyy')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Project: {actionDialog.timesheet.projectCode}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Hours: {actionDialog.timesheet.hours}
                </Typography>
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
                '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                  backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                  padding: '0 8px',
                  borderRadius: 1,
                },
              }}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => { setActionDialog({ open: false, timesheet: null, action: null }); setComments('') }}>
              Cancel
            </Button>
            <Button
              onClick={actionDialog.action === 'approve' ? handleApprove : handleReject}
              variant="contained"
              disabled={processing}
              sx={{
                background: actionDialog.action === 'approve'
                  ? theme.palette.success.main
                  : theme.palette.error.main,
              }}
            >
              {processing ? <CircularProgress size={24} /> : actionDialog.action === 'approve' ? 'Approve' : 'Reject'}
            </Button>
          </DialogActions>
        </Dialog>
      </motion.div>
    </Box>
  )
}
