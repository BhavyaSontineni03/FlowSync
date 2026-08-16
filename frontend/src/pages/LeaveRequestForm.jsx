import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box,
  Button,
  Card,
  CardContent,
  TextField,
  Typography,
  Grid,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  useTheme,
} from '@mui/material'
import { DatePicker } from '@mui/x-date-pickers/DatePicker'
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider'
import { AdapterDateFns } from '@mui/x-date-pickers/AdapterDateFns'
import { motion } from 'framer-motion'
import { leaveRequestService } from '../services/leaveRequestService'
import { useSnackbar } from 'notistack'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import { pageSx, pageTitleSx, pageSubtitleSx, sectionGap } from '../utils/uiTokens'

const leaveTypes = [
  'VACATION',
  'SICK_LEAVE',
  'PERSONAL_LEAVE',
  'UNPAID_LEAVE',
  'MATERNITY_LEAVE',
  'PATERNITY_LEAVE',
  'BEREAVEMENT',
  'OTHER',
]

const LeaveRequestForm = () => {
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  const [formData, setFormData] = useState({
    leaveType: 'VACATION',
    startDate: null,
    endDate: null,
    reason: '',
  })
  const [loading, setLoading] = useState(false)

  const handleChange = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()

    if (!formData.startDate || !formData.endDate) {
      enqueueSnackbar('Please select start and end dates', { variant: 'error' })
      return
    }

    if (formData.startDate > formData.endDate) {
      enqueueSnackbar('Start date must be before or equal to end date', { variant: 'error' })
      return
    }

    try {
      setLoading(true)
      await leaveRequestService.createLeaveRequest({
        leaveType: formData.leaveType,
        startDate: formData.startDate.toISOString().split('T')[0],
        endDate: formData.endDate.toISOString().split('T')[0],
        reason: formData.reason,
      })
      enqueueSnackbar('Leave request submitted successfully', { variant: 'success' })
      navigate('/leave-requests')
    } catch (error) {
      enqueueSnackbar(error.response?.data?.message || 'Failed to submit leave request', {
        variant: 'error',
      })
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box sx={{ ...pageSx, maxWidth: 800, mx: 'auto', width: '100%' }}>
      <motion.div
        initial={motionPresets.pageInitial}
        animate={motionPresets.pageAnimate}
        transition={motionPresets.pageTransition}
      >
        <Box sx={{ mb: sectionGap }}>
          <Typography variant="h4" component="h1" sx={pageTitleSx}>
            Request leave
          </Typography>
          <Typography variant="body2" sx={pageSubtitleSx}>
            Choose dates and a type. Your manager reviews next.
          </Typography>
        </Box>

        <Card sx={getSurfaceStyles(isDark, 'card')}>
          <CardContent sx={{ p: { xs: 2.5, sm: 3.5 } }}>
            <form onSubmit={handleSubmit}>
              <Grid container spacing={2.5}>
                <Grid item xs={12} sm={6}>
                  <FormControl fullWidth>
                    <InputLabel>Leave type</InputLabel>
                    <Select
                      value={formData.leaveType}
                      onChange={(e) => handleChange('leaveType', e.target.value)}
                      label="Leave type"
                    >
                      {leaveTypes.map((type) => (
                        <MenuItem key={type} value={type}>
                          {type.replace(/_/g, ' ')}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>

                <Grid item xs={12} sm={3}>
                  <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <DatePicker
                      label="Start date"
                      value={formData.startDate}
                      onChange={(date) => handleChange('startDate', date)}
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </LocalizationProvider>
                </Grid>

                <Grid item xs={12} sm={3}>
                  <LocalizationProvider dateAdapter={AdapterDateFns}>
                    <DatePicker
                      label="End date"
                      value={formData.endDate}
                      onChange={(date) => handleChange('endDate', date)}
                      minDate={formData.startDate}
                      slotProps={{ textField: { fullWidth: true } }}
                    />
                  </LocalizationProvider>
                </Grid>

                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    multiline
                    rows={4}
                    label="Reason"
                    value={formData.reason}
                    onChange={(e) => handleChange('reason', e.target.value)}
                  />
                </Grid>

                <Grid item xs={12}>
                  <Box sx={{ display: 'flex', gap: 1.5, mt: 1, flexWrap: 'wrap' }}>
                    <Button
                      type="submit"
                      variant="contained"
                      color="primary"
                      disabled={loading}
                      sx={{ px: 3, py: 1.25 }}
                    >
                      {loading ? 'Submitting…' : 'Submit request'}
                    </Button>
                    <Button
                      variant="outlined"
                      color="secondary"
                      onClick={() => navigate('/leave-requests')}
                      sx={{ px: 3, py: 1.25 }}
                    >
                      Cancel
                    </Button>
                  </Box>
                </Grid>
              </Grid>
            </form>
          </CardContent>
        </Card>
      </motion.div>
    </Box>
  )
}

export default LeaveRequestForm
