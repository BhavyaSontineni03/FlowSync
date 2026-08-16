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
} from '@mui/material'
import {
  SupervisorAccount as HRIcon,
  Person as PersonIcon,
  Work as WorkIcon,
  Weekend as BenchIcon,
  Email as EmailIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { userManagementService } from '../services/userManagementService'
import { useSnackbar } from 'notistack'
import { useAuth } from '../contexts/AuthContext'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'

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

export default function MyEmployees() {
  const [loading, setLoading] = useState(true)
  const [employees, setEmployees] = useState([])
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
      const data = await userManagementService.getMyEmployees()
      setEmployees(data || [])
    } catch (error) {
      enqueueSnackbar('Error loading employees', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const employeesOnBench = employees.filter(e => e.isOnBench)
  const employeesWorking = employees.filter(e => !e.isOnBench)

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
          <HRIcon sx={{ fontSize: 40, color: 'error.main' }} />
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
              My employees
            </Typography>
            <Typography variant="body2" color="text.secondary">
              People under your HR support.
            </Typography>
          </Box>
        </Box>

        {/* Summary Cards */}
        <motion.div variants={containerVariants} initial="hidden" animate="visible">
          <Grid container spacing={3} sx={{ mb: 4 }}>
            {/* Total Employees */}
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
                        <PersonIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          Total Employees
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'error.main' }}>
                          {employees.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Under your HR support
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* Working */}
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
                          On Projects
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'primary.main' }}>
                          {employeesWorking.length}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography variant="body2" color="text.secondary">
                      Currently assigned to projects
                    </Typography>
                  </CardContent>
                </Card>
              </motion.div>
            </Grid>

            {/* On Bench */}
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
                        <BenchIcon sx={{ color: 'white', fontSize: 24 }} />
                      </Box>
                      <Box>
                        <Typography variant="body2" sx={{ opacity: 0.7, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                          On Bench
                        </Typography>
                        <Typography variant="h4" sx={{ fontWeight: 700, color: 'warning.main' }}>
                          {employeesOnBench.length}
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

        {/* Employees Table */}
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
            <PersonIcon sx={{ color: 'error.main' }} />
            Employee List
          </Typography>

          {employees.length === 0 ? (
            <Card sx={{ ...getSurfaceStyles(isDark, 'navigation') }}>
              <CardContent sx={{ p: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">
                  No employees are assigned to you for HR support yet.
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
                    <TableCell sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Role
                    </TableCell>
                    <TableCell align="center" sx={{ fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', opacity: 0.7 }}>
                      Project Status
                    </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {employees.map((employee) => (
                    <TableRow
                      key={employee.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                          <Avatar sx={{ bgcolor: 'error.main', width: 36, height: 36 }}>
                            {employee.firstName?.charAt(0)}{employee.lastName?.charAt(0)}
                          </Avatar>
                          <Typography variant="body2" sx={{ fontWeight: 500 }}>
                            {employee.firstName} {employee.lastName}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                          <EmailIcon sx={{ fontSize: 16, opacity: 0.5 }} />
                          <Typography variant="body2" sx={{ opacity: 0.8 }}>
                            {employee.email}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={employee.role}
                          size="small"
                          sx={{
                            backgroundColor: 'rgba(79, 122, 92, 0.12)',
                            color: 'primary.main',
                            fontWeight: 600,
                            fontSize: '0.75rem',
                          }}
                        />
                      </TableCell>
                      <TableCell align="center">
                        <Chip
                          label={employee.isOnBench ? 'On Bench' : 'On Project'}
                          size="small"
                          sx={{
                            backgroundColor: employee.isOnBench
                              ? 'rgba(184, 137, 74, 0.15)'
                              : 'rgba(79, 122, 92, 0.12)',
                            color: employee.isOnBench ? theme.palette.warning.main : theme.palette.primary.main,
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
