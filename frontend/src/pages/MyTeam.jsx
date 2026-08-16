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
  Divider,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  Groups as GroupsIcon,
  Folder as FolderIcon,
  Person as PersonIcon,
  ExpandMore as ExpandMoreIcon,
  Work as WorkIcon,
  Weekend as BenchIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { projectService } from '../services/projectService'
import { projectAssignmentService } from '../services/projectAssignmentService'
import { userManagementService } from '../services/userManagementService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
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

export default function MyTeam() {
  const [loading, setLoading] = useState(true)
  const [myProjects, setMyProjects] = useState([])
  const [myTeam, setMyTeam] = useState([])
  const [projectAssignments, setProjectAssignments] = useState({})
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
      // Fetch projects managed by current user
      const projects = await projectService.getMyProjects()
      setMyProjects(projects || [])

      // Fetch team members who report to current user
      const team = await userManagementService.getMyTeam()
      setMyTeam(team || [])

      // Fetch assignments for each project
      const assignmentsMap = {}
      for (const project of projects || []) {
        try {
          const assignments = await projectAssignmentService.getByProject(project.id)
          assignmentsMap[project.id] = assignments || []
        } catch (e) {
          assignmentsMap[project.id] = []
        }
      }
      setProjectAssignments(assignmentsMap)
    } catch (error) {
      enqueueSnackbar('Error loading team data', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const activeProjects = myProjects.filter(p => p.status === 'ACTIVE')
  const teamOnBench = myTeam.filter(m => m.isOnBench)
  const teamWorking = myTeam.filter(m => !m.isOnBench)

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
          <GroupsIcon sx={{ fontSize: 40, color: 'primary.main' }} />
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
              My team
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Projects you own and the people on them.
            </Typography>
          </Box>
        </Box>

        {/* Summary Cards */}
        <motion.div variants={containerVariants} initial="hidden" animate="visible">
          <Grid container spacing={3} sx={{ mb: 4 }}>
            {/* Projects Summary */}
            <Grid item xs={12} sm={6} md={3}>
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
                          My Projects
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'text.secondary' }}>
                          {myProjects.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      {activeProjects.length} active
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* Team Size */}
            <Grid item xs={12} sm={6} md={3}>
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
                        <PersonIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          Team Members
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                          {myTeam.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Assigned to your projects
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* Working */}
            <Grid item xs={12} sm={6} md={3}>
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
                          On Projects
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                          {teamWorking.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Currently assigned
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* On Bench */}
            <Grid item xs={12} sm={6} md={3}>
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
                        <BenchIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          On Bench
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'warning.main' }}>
                          {teamOnBench.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Available for assignment
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>
          </Grid>
        </motion.div>

        {/* Projects Section */}
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
            <FolderIcon sx={{ color: 'text.secondary' }} />
            Projects I Manage
          </Typography>

          {myProjects.length === 0 ? (
            <Card sx={{ ...getSurfaceStyles(isDark, 'navigation'), mb: 4 }}>
              <CardContent sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  You don't have any projects assigned to you yet.
                </Typography>
              </CardContent>
            </Card>
          ) : (
            <Box sx={{ mb: 4 }}>
              {myProjects.map((project) => (
                <Accordion
                  key={project.id}
                  sx={{
                    ...getSurfaceStyles(isDark, 'navigation'),
                    mb: 2,
                    '&:before': { display: 'none' },
                    borderRadius: '16px !important',
                    overflow: 'hidden',
                  }}
                >
                  <AccordionSummary
                    expandIcon={<ExpandMoreIcon />}
                    sx={{ px: 3, py: 1 }}
                  >
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flex: 1 }}>
                      <Box
                        sx={{
                          width: 40,
                          height: 40,
                          borderRadius: 2,
                          backgroundColor: 'primary.main',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <FolderIcon sx={{ color: 'white', fontSize: 20 }} />
                      </Box>
                      <Box sx={{ flex: 1 }}>
                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                          {project.name}
                        </Typography>
                        <Typography variant="body2" color="text.secondary" sx={{ fontFamily: 'monospace' }}>
                          {project.code}
                        </Typography>
                      </Box>
                      <Chip
                        label={project.status}
                        size="small"
                        sx={{
                          backgroundColor: statusColors[project.status]?.bg || 'rgba(0, 0, 0, 0.1)',
                          color: statusColors[project.status]?.color || 'text.primary',
                          fontWeight: 600,
                          fontSize: '0.75rem',
                        }}
                      />
                      <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
                        {projectAssignments[project.id]?.length || 0} members
                      </Typography>
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails sx={{ px: 3, pb: 3 }}>
                    <Divider sx={{ mb: 2 }} />
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                      {project.description || 'No description'}
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 4, mb: 2 }}>
                      <Box>
                        <Typography variant="caption" color="text.secondary">Start Date</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {project.startDate ? format(new Date(project.startDate), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </Box>
                      <Box>
                        <Typography variant="caption" color="text.secondary">End Date</Typography>
                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                          {project.endDate ? format(new Date(project.endDate), 'MMM dd, yyyy') : '-'}
                        </Typography>
                      </Box>
                    </Box>

                    {/* Team Members on this Project */}
                    <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1, mt: 2 }}>
                      Team Members
                    </Typography>
                    {(!projectAssignments[project.id] || projectAssignments[project.id].length === 0) ? (
                      <Typography variant="body2" color="text.secondary">
                        No team members assigned yet
                      </Typography>
                    ) : (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                        {projectAssignments[project.id].map((assignment) => (
                          <Chip
                            key={assignment.id}
                            avatar={<Avatar sx={{ bgcolor: 'primary.main' }}>{assignment.userName?.charAt(0)}</Avatar>}
                            label={`${assignment.userName} (${assignment.role})`}
                            variant="outlined"
                            sx={{ borderColor: alpha(theme.palette.primary.main, 0.3) }}
                          />
                        ))}
                      </Box>
                    )}
                  </AccordionDetails>
                </Accordion>
              ))}
            </Box>
          )}
        </motion.div>

        {/* Team Members Section */}
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
            <PersonIcon sx={{ color: 'primary.main' }} />
            My Team Members
          </Typography>

          {myTeam.length === 0 ? (
            <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
              <CardContent sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  No employees are assigned to your projects yet.
                </Typography>
              </CardContent>
            </Card>
          ) : (
            <TableContainer component={Paper} sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Employee
                    </TableCell>
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Email
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Status
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {myTeam.map((member) => (
                    <TableRow
                      key={member.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36 }}>
                            {member.firstName?.charAt(0)}{member.lastName?.charAt(0)}
                          </Avatar>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {member.firstName} {member.lastName}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ opacity: 0.8 }}>
                          {member.email}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={member.isOnBench ? 'On Bench' : 'On Project'}
                          size="small"
                          sx={{
                            backgroundColor: member.isOnBench
                              ? 'rgba(184, 137, 74, 0.15)'
                              : 'rgba(79, 122, 92, 0.12)',
                            color: member.isOnBench ? theme.palette.warning.main : theme.palette.primary.main,
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
          )}
        </motion.div>
      </motion.div>
    </Box>
  )
}
