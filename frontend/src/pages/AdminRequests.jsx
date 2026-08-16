import React, { useEffect, useState } from 'react'
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
  IconButton,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Button,
  useTheme,
  Tooltip,
  Collapse,
  Divider,
  Alert,
} from '@mui/material'
import { 
  Check as CheckIcon, 
  Close as CloseIcon, 
  Assignment as AssignmentIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Person as PersonIcon,
  Folder as FolderIcon,
  PersonAdd as PersonAddIcon,
} from '@mui/icons-material'
import { adminRequestService } from '../services/adminRequestService'
import { useSnackbar } from 'notistack'
import { motion } from 'framer-motion'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { format } from 'date-fns'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  actionIconSx,
  paletteTone,
} from '../utils/uiTokens'

const statusToneKey = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
}

const typeMeta = {
  USER_CREATE: { key: 'primary', icon: PersonIcon, label: 'Create User' },
  USER_UPDATE: { key: 'secondary', icon: PersonIcon, label: 'Update User' },
  USER_DISABLE: { key: 'error', icon: PersonIcon, label: 'Disable User' },
  PROJECT_CREATE: { key: 'success', icon: FolderIcon, label: 'Create Project' },
  PROJECT_UPDATE: { key: 'secondary', icon: FolderIcon, label: 'Update Project' },
  PROJECT_DELETE: { key: 'error', icon: FolderIcon, label: 'Delete Project' },
  PROJECT_ASSIGN: { key: 'success', icon: PersonAddIcon, label: 'Assign Employee' },
  PROJECT_UNASSIGN: { key: 'warning', icon: PersonAddIcon, label: 'Unassign Employee' },
  PROFILE_UPDATE: { key: 'secondary', icon: PersonIcon, label: 'Profile Update' },
}

export default function AdminRequests() {
  const [loading, setLoading] = useState(true)
  const [requests, setRequests] = useState([])
  const [expandedId, setExpandedId] = useState(null)
  const [rejectDialog, setRejectDialog] = useState({ open: false, id: null, comments: '' })
  const [processing, setProcessing] = useState(null)
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    load()
  }, [])

  const load = async () => {
    setLoading(true)
    try {
      const data = await adminRequestService.getPending()
      setRequests(data || [])
    } catch (e) {
      enqueueSnackbar('Error loading requests', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const approve = async (id) => {
    setProcessing(id)
    try {
      await adminRequestService.approve(id)
      enqueueSnackbar('Request approved and executed successfully', { variant: 'success' })
      load()
    } catch (e) {
      enqueueSnackbar(e.response?.data?.message || 'Error approving request', { variant: 'error' })
    } finally {
      setProcessing(null)
    }
  }

  const reject = async () => {
    setProcessing(rejectDialog.id)
    try {
      await adminRequestService.reject(rejectDialog.id, rejectDialog.comments || null)
      enqueueSnackbar('Request rejected', { variant: 'info' })
      setRejectDialog({ open: false, id: null, comments: '' })
      load()
    } catch (e) {
      enqueueSnackbar('Error rejecting request', { variant: 'error' })
    } finally {
      setProcessing(null)
    }
  }

  const parsePayload = (payloadJson) => {
    try {
      return JSON.parse(payloadJson)
    } catch {
      return null
    }
  }

  const renderPayloadDetails = (request) => {
    const payload = parsePayload(request.payloadJson)
    if (!payload) return <Typography color="text.secondary">Unable to parse request details</Typography>

    const type = request.type

    if (type.startsWith('USER_')) {
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {payload.user && (
            <>
              <DetailRow label="Name" value={`${payload.user.firstName || ''} ${payload.user.lastName || ''}`} />
              <DetailRow label="Email" value={payload.user.email} />
              <DetailRow label="Role" value={payload.user.role} />
              {payload.user.monthlySalary && <DetailRow label="Monthly Salary" value={`₹${payload.user.monthlySalary}`} />}
            </>
          )}
          {payload.id && <DetailRow label="Target User ID" value={payload.id} />}
        </Box>
      )
    }

    if (type.startsWith('PROJECT_') && !type.includes('ASSIGN')) {
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {payload.project && (
            <>
              <DetailRow label="Project Code" value={payload.project.code} />
              <DetailRow label="Project Name" value={payload.project.name} />
              <DetailRow label="Description" value={payload.project.description || 'N/A'} />
              <DetailRow label="Status" value={payload.project.status} />
              <DetailRow label="Start Date" value={payload.project.startDate} />
              <DetailRow label="End Date" value={payload.project.endDate || 'N/A'} />
              {payload.project.managerId && <DetailRow label="Manager ID" value={payload.project.managerId} />}
            </>
          )}
          {payload.id && <DetailRow label="Target Project ID" value={payload.id} />}
        </Box>
      )
    }

    if (type.includes('ASSIGN')) {
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          <DetailRow label="Employee ID" value={payload.userId} />
          <DetailRow label="Project ID" value={payload.projectId} />
          {payload.role && <DetailRow label="Role in Project" value={payload.role} />}
        </Box>
      )
    }

    if (type === 'PROFILE_UPDATE') {
      return (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {payload.firstName && <DetailRow label="First Name" value={payload.firstName} />}
          {payload.lastName && <DetailRow label="Last Name" value={payload.lastName} />}
          {payload.phoneNumber && <DetailRow label="Phone Number" value={payload.phoneNumber} />}
          {payload.address && <DetailRow label="Address" value={payload.address} />}
        </Box>
      )
    }

    return <Typography color="text.secondary">No additional details</Typography>
  }

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: pageHeaderSx.mb }}>
          <AssignmentIcon sx={{ fontSize: 38, color: 'primary.main' }} />
          <Box>
            <Typography variant="h4" component="h1" sx={pageTitleSx}>
              Admin requests
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Approve create and update requests.
            </Typography>
          </Box>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
            <CircularProgress />
          </Box>
        ) : requests.length === 0 ? (
          <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
            <CardContent sx={{ textAlign: 'center', py: 6 }}>
              <AssignmentIcon sx={{ fontSize: 48, opacity: 0.3, mb: 2 }} />
              <Typography color="text.secondary">No pending requests.</Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Nothing waiting for approval.
              </Typography>
            </CardContent>
          </Card>
        ) : (
          <TableContainer component={Paper} sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Type
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Requested By
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Created
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Status
                  </TableCell>
                  <TableCell align="center" sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Details
                  </TableCell>
                  <TableCell align="center" sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                    Actions
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {requests.map((r) => {
                  const meta = typeMeta[r.type] || { key: 'secondary', icon: AssignmentIcon, label: r.type }
                  const typeTone = paletteTone(theme, meta.key)
                  const TypeIcon = meta.icon || AssignmentIcon
                  const statusTone = paletteTone(theme, statusToneKey[r.status] || 'secondary')
                  const isExpanded = expandedId === r.id
                  const isProcessing = processing === r.id

                  return (
                    <React.Fragment key={r.id}>
                      <TableRow 
                        hover
                        sx={{
                          '&:hover': {
                            backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                          },
                        }}
                      >
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Box
                              sx={{
                                width: 32,
                                height: 32,
                                borderRadius: 1,
                                backgroundColor: typeTone.bg,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                              }}
                            >
                              <TypeIcon sx={{ fontSize: 18, color: typeTone.color }} />
                            </Box>
                            <Typography variant="body2" sx={{ fontWeight: 500 }}>
                              {meta.label}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {r.requestedBy?.firstName} {r.requestedBy?.lastName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {r.requestedBy?.email}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2">
                            {r.createdAt ? format(new Date(r.createdAt), 'MMM dd, yyyy') : '-'}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {r.createdAt ? format(new Date(r.createdAt), 'HH:mm') : ''}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={r.status}
                            size="small"
                            sx={{
                              backgroundColor: statusTone.bg,
                              color: statusTone.color,
                              fontWeight: 600,
                            }}
                          />
                        </TableCell>
                        <TableCell align="center">
                          <Tooltip title={isExpanded ? "Hide Details" : "Show Details"}>
                            <IconButton
                              size="small"
                              onClick={() => setExpandedId(isExpanded ? null : r.id)}
                            >
                              {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                        <TableCell align="center">
                          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                            <Tooltip title="Approve">
                              <span>
                                <IconButton 
                                  color="success" 
                                  onClick={() => approve(r.id)} 
                                  disabled={isProcessing}
                                  sx={actionIconSx(theme, 'success')}
                                >
                                  {isProcessing ? <CircularProgress size={20} /> : <CheckIcon />}
                                </IconButton>
                              </span>
                            </Tooltip>
                            <Tooltip title="Reject">
                              <span>
                                <IconButton
                                  color="error"
                                  onClick={() => setRejectDialog({ open: true, id: r.id, comments: '' })}
                                  disabled={isProcessing}
                                  sx={actionIconSx(theme, 'error')}
                                >
                                  <CloseIcon />
                                </IconButton>
                              </span>
                            </Tooltip>
                          </Box>
                        </TableCell>
                      </TableRow>
                      <TableRow>
                        <TableCell colSpan={6} sx={{ py: 0, borderBottom: isExpanded ? undefined : 'none' }}>
                          <Collapse in={isExpanded} timeout="auto" unmountOnExit>
                            <Box sx={{ 
                              py: 2, 
                              px: 2, 
                              backgroundColor: isDark ? 'rgba(0, 0, 0, 0.2)' : 'rgba(0, 0, 0, 0.02)',
                              borderRadius: 2,
                              my: 1,
                            }}>
                              <Typography variant="subtitle2" sx={{ mb: 2, fontWeight: 600 }}>
                                Request Details
                              </Typography>
                              {renderPayloadDetails(r)}
                            </Box>
                          </Collapse>
                        </TableCell>
                      </TableRow>
                    </React.Fragment>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </motion.div>

      {/* Reject Dialog */}
      <Dialog 
        open={rejectDialog.open} 
        onClose={() => setRejectDialog({ open: false, id: null, comments: '' })}
        maxWidth="sm"
        fullWidth
        PaperProps={{
          sx: {
            ...getSurfaceStyles(isDark, 'navigation'),
            borderRadius: 4,
          },
        }}
      >
        <DialogTitle sx={{ fontWeight: 600 }}>Reject Request</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            This action cannot be undone. The requester will be notified of the rejection.
          </Alert>
          <TextField
            label="Reason for Rejection"
            multiline
            minRows={3}
            fullWidth
            value={rejectDialog.comments}
            onChange={(e) => setRejectDialog({ ...rejectDialog, comments: e.target.value })}
            placeholder="Provide a reason for rejecting this request (optional but recommended)"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialog({ open: false, id: null, comments: '' })}>Cancel</Button>
          <Button onClick={reject} color="error" variant="contained" disabled={processing === rejectDialog.id}>
            {processing === rejectDialog.id ? <CircularProgress size={20} /> : 'Reject'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

// Helper component for displaying details
function DetailRow({ label, value }) {
  if (!value) return null
  return (
    <Box sx={{ display: 'flex', gap: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ minWidth: 120 }}>
        {label}:
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 500 }}>
        {value}
      </Typography>
    </Box>
  )
}
