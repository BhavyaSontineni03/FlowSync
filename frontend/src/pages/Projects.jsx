import React, { useState, useEffect } from 'react'
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
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
} from '@mui/material'
import {
  Add as AddIcon,
  Edit as EditIcon,
  Folder as FolderIcon,
  People as PeopleIcon,
  Delete as DeleteIcon,
  PersonAdd as PersonAddIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { projectService } from '../services/projectService'
import { projectAssignmentService } from '../services/projectAssignmentService'
import { userManagementService } from '../services/userManagementService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { toCssColor, pageSx, pageHeaderSx, pageTitleSx, pageSubtitleSx, softRadius, actionIconSx } from '../utils/uiTokens'

const statusColors = {
  ACTIVE: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  INACTIVE: { color: 'text.secondary', bg: 'rgba(142, 142, 147, 0.15)' },
  COMPLETED: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  ON_HOLD: { color: 'warning.main', bg: 'rgba(184, 137, 74, 0.15)' },
}

export default function Projects() {
  const [projects, setProjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [dialog, setDialog] = useState({ open: false, mode: 'create', project: null })
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    startDate: format(new Date(), 'yyyy-MM-dd'),
    endDate: '',
    status: 'ACTIVE',
    managerId: '',
  })
  const [processing, setProcessing] = useState(false)
  const [assignDialog, setAssignDialog] = useState({ open: false, project: null })
  const [assignments, setAssignments] = useState({})
  const [allUsers, setAllUsers] = useState([])
  const [assignForm, setAssignForm] = useState({ userId: '', role: 'Team Member' })
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  
  // Only Admin can manage projects
  const isAdmin = user?.role === 'ADMIN'

  useEffect(() => {
    fetchProjects()
    if (isAdmin) {
      fetchAllUsers()
    }
  }, [page, isAdmin])

  const fetchAllUsers = async () => {
    try {
      const usersData = await userManagementService.getAllUsers()
      setAllUsers(usersData || [])
    } catch (error) {
      // Ignore if not accessible
    }
  }

  const fetchAssignments = async (projectId) => {
    try {
      const data = await projectAssignmentService.getByProject(projectId)
      setAssignments(prev => ({ ...prev, [projectId]: data }))
    } catch (error) {
      // Ignore
    }
  }

  const fetchProjects = async () => {
    setLoading(true)
    try {
      const response = await projectService.getAll(page, 20)
      setProjects(response.content || [])
      setTotalPages(response.totalPages || 0)
    } catch (error) {
      enqueueSnackbar('Error loading projects', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const handleOpenDialog = (mode, project = null) => {
    if (mode === 'edit' && project) {
      setFormData({
        code: project.code,
        name: project.name,
        description: project.description || '',
        startDate: project.startDate ? format(new Date(project.startDate), 'yyyy-MM-dd') : '',
        endDate: project.endDate ? format(new Date(project.endDate), 'yyyy-MM-dd') : '',
        status: project.status,
        managerId: project.managerId || '',
      })
    } else {
      setFormData({
        code: '',
        name: '',
        description: '',
        startDate: format(new Date(), 'yyyy-MM-dd'),
        endDate: '',
        status: 'ACTIVE',
        managerId: '',
      })
    }
    setDialog({ open: true, mode, project })
  }

  const handleDeleteProject = async (projectId) => {
    if (!window.confirm('Are you sure you want to delete this project?')) return
    
    setProcessing(true)
    try {
      await projectService.delete(projectId)
      enqueueSnackbar('Project deleted successfully', { variant: 'success' })
      fetchProjects()
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Error deleting project', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleSave = async () => {
    if (!formData.code || !formData.name || !formData.startDate) {
      enqueueSnackbar('Please fill all required fields (Code, Name, Start Date)', { variant: 'warning' })
      return
    }

    const payload = {
      ...formData,
      managerId: formData.managerId || null,
    }

    setProcessing(true)
    try {
      if (dialog.mode === 'create') {
        await projectService.create(payload)
        enqueueSnackbar('Project created successfully', { variant: 'success' })
      } else {
        await projectService.update(dialog.project.id, payload)
        enqueueSnackbar('Project updated successfully', { variant: 'success' })
      }
      setDialog({ open: false, mode: 'create', project: null })
      fetchProjects()
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Error saving project', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleAssign = async () => {
    if (!assignForm.userId) {
      enqueueSnackbar('Please select an employee', { variant: 'warning' })
      return
    }

    setProcessing(true)
    try {
      await projectAssignmentService.assignEmployee(assignForm.userId, assignDialog.project.id, assignForm.role)
      enqueueSnackbar('Employee assigned successfully', { variant: 'success' })
      setAssignForm({ userId: '', role: 'Team Member' })
      if (assignDialog.project) {
        fetchAssignments(assignDialog.project.id)
      }
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Error assigning employee', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleUnassign = async (assignment) => {
    if (!window.confirm(`Are you sure you want to unassign ${assignment.userName}?`)) return
    
    setProcessing(true)
    try {
      await projectAssignmentService.unassignEmployee(assignment.userId, assignment.projectId)
      enqueueSnackbar('Employee unassigned successfully', { variant: 'success' })
      if (assignDialog.project) {
        fetchAssignments(assignDialog.project.id)
      }
    } catch (error) {
      enqueueSnackbar('Error unassigning employee', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  // Filter users for manager selection (only MANAGER and ADMIN roles)
  const managers = allUsers.filter(u => u.role === 'MANAGER' || u.role === 'ADMIN')
  // Filter employees for assignment
  const employees = allUsers.filter(u => u.role === 'EMPLOYEE')

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <FolderIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
            <Box>
            <Typography
              variant="h4"
              component="h1"
              sx={pageTitleSx}
            >Projects</Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Create projects and assign people.
            </Typography>
          </Box>
          </Box>
          {isAdmin && (
            <Button
              variant="contained"
              startIcon={<AddIcon />}
              onClick={() => handleOpenDialog('create')}
              sx={{
                borderRadius: softRadius,
                px: 3,
                py: 1.5,
                backgroundColor: 'primary.main',
                fontWeight: 600,
                textTransform: 'none',
              }}
            >
              Create Project
            </Button>
          )}
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
            <CircularProgress />
          </Box>
        ) : projects.length === 0 ? (
          <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
            <CardContent sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary" sx={{ opacity: 0.6 }}>
                No projects found
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
                      Code
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Name
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Manager
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Start Date
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      End Date
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Status
                    </TableCell>
                    {isAdmin && (
                      <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                        Actions
                      </TableCell>
                    )}
                  </TableRow>
                </TableHead>
                <TableBody>
                  {projects.map((project) => (
                    <TableRow
                      key={project.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.9375rem', fontFamily: 'monospace' }}>
                          {project.code}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.9375rem' }}>
                          {project.name}
                        </Typography>
                        {project.description && (
                          <Typography variant="caption" sx={{ opacity: 0.6, display: 'block' }}>
                            {project.description.length > 50 ? project.description.substring(0, 50) + '...' : project.description}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {project.managerName || <span style={{ opacity: 0.5 }}>Not assigned</span>}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {project.startDate ? format(new Date(project.startDate), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {project.endDate ? format(new Date(project.endDate), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={project.status}
                          size="small"
                          sx={{
                            backgroundColor: statusColors[project.status]?.bg || 'rgba(0, 0, 0, 0.1)',
                            color: statusColors[project.status]?.color || 'text.primary',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            border: `1px solid ${alpha(toCssColor(theme, statusColors[project.status]?.color || '#000'), 0.3)}`,
                          }}
                        />
                      </TableCell>
                      {isAdmin && (
                        <TableCell align="center" sx={{ py: 2, px: 3 }}>
                          <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                            <Tooltip title="Manage Assignments">
                              <IconButton
                                size="small"
                                onClick={() => {
                                  setAssignDialog({ open: true, project })
                                  fetchAssignments(project.id)
                                }}
                                sx={actionIconSx(theme, 'success')}
                              >
                                <PeopleIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Edit Project">
                              <IconButton
                                size="small"
                                onClick={() => handleOpenDialog('edit', project)}
                                sx={actionIconSx(theme, 'primary')}
                              >
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Delete Project">
                              <IconButton
                                size="small"
                                onClick={() => handleDeleteProject(project.id)}
                                sx={actionIconSx(theme, 'error')}
                                disabled={processing}
                              >
                                <DeleteIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </Box>
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

        {/* Create/Edit Dialog - Admin Only */}
        <Dialog
          open={dialog.open}
          onClose={() => setDialog({ open: false, mode: 'create', project: null })}
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
            {dialog.mode === 'create' ? 'Create Project' : 'Edit Project'}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              <TextField
                label="Project Code *"
                value={formData.code}
                onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                fullWidth
                required
                placeholder="e.g., PROJ001"
                disabled={dialog.mode === 'edit'}
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <TextField
                label="Project Name *"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
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
              <FormControl fullWidth>
                <InputLabel>Project Manager</InputLabel>
                <Select
                  value={formData.managerId}
                  onChange={(e) => setFormData({ ...formData, managerId: e.target.value })}
                  label="Project Manager"
                >
                  <MenuItem value="">
                    <em>Not assigned</em>
                  </MenuItem>
                  {managers.map((mgr) => (
                    <MenuItem key={mgr.id} value={mgr.id}>
                      {mgr.firstName} {mgr.lastName} ({mgr.role})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Description"
                multiline
                rows={3}
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                fullWidth
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <Box sx={{ display: 'flex', gap: 2 }}>
                <TextField
                  label="Start Date *"
                  type="date"
                  value={formData.startDate}
                  onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
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
                  label="End Date"
                  type="date"
                  value={formData.endDate}
                  onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                  InputLabelProps={{ shrink: true }}
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
              <FormControl fullWidth>
                <InputLabel>Status</InputLabel>
                <Select
                  value={formData.status}
                  onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                  label="Status"
                >
                  <MenuItem value="ACTIVE">Active</MenuItem>
                  <MenuItem value="ON_HOLD">On Hold</MenuItem>
                  <MenuItem value="COMPLETED">Completed</MenuItem>
                  <MenuItem value="INACTIVE">Inactive</MenuItem>
                </Select>
              </FormControl>
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialog({ open: false, mode: 'create', project: null })}>Cancel</Button>
            <Button
              onClick={handleSave}
              variant="contained"
              disabled={processing}
              sx={{
                backgroundColor: 'primary.main',
              }}
            >
              {processing ? <CircularProgress size={24} /> : 'Save'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Assignment Dialog - Admin Only */}
        <Dialog
          open={assignDialog.open}
          onClose={() => setAssignDialog({ open: false, project: null })}
          maxWidth="md"
          fullWidth
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600, display: 'flex', alignItems: 'center', gap: 1 }}>
            <PersonAddIcon />
            Manage Assignments - {assignDialog.project?.name}
          </DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              <FormControl fullWidth>
                <InputLabel>Employee</InputLabel>
                <Select
                  value={assignForm.userId}
                  onChange={(e) => setAssignForm({ ...assignForm, userId: e.target.value })}
                  label="Employee"
                >
                  {employees.map((emp) => (
                    <MenuItem key={emp.id} value={emp.id}>
                      {emp.firstName} {emp.lastName} 
                      {emp.isOnBench && <Chip label="Bench" size="small" sx={{ ml: 1 }} color="warning" />}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Role in Project"
                value={assignForm.role}
                onChange={(e) => setAssignForm({ ...assignForm, role: e.target.value })}
                fullWidth
                placeholder="e.g., Developer, Lead, QA, Designer"
                sx={{
                  '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                    backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                    padding: '0 8px',
                    borderRadius: 1,
                  },
                }}
              />
              <Button
                onClick={handleAssign}
                variant="contained"
                disabled={processing || !assignForm.userId}
                startIcon={<PersonAddIcon />}
                sx={{
                  backgroundColor: 'primary.main',
                }}
              >
                {processing ? <CircularProgress size={24} /> : 'Assign Employee'}
              </Button>
            </Box>
            
            {/* Current Assignments */}
            {assignDialog.project && (
              <Box sx={{ mt: 4 }}>
                <Typography variant="subtitle1" sx={{ mb: 2, fontWeight: 600 }}>
                  Current Team ({assignments[assignDialog.project.id]?.length || 0} members)
                </Typography>
                {!assignments[assignDialog.project.id] || assignments[assignDialog.project.id].length === 0 ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
                    No employees assigned to this project yet
                  </Typography>
                ) : (
                  <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                    {assignments[assignDialog.project.id].map((assignment) => (
                      <Box 
                        key={assignment.id} 
                        sx={{ 
                          display: 'flex', 
                          justifyContent: 'space-between', 
                          alignItems: 'center', 
                          p: 1.5, 
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)', 
                          borderRadius: 2,
                          border: '1px solid',
                          borderColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.05)',
                        }}
                      >
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {assignment.userName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {assignment.role} • Assigned {assignment.assignedDate ? format(new Date(assignment.assignedDate), 'MMM dd, yyyy') : '-'}
                          </Typography>
                        </Box>
                        <Tooltip title="Unassign">
                          <IconButton
                            size="small"
                            onClick={() => handleUnassign(assignment)}
                            disabled={processing}
                            sx={{ color: 'error.main' }}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    ))}
                  </Box>
                )}
              </Box>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setAssignDialog({ open: false, project: null })}>Close</Button>
          </DialogActions>
        </Dialog>
      </motion.div>
    </Box>
  )
}
