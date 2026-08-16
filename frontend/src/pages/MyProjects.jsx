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
  alpha,
  Avatar,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  LinearProgress,
} from '@mui/material'
import {
  Folder as FolderIcon,
  Work as WorkIcon,
  Person as PersonIcon,
  CalendarToday as CalendarIcon,
  Assignment as AssignmentIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { projectAssignmentService } from '../services/projectAssignmentService'
import { useSnackbar } from 'notistack'
import { format, differenceInDays, isAfter, isBefore } from 'date-fns'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'

const statusColors = {
  ACTIVE: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  INACTIVE: { color: 'text.secondary', bg: 'rgba(142, 142, 147, 0.15)' },
  COMPLETED: { color: 'primary.main', bg: 'rgba(79, 122, 92, 0.12)' },
  ON_HOLD: { color: 'warning.main', bg: 'rgba(184, 137, 74, 0.15)' },
}

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.05,
      delayChildren: 0.02,
    },
  },
}

const itemVariants = {
  hidden: { opacity: 0, y: 8 },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.25,
      ease: [0.25, 0.1, 0.25, 1],
    },
  },
}

export default function MyProjects() {
  const [loading, setLoading] = useState(true)
  const [assignments, setAssignments] = useState([])
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    setLoading(true)
    try {
      // Get assignments for current user (user.userId comes from AuthResponse)
      const data = await projectAssignmentService.getByUser(user.userId)
      setAssignments(data || [])
    } catch (error) {
      enqueueSnackbar('Error loading project assignments', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const activeAssignments = assignments.filter(a => a.isActive && a.projectStatus === 'ACTIVE')
  const completedAssignments = assignments.filter(a => !a.isActive || a.projectStatus === 'COMPLETED')

  // Calculate project progress (if end date exists)
  const getProjectProgress = (startDate, endDate) => {
    if (!startDate || !endDate) return null
    const start = new Date(startDate)
    const end = new Date(endDate)
    const today = new Date()
    
    if (isBefore(today, start)) return 0
    if (isAfter(today, end)) return 100
    
    const totalDays = differenceInDays(end, start)
    const elapsedDays = differenceInDays(today, start)
    return Math.round((elapsedDays / totalDays) * 100)
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box sx={{ p: { xs: 2, sm: 3, md: 4 } }}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 4 }}>
          <FolderIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
          <Box>
            <Typography
              variant="h4"
              component="h1"
              sx={{
              fontWeight: 600,
              fontSize: { xs: '1.75rem', md: '2rem' },
              mb: 0.5,
              color: 'text.primary',
            }}
            >
              My projects
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Assignments you are on right now.
            </Typography>
          </Box>
        </Box>

        {/* Summary Cards */}
        <motion.div variants={containerVariants} initial="hidden" animate="visible">
          <Grid container spacing={3} sx={{ mb: 4 }}>
            {/* Active Projects */}
            <Grid item xs={12} sm={6} md={4}>
              <motion.div variants={itemVariants}>
                <Card sx={{ ...getSurfaceStyles(isDark, 'card'), height: '100%' }}>
                  <CardContent sx={{ p: 3 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                      <Box
                        sx={{
                          width: 48,
                          height: 48,
                          borderRadius: 2,
                          backgroundColor: 'primary.main',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <WorkIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          Active Projects
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                          {activeAssignments.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Currently working on
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* Total Assignments */}
            <Grid item xs={12} sm={6} md={4}>
              <motion.div variants={itemVariants}>
                <Card sx={{ ...getSurfaceStyles(isDark, 'card'), height: '100%' }}>
                  <CardContent sx={{ p: 3 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                      <Box
                        sx={{
                          width: 48,
                          height: 48,
                          borderRadius: 2,
                          backgroundColor: 'primary.main',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <AssignmentIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          Total Assignments
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                          {assignments.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      All time
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* Completed */}
            <Grid item xs={12} sm={6} md={4}>
              <motion.div variants={itemVariants}>
                <Card sx={{ ...getSurfaceStyles(isDark, 'card'), height: '100%' }}>
                  <CardContent sx={{ p: 3 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
                      <Box
                        sx={{
                          width: 48,
                          height: 48,
                          borderRadius: 2,
                          backgroundColor: 'primary.main',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <FolderIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          Completed
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                          {completedAssignments.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Past projects
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>
          </Grid>
        </motion.div>

        {/* Active Projects List */}
        <motion.div variants={itemVariants} initial="hidden" animate="visible">
          <Typography
            variant="h5"
            sx={{
              fontWeight: 600,
              mb: 2,
              display: 'flex',
              alignItems: 'center',
              gap: 1,
            }}
          >
            <WorkIcon sx={{ color: 'primary.main' }} />
            Current Projects
          </Typography>

          {activeAssignments.length === 0 ? (
            <Card sx={{ ...getSurfaceStyles(isDark, 'navigation'), mb: 4 }}>
              <CardContent sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  You are not assigned to any active projects yet.
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1, opacity: 0.7 }}>
                  Contact your manager or admin to get assigned to a project.
                </Typography>
              </CardContent>
            </Card>
          ) : (
            <Grid container spacing={3} sx={{ mb: 4 }}>
              {activeAssignments.map((assignment) => {
                const progress = getProjectProgress(assignment.projectStartDate, assignment.projectEndDate)
                return (
                  <Grid item xs={12} md={6} key={assignment.id}>
                    <motion.div variants={itemVariants}>
                      <Card sx={{ ...getSurfaceStyles(isDark, 'card'), height: '100%' }}>
                        <CardContent sx={{ p: 3 }}>
                          <Box sx={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', mb: 2 }}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                              <Box
                                sx={{
                                  width: 48,
                                  height: 48,
                                  borderRadius: 2,
                                  backgroundColor: 'primary.main',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                }}
                              >
                                <FolderIcon sx={{ color: 'white', fontSize: 24 }} />
                              </Box>
                              <Box>
                                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                                  {assignment.projectName}
                                </Typography>
                                <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                                  {assignment.projectCode}
                                </Typography>
                              </Box>
                            </Box>
                            <Chip
                              label={assignment.projectStatus}
                              size="small"
                              sx={{
                                backgroundColor: statusColors[assignment.projectStatus]?.bg || 'rgba(0, 0, 0, 0.1)',
                                color: statusColors[assignment.projectStatus]?.color || 'text.primary',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                              }}
                            />
                          </Box>

                          {/* Role */}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                            <PersonIcon sx={{ fontSize: 18, opacity: 0.6 }} />
                            <Typography variant="body2" color="text.secondary">
                              Your Role:
                            </Typography>
                            <Chip
                              label={assignment.role}
                              size="small"
                              sx={{
                                backgroundColor: 'rgba(79, 122, 92, 0.12)',
                                color: 'primary.main',
                                fontWeight: 600,
                                fontSize: '0.75rem',
                              }}
                            />
                          </Box>

                          {/* Dates */}
                          <Box sx={{ display: 'flex', gap: 3, mb: 2 }}>
                            <Box>
                              <Typography variant="caption" color="text.secondary">Start Date</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                {assignment.projectStartDate 
                                  ? format(new Date(assignment.projectStartDate), 'MMM dd, yyyy') 
                                  : '-'}
                              </Typography>
                            </Box>
                            <Box>
                              <Typography variant="caption" color="text.secondary">End Date</Typography>
                              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                {assignment.projectEndDate 
                                  ? format(new Date(assignment.projectEndDate), 'MMM dd, yyyy') 
                                  : 'Ongoing'}
                              </Typography>
                            </Box>
                          </Box>

                          {/* Assigned Date */}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                            <CalendarIcon sx={{ fontSize: 16, opacity: 0.5 }} />
                            <Typography variant="caption" color="text.secondary">
                              Assigned on {assignment.assignedDate 
                                ? format(new Date(assignment.assignedDate), 'MMM dd, yyyy') 
                                : '-'}
                            </Typography>
                          </Box>

                          {/* Progress Bar (if dates exist) */}
                          {progress !== null && (
                            <Box sx={{ mt: 2 }}>
                              <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                <Typography variant="caption" color="text.secondary">
                                  Project Timeline
                                </Typography>
                                <Typography variant="caption" sx={{ fontWeight: 600, color: 'text.secondary' }}>
                                  {progress}%
                                </Typography>
                              </Box>
                              <LinearProgress
                                variant="determinate"
                                value={progress}
                                sx={{
                                  height: 6,
                                  borderRadius: 3,
                                  backgroundColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)',
                                  '& .MuiLinearProgress-bar': {
                                    borderRadius: 3,
                                    backgroundColor: 'primary.main',
                                  },
                                }}
                              />
                            </Box>
                          )}

                          {/* Manager */}
                          {assignment.managerName && (
                            <Box sx={{ 
                              display: 'flex', 
                              alignItems: 'center', 
                              gap: 1.5, 
                              mt: 2,
                              pt: 2,
                              borderTop: '1px solid',
                              borderColor: isDark ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.08)',
                            }}>
                              <Avatar sx={{ width: 28, height: 28, bgcolor: 'primary.main', fontSize: '0.75rem' }}>
                                {assignment.managerName?.split(' ').map(n => n[0]).join('')}
                              </Avatar>
                              <Box>
                                <Typography variant="caption" color="text.secondary">Project Manager</Typography>
                                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                  {assignment.managerName}
                                </Typography>
                              </Box>
                            </Box>
                          )}
                        </CardContent>
                      </Card>
                    </motion.div>
                  </Grid>
                )
              })}
            </Grid>
          )}
        </motion.div>

        {/* Past Projects Table */}
        {completedAssignments.length > 0 && (
          <motion.div variants={itemVariants} initial="hidden" animate="visible">
            <Typography
              variant="h5"
              sx={{
                fontWeight: 600,
                mb: 2,
                display: 'flex',
                alignItems: 'center',
                gap: 1,
              }}
            >
              <FolderIcon sx={{ color: 'primary.main' }} />
              Past Projects
            </Typography>

            <TableContainer component={Paper} sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Project
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Role
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Duration
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Status
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {completedAssignments.map((assignment) => (
                    <TableRow
                      key={assignment.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2 }}>
                        <Box>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {assignment.projectName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                            {assignment.projectCode}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{assignment.role}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ opacity: 0.8 }}>
                          {assignment.assignedDate 
                            ? format(new Date(assignment.assignedDate), 'MMM yyyy')
                            : '-'}
                          {assignment.unassignedDate && (
                            <> - {format(new Date(assignment.unassignedDate), 'MMM yyyy')}</>
                          )}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={assignment.isActive ? 'Unassigned' : 'Completed'}
                          size="small"
                          sx={{
                            backgroundColor: 'rgba(79, 122, 92, 0.12)',
                            color: 'primary.main',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                          }}
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </motion.div>
        )}
      </motion.div>
    </Box>
  )
}
