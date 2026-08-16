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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  FormControlLabel,
  Switch,
  Button,
  IconButton,
  CircularProgress,
  useTheme,
  alpha,
} from '@mui/material'
import {
  People as PeopleIcon,
  Edit as EditIcon,
  AccountBalance as SalaryIcon,
  Add as AddIcon,
  ToggleOn as ToggleOnIcon,
  ToggleOff as ToggleOffIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { userManagementService } from '../services/userManagementService'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { pageSx, pageHeaderSx, pageTitleSx, pageSubtitleSx, actionIconSx, paletteTone } from '../utils/uiTokens'

const roleColors = {
  EMPLOYEE: { key: 'info' },
  MANAGER: { key: 'primary' },
  ADMIN: { key: 'secondary' },
  FINANCE: { key: 'warning' },
  HR: { key: 'success' },
}

export default function UserManagement() {
  const [users, setUsers] = useState([])
  const [allUsers, setAllUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [editDialog, setEditDialog] = useState({ open: false, user: null, type: null })
  const [formData, setFormData] = useState({
    managerId: '',
    hrId: '',
    monthlySalary: '',
  })
  const [createDialog, setCreateDialog] = useState(false)
  const [createForm, setCreateForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    role: 'EMPLOYEE',
    managerId: '',
    hrId: '',
    monthlySalary: '',
    isOnBench: false,
    enabled: true,
  })
  const [processing, setProcessing] = useState(false)
  const { enqueueSnackbar } = useSnackbar()
  const { user: currentUser } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  const buildPayload = (user, overrides = {}) => ({
    firstName: overrides.firstName ?? user.firstName,
    lastName: overrides.lastName ?? user.lastName,
    email: overrides.email ?? user.email,
    role: overrides.role ?? user.role,
    managerId: overrides.managerId ?? user.managerId ?? null,
    hrId: overrides.hrId ?? user.hrId ?? null,
    monthlySalary: overrides.monthlySalary ?? user.monthlySalary ?? null,
    isOnBench: overrides.isOnBench ?? user.isOnBench ?? false,
    enabled: overrides.enabled ?? user.enabled ?? true,
  })

  useEffect(() => {
    fetchUsers()
    fetchAllUsers()
  }, [page])

  const fetchUsers = async () => {
    setLoading(true)
    try {
      const response = await userManagementService.getAll(page, 20)
      setUsers(response.content || [])
      setTotalPages(response.totalPages || 0)
    } catch (error) {
      enqueueSnackbar('Error loading users', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const fetchAllUsers = async () => {
    try {
      const usersData = await userManagementService.getAllUsers()
      setAllUsers(usersData || [])
    } catch (error) {
      // Ignore if not accessible
    }
  }

  const handleOpenDialog = (user, type) => {
    setFormData({
      managerId: user.managerId || '',
      hrId: user.hrId || '',
      monthlySalary: user.monthlySalary || '',
    })
    setEditDialog({ open: true, user, type })
  }

  const handleSave = async () => {
    setProcessing(true)
    try {
      const { user, type } = editDialog
      if (type === 'manager') {
        await userManagementService.assignManager(user.id, formData.managerId || null)
        enqueueSnackbar('Manager assigned successfully', { variant: 'success' })
      } else if (type === 'hr') {
        await userManagementService.assignHR(user.id, formData.hrId || null)
        enqueueSnackbar('HR assigned successfully', { variant: 'success' })
      } else if (type === 'salary') {
        await userManagementService.updateSalary(user.id, formData.monthlySalary ? parseFloat(formData.monthlySalary) : null)
        enqueueSnackbar('Salary updated successfully', { variant: 'success' })
      }
      setEditDialog({ open: false, user: null, type: null })
      fetchUsers()
    } catch (error) {
      enqueueSnackbar('Error updating user', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleToggleEnabled = async (user) => {
    setProcessing(true)
    try {
      const payload = buildPayload(user, { enabled: !user.enabled })
      await userManagementService.updateUser(user.id, payload)
      enqueueSnackbar(`User ${payload.enabled ? 'enabled' : 'disabled'}`, { variant: 'success' })
      fetchUsers()
    } catch (error) {
      enqueueSnackbar('Error updating user status', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const handleOpenCreate = () => {
    setCreateForm({
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      role: 'EMPLOYEE',
      managerId: '',
      hrId: '',
      monthlySalary: '',
      isOnBench: false,
      enabled: true,
    })
    setCreateDialog(true)
  }

  const handleCreate = async () => {
    setProcessing(true)
    try {
      const payload = {
        ...createForm,
        managerId: createForm.managerId || null,
        hrId: createForm.hrId || null,
        monthlySalary: createForm.monthlySalary ? parseFloat(createForm.monthlySalary) : null,
      }
      await userManagementService.createUser(payload)
      enqueueSnackbar('User created successfully', { variant: 'success' })
      setCreateDialog(false)
      fetchUsers()
      fetchAllUsers()
    } catch (error) {
      enqueueSnackbar('Error creating user', { variant: 'error' })
    } finally {
      setProcessing(false)
    }
  }

  const managers = allUsers.filter(u => u.role === 'MANAGER' || u.role === 'ADMIN')
  const hrs = allUsers.filter(u => u.role === 'HR' || u.role === 'ADMIN')

  return (
    <Box sx={pageSx}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={pageHeaderSx}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: 1, minWidth: 160 }}>
            <PeopleIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
            <Box>
              <Typography variant="h4" component="h1" sx={pageTitleSx}>
                Users
              </Typography>
              <Typography variant="body2" sx={pageSubtitleSx}>
                Accounts, roles, and bench status.
              </Typography>
            </Box>
          </Box>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={handleOpenCreate}
          >
            Add User
          </Button>
        </Box>

        {loading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '400px' }}>
            <CircularProgress />
          </Box>
        ) : users.length === 0 ? (
          <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
            <CardContent sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary" sx={{ opacity: 0.6 }}>
                No users found
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
                      Name
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Email
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Role
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Manager
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      HR
                    </TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Salary
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Status
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Enabled
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, py: 2, px: 3, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Actions
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {users.map((user) => (
                    <TableRow
                      key={user.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 500, fontSize: '0.9375rem' }}>
                          {user.firstName} {user.lastName}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {user.email}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={user.role}
                          size="small"
                          sx={{
                            backgroundColor: paletteTone(theme, roleColors[user.role]?.key || 'secondary').bg,
                            color: paletteTone(theme, roleColors[user.role]?.key || 'secondary').color,
                            fontWeight: 600,
                            fontSize: '0.75rem',
                            border: `1px solid ${alpha(paletteTone(theme, roleColors[user.role]?.key || 'secondary').color, 0.3)}`,
                          }}
                        />
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {user.managerName || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontSize: '0.875rem' }}>
                          {user.hrName || '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="right" sx={{ py: 2, px: 3 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.9375rem' }}>
                          {user.monthlySalary ? `$${user.monthlySalary.toFixed(2)}` : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={user.isOnBench ? 'Bench' : 'Active'}
                          size="small"
                          sx={{
                            backgroundColor: user.isOnBench 
                              ? paletteTone(theme, 'warning').bg
                              : paletteTone(theme, 'success').bg,
                            color: user.isOnBench ? 'warning.main' : 'success.main',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                          }}
                        />
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Chip
                          label={user.enabled ? 'Enabled' : 'Disabled'}
                          size="small"
                          sx={{
                            backgroundColor: user.enabled
                              ? paletteTone(theme, 'success').bg
                              : paletteTone(theme, 'secondary').bg,
                            color: user.enabled ? 'success.main' : 'text.secondary',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                          }}
                        />
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
                          <IconButton
                            size="small"
                            onClick={() => handleOpenDialog(user, 'manager')}
                            sx={actionIconSx(theme, 'primary')}
                            title="Assign Manager"
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => handleOpenDialog(user, 'hr')}
                            sx={actionIconSx(theme, 'success')}
                            title="Assign HR"
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => handleOpenDialog(user, 'salary')}
                            sx={actionIconSx(theme, 'warning')}
                            title="Update Salary"
                          >
                            <SalaryIcon fontSize="small" />
                          </IconButton>
                          <IconButton
                            size="small"
                            onClick={() => handleToggleEnabled(user)}
                            sx={actionIconSx(theme, 'secondary')}
                            title={user.enabled ? 'Disable User' : 'Enable User'}
                            disabled={processing}
                          >
                            {user.enabled ? <ToggleOffIcon fontSize="small" /> : <ToggleOnIcon fontSize="small" />}
                          </IconButton>
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

        {/* Create Dialog */}
        <Dialog
          open={createDialog}
          onClose={() => setCreateDialog(false)}
          maxWidth="sm"
          fullWidth
          PaperProps={{
            sx: {
              ...getSurfaceStyles(isDark, 'navigation'),
              borderRadius: 4,
            },
          }}
        >
          <DialogTitle sx={{ fontWeight: 600 }}>Add User</DialogTitle>
          <DialogContent>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2, mt: 1 }}>
              <TextField
                label="First Name"
                value={createForm.firstName}
                onChange={(e) => setCreateForm({ ...createForm, firstName: e.target.value })}
                fullWidth
              />
              <TextField
                label="Last Name"
                value={createForm.lastName}
                onChange={(e) => setCreateForm({ ...createForm, lastName: e.target.value })}
                fullWidth
              />
              <TextField
                label="Email"
                type="email"
                value={createForm.email}
                onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
                fullWidth
              />
              <TextField
                label="Password"
                type="password"
                value={createForm.password}
                onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                fullWidth
              />
              <FormControl fullWidth>
                <InputLabel>Role</InputLabel>
                <Select
                  value={createForm.role}
                  label="Role"
                  onChange={(e) => setCreateForm({ ...createForm, role: e.target.value })}
                >
                  <MenuItem value="EMPLOYEE">EMPLOYEE</MenuItem>
                  <MenuItem value="MANAGER">MANAGER</MenuItem>
                  <MenuItem value="HR">HR</MenuItem>
                  <MenuItem value="FINANCE">FINANCE</MenuItem>
                  <MenuItem value="ADMIN">ADMIN</MenuItem>
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel>Manager</InputLabel>
                <Select
                  value={createForm.managerId}
                  label="Manager"
                  onChange={(e) => setCreateForm({ ...createForm, managerId: e.target.value })}
                >
                  <MenuItem value="">None</MenuItem>
                  {managers.map((m) => (
                    <MenuItem key={m.id} value={m.id}>
                      {m.firstName} {m.lastName} ({m.role})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl fullWidth>
                <InputLabel>HR</InputLabel>
                <Select
                  value={createForm.hrId}
                  label="HR"
                  onChange={(e) => setCreateForm({ ...createForm, hrId: e.target.value })}
                >
                  <MenuItem value="">None</MenuItem>
                  {hrs.map((h) => (
                    <MenuItem key={h.id} value={h.id}>
                      {h.firstName} {h.lastName} ({h.role})
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                label="Monthly Salary"
                type="number"
                value={createForm.monthlySalary}
                onChange={(e) => setCreateForm({ ...createForm, monthlySalary: e.target.value })}
                fullWidth
                InputProps={{
                  startAdornment: <Typography sx={{ mr: 1 }}>$</Typography>,
                }}
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={createForm.isOnBench}
                    onChange={(e) => setCreateForm({ ...createForm, isOnBench: e.target.checked })}
                  />
                }
                label="On Bench"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={createForm.enabled}
                    onChange={(e) => setCreateForm({ ...createForm, enabled: e.target.checked })}
                  />
                }
                label="Enabled"
              />
            </Box>
          </DialogContent>
          <DialogActions sx={{ p: 3, pt: 1 }}>
            <Button onClick={() => setCreateDialog(false)} color="inherit">
              Cancel
            </Button>
            <Button onClick={handleCreate} variant="contained" disabled={processing}>
              {processing ? <CircularProgress size={20} /> : 'Create'}
            </Button>
          </DialogActions>
        </Dialog>

        {/* Edit Dialog */}
        <Dialog
          open={editDialog.open}
          onClose={() => setEditDialog({ open: false, user: null, type: null })}
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
            {editDialog.type === 'manager' && 'Assign Manager'}
            {editDialog.type === 'hr' && 'Assign HR'}
            {editDialog.type === 'salary' && 'Update Salary'}
          </DialogTitle>
          <DialogContent>
            {editDialog.user && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="body2" color="text.secondary">
                  User: {editDialog.user.firstName} {editDialog.user.lastName}
                </Typography>
              </Box>
            )}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5, mt: 1 }}>
              {editDialog.type === 'manager' && (
                <FormControl fullWidth>
                  <InputLabel>Manager</InputLabel>
                  <Select
                    value={formData.managerId}
                    onChange={(e) => setFormData({ ...formData, managerId: e.target.value })}
                    label="Manager"
                  >
                    <MenuItem value="">None</MenuItem>
                    {managers.map((m) => (
                      <MenuItem key={m.id} value={m.id}>
                        {m.firstName} {m.lastName} ({m.role})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}
              {editDialog.type === 'hr' && (
                <FormControl fullWidth>
                  <InputLabel>HR</InputLabel>
                  <Select
                    value={formData.hrId}
                    onChange={(e) => setFormData({ ...formData, hrId: e.target.value })}
                    label="HR"
                  >
                    <MenuItem value="">None</MenuItem>
                    {hrs.map((h) => (
                      <MenuItem key={h.id} value={h.id}>
                        {h.firstName} {h.lastName} ({h.role})
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              )}
              {editDialog.type === 'salary' && (
                <TextField
                  label="Monthly Salary"
                  type="number"
                  value={formData.monthlySalary}
                  onChange={(e) => setFormData({ ...formData, monthlySalary: e.target.value })}
                  fullWidth
                  InputProps={{
                    startAdornment: <Typography sx={{ mr: 1 }}>$</Typography>,
                  }}
                  sx={{
                    '& .MuiInputLabel-root.MuiInputLabel-shrink': {
                      backgroundColor: isDark ? 'rgba(28, 28, 30, 0.12)' : 'rgba(255, 255, 255, 0.15)',
                      padding: '0 8px',
                      borderRadius: 1,
                    },
                  }}
                />
              )}
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEditDialog({ open: false, user: null, type: null })}>Cancel</Button>
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
      </motion.div>
    </Box>
  )
}
