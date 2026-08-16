import React, { useState, useEffect } from 'react'
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  CircularProgress,
  useTheme,
  Avatar,
  TextField,
  Button,
  Divider,
} from '@mui/material'
import {
  Email as EmailIcon,
  Phone as PhoneIcon,
  Home as AddressIcon,
  Work as WorkIcon,
  SupervisorAccount as ManagerIcon,
  Support as HRIcon,
  Edit as EditIcon,
  Save as SaveIcon,
  Cancel as CancelIcon,
  Badge as BadgeIcon,
  Business as BusinessIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { userManagementService } from '../services/userManagementService'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { format } from 'date-fns'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  roleTone,
  paletteTone,
  iconWellSx,
} from '../utils/uiTokens'

const containerVariants = motionPresets.staggerContainer
const itemVariants = motionPresets.staggerItem

export default function Profile() {
  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState(null)
  const [isEditing, setIsEditing] = useState(false)
  const [editForm, setEditForm] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const warning = paletteTone(theme, 'warning')
  const success = paletteTone(theme, 'success')

  useEffect(() => {
    loadProfile()
  }, [])

  const loadProfile = async () => {
    setLoading(true)
    try {
      const data = await userManagementService.getMyProfile()
      setProfile(data)
      setEditForm({
        firstName: data.firstName || '',
        lastName: data.lastName || '',
        phoneNumber: data.phoneNumber || '',
        address: data.address || '',
      })
    } catch (error) {
      enqueueSnackbar('Error loading profile', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = () => setIsEditing(true)

  const handleCancel = () => {
    setIsEditing(false)
    setEditForm({
      firstName: profile?.firstName || '',
      lastName: profile?.lastName || '',
      phoneNumber: profile?.phoneNumber || '',
      address: profile?.address || '',
    })
  }

  const handleSubmit = async () => {
    const changes = {}
    if (editForm.firstName !== profile.firstName) changes.firstName = editForm.firstName
    if (editForm.lastName !== profile.lastName) changes.lastName = editForm.lastName
    if (editForm.phoneNumber !== (profile.phoneNumber || '')) changes.phoneNumber = editForm.phoneNumber
    if (editForm.address !== (profile.address || '')) changes.address = editForm.address

    if (Object.keys(changes).length === 0) {
      enqueueSnackbar('No changes to submit', { variant: 'info' })
      setIsEditing(false)
      return
    }

    setSubmitting(true)
    try {
      await userManagementService.requestProfileUpdate(changes)
      enqueueSnackbar('Profile update request submitted for admin approval', { variant: 'success' })
      setIsEditing(false)
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Error submitting update request', { variant: 'error' })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress color="primary" />
      </Box>
    )
  }

  if (!profile) {
    return (
      <Box sx={{ p: 4, textAlign: 'center' }}>
        <Typography color="text.secondary">Unable to load profile</Typography>
      </Box>
    )
  }

  const role = roleTone(theme, profile.role)

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
              Profile
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Contact details and reporting lines for your account.
            </Typography>
          </Box>

          {!isEditing ? (
            <Button variant="outlined" color="secondary" startIcon={<EditIcon />} onClick={handleEdit}>
              Edit profile
            </Button>
          ) : (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button
                variant="outlined"
                startIcon={<CancelIcon />}
                onClick={handleCancel}
                disabled={submitting}
              >
                Cancel
              </Button>
              <Button
                variant="contained"
                color="primary"
                startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : <SaveIcon />}
                onClick={handleSubmit}
                disabled={submitting}
              >
                Submit for approval
              </Button>
            </Box>
          )}
        </Box>

        <motion.div variants={containerVariants} initial="hidden" animate="show">
          <Grid container spacing={2.5}>
            <Grid item xs={12} md={4}>
              <motion.div variants={itemVariants}>
                <Card sx={{ ...getSurfaceStyles(isDark, 'card'), height: '100%' }}>
                  <CardContent sx={{ p: 3, textAlign: 'center' }}>
                    <Avatar
                      sx={{
                        width: 96,
                        height: 96,
                        fontSize: '2rem',
                        bgcolor: 'secondary.main',
                        mx: 'auto',
                        mb: 2,
                      }}
                    >
                      {profile.firstName?.charAt(0)}
                      {profile.lastName?.charAt(0)}
                    </Avatar>
                    <Typography variant="h5" sx={{ fontWeight: 600, mb: 0.5 }}>
                      {profile.firstName} {profile.lastName}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      {profile.email}
                    </Typography>
                    <Chip
                      label={profile.role}
                      sx={{
                        backgroundColor: role.bg,
                        color: role.color,
                        fontWeight: 600,
                      }}
                    />

                    <Divider sx={{ my: 3 }} />

                    <Box sx={{ textAlign: 'left' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                        <WorkIcon
                          sx={{
                            fontSize: 20,
                            color: profile.isOnBench ? warning.color : success.color,
                          }}
                        />
                        <Box>
                          <Typography variant="caption" color="text.secondary">
                            Status
                          </Typography>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {profile.isOnBench ? 'On bench' : 'On project'}
                          </Typography>
                        </Box>
                      </Box>

                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                        <BusinessIcon sx={{ fontSize: 20, color: 'primary.main' }} />
                        <Box>
                          <Typography variant="caption" color="text.secondary">
                            Member since
                          </Typography>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {profile.createdAt
                              ? format(new Date(profile.createdAt), 'MMMM yyyy')
                              : '-'}
                          </Typography>
                        </Box>
                      </Box>
                    </Box>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            <Grid item xs={12} md={8}>
              <motion.div variants={itemVariants}>
                <Card sx={{ ...getSurfaceStyles(isDark, 'card'), mb: 2.5 }}>
                  <CardContent sx={{ p: 3 }}>
                    <Typography
                      variant="h6"
                      sx={{ fontWeight: 600, mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}
                    >
                      <BadgeIcon sx={{ color: 'secondary.main' }} />
                      Personal details
                    </Typography>

                    <Grid container spacing={2.5}>
                      <Grid item xs={12} sm={6}>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          First name
                        </Typography>
                        {isEditing ? (
                          <TextField
                            fullWidth
                            size="small"
                            value={editForm.firstName}
                            onChange={(e) => setEditForm({ ...editForm, firstName: e.target.value })}
                          />
                        ) : (
                          <Typography variant="body1" sx={{ fontWeight: 500 }}>
                            {profile.firstName}
                          </Typography>
                        )}
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          Last name
                        </Typography>
                        {isEditing ? (
                          <TextField
                            fullWidth
                            size="small"
                            value={editForm.lastName}
                            onChange={(e) => setEditForm({ ...editForm, lastName: e.target.value })}
                          />
                        ) : (
                          <Typography variant="body1" sx={{ fontWeight: 500 }}>
                            {profile.lastName}
                          </Typography>
                        )}
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          Email
                        </Typography>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <EmailIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                          <Typography variant="body1" sx={{ fontWeight: 500 }}>
                            {profile.email}
                          </Typography>
                        </Box>
                        {isEditing && (
                          <Typography variant="caption" color="text.secondary">
                            Email cannot be changed here
                          </Typography>
                        )}
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          Phone
                        </Typography>
                        {isEditing ? (
                          <TextField
                            fullWidth
                            size="small"
                            value={editForm.phoneNumber}
                            onChange={(e) => setEditForm({ ...editForm, phoneNumber: e.target.value })}
                            placeholder="Phone number"
                          />
                        ) : (
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <PhoneIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
                            <Typography variant="body1" sx={{ fontWeight: 500 }}>
                              {profile.phoneNumber || (
                                <Box component="span" sx={{ opacity: 0.5 }}>
                                  Not provided
                                </Box>
                              )}
                            </Typography>
                          </Box>
                        )}
                      </Grid>

                      <Grid item xs={12}>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                          Address
                        </Typography>
                        {isEditing ? (
                          <TextField
                            fullWidth
                            size="small"
                            multiline
                            rows={2}
                            value={editForm.address}
                            onChange={(e) => setEditForm({ ...editForm, address: e.target.value })}
                            placeholder="Address"
                          />
                        ) : (
                          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1 }}>
                            <AddressIcon sx={{ fontSize: 18, color: 'text.secondary', mt: 0.3 }} />
                            <Typography variant="body1" sx={{ fontWeight: 500 }}>
                              {profile.address || (
                                <Box component="span" sx={{ opacity: 0.5 }}>
                                  Not provided
                                </Box>
                              )}
                            </Typography>
                          </Box>
                        )}
                      </Grid>
                    </Grid>

                    {isEditing && (
                      <Box
                        sx={{
                          mt: 3,
                          p: 2,
                          borderRadius: 2,
                          backgroundColor: warning.bg,
                          border: `1px solid ${warning.color}40`,
                        }}
                      >
                        <Typography variant="body2" sx={{ color: warning.color }}>
                          Profile edits need admin approval. You will be notified when processed.
                        </Typography>
                      </Box>
                    )}
                  </CardContent>
                </Card>
              </motion.div>

              <motion.div variants={itemVariants}>
                <Card sx={getSurfaceStyles(isDark, 'card')}>
                  <CardContent sx={{ p: 3 }}>
                    <Typography
                      variant="h6"
                      sx={{ fontWeight: 600, mb: 3, display: 'flex', alignItems: 'center', gap: 1 }}
                    >
                      <BusinessIcon sx={{ color: 'primary.main' }} />
                      Organization
                    </Typography>

                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6}>
                        <Box
                          sx={{
                            p: 2,
                            borderRadius: 2,
                            backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.03)',
                            border: '1px solid',
                            borderColor: 'divider',
                          }}
                        >
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Box sx={{ ...iconWellSx(theme, 'primary'), width: 40, height: 40 }}>
                              <ManagerIcon sx={{ fontSize: 20 }} />
                            </Box>
                            <Box>
                              <Typography variant="caption" color="text.secondary">
                                Manager
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 600 }}>
                                {profile.managerName || (
                                  <Box component="span" sx={{ opacity: 0.5 }}>
                                    Not assigned
                                  </Box>
                                )}
                              </Typography>
                            </Box>
                          </Box>
                          {profile.role === 'EMPLOYEE' && (
                            <Typography
                              variant="caption"
                              color="text.secondary"
                              sx={{ mt: 1, display: 'block' }}
                            >
                              Set by your project assignments
                            </Typography>
                          )}
                        </Box>
                      </Grid>

                      <Grid item xs={12} sm={6}>
                        <Box
                          sx={{
                            p: 2,
                            borderRadius: 2,
                            backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.03)',
                            border: '1px solid',
                            borderColor: 'divider',
                          }}
                        >
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                            <Box sx={{ ...iconWellSx(theme, 'warning'), width: 40, height: 40 }}>
                              <HRIcon sx={{ fontSize: 20 }} />
                            </Box>
                            <Box>
                              <Typography variant="caption" color="text.secondary">
                                HR contact
                              </Typography>
                              <Typography variant="body1" sx={{ fontWeight: 600 }}>
                                {profile.hrName || (
                                  <Box component="span" sx={{ opacity: 0.5 }}>
                                    Not assigned
                                  </Box>
                                )}
                              </Typography>
                            </Box>
                          </Box>
                        </Box>
                      </Grid>

                      <Grid item xs={12}>
                        <Box
                          sx={{
                            p: 2,
                            borderRadius: 2,
                            backgroundColor: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(30,41,59,0.03)',
                            border: '1px solid',
                            borderColor: 'divider',
                          }}
                        >
                          <Typography variant="caption" color="text.secondary">
                            Role
                          </Typography>
                          <Typography variant="body1" sx={{ fontWeight: 600 }}>
                            {profile.role}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, display: 'block' }}>
                            Only an administrator can change roles
                          </Typography>
                        </Box>
                      </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>
          </Grid>
        </motion.div>
      </motion.div>
    </Box>
  )
}
