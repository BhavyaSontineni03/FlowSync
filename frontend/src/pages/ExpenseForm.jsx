import React, { useState, useEffect } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Grid,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  CircularProgress,
  Paper,
  IconButton,
  InputAdornment,
  useTheme,
} from '@mui/material'
import { 
  ArrowBack as ArrowBackIcon, 
  CloudUpload as CloudUploadIcon, 
  Delete as DeleteIcon,
  Description as DescriptionIcon,
  AttachMoney as MoneyIcon,
  CalendarToday as CalendarIcon,
  Category as CategoryIcon,
  Notes as NotesIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { useDropzone } from 'react-dropzone'
import { expenseService } from '../services/expenseService'
import { useSnackbar } from 'notistack'
import { getSurfaceStyles } from '../utils/glassStyles'
import { pageSx, sectionGap, cardPadComfortableSx } from '../utils/uiTokens'

const categories = [
  'TRAVEL',
  'MEALS',
  'ACCOMMODATION',
  'TRANSPORTATION',
  'OFFICE_SUPPLIES',
  'SOFTWARE',
  'TRAINING',
  'ENTERTAINMENT',
  'UTILITIES',
  'OTHER',
]

export default function ExpenseForm() {
  const { id } = useParams()
  const isEdit = !!id
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  const [loading, setLoading] = useState(isEdit)
  const [submitting, setSubmitting] = useState(false)
  const [formData, setFormData] = useState({
    description: '',
    amount: '',
    expenseDate: new Date().toISOString().split('T')[0],
    category: 'OTHER',
    notes: '',
  })
  const [receiptFile, setReceiptFile] = useState(null)
  const [receiptPreview, setReceiptPreview] = useState(null)

  useEffect(() => {
    if (isEdit) {
      fetchExpense()
    }
  }, [id])

  const fetchExpense = async () => {
    try {
      const expense = await expenseService.getById(id)
      setFormData({
        description: expense.description || '',
        amount: expense.amount || '',
        expenseDate: expense.expenseDate || new Date().toISOString().split('T')[0],
        category: expense.category || 'OTHER',
        notes: expense.notes || '',
      })
      if (expense.receiptUrl) {
        setReceiptPreview(expense.receiptUrl)
      }
    } catch (error) {
      enqueueSnackbar('Error loading expense', { variant: 'error' })
      navigate('/expenses')
    } finally {
      setLoading(false)
    }
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'image/*': ['.png', '.jpg', '.jpeg', '.pdf'],
    },
    maxFiles: 1,
    onDrop: (acceptedFiles) => {
      if (acceptedFiles.length > 0) {
        const file = acceptedFiles[0]
        setReceiptFile(file)
        if (file.type.startsWith('image/')) {
          const reader = new FileReader()
          reader.onload = () => setReceiptPreview(reader.result)
          reader.readAsDataURL(file)
        }
      }
    },
  })

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    
    if (!formData.description.trim()) {
      enqueueSnackbar('Description is required', { variant: 'error' })
      return
    }

    const amount = parseFloat(formData.amount)
    if (isNaN(amount) || amount <= 0) {
      enqueueSnackbar('Amount must be greater than 0', { variant: 'error' })
      return
    }

    if (amount > 999999.99) {
      enqueueSnackbar('Amount exceeds maximum limit of $999,999.99', { variant: 'error' })
      return
    }

    const expenseDate = new Date(formData.expenseDate)
    const today = new Date()
    today.setHours(23, 59, 59, 999)
    if (expenseDate > today) {
      enqueueSnackbar('Expense date cannot be in the future', { variant: 'error' })
      return
    }

    if (receiptFile && receiptFile.size > 10 * 1024 * 1024) {
      enqueueSnackbar('File size must be less than 10MB', { variant: 'error' })
      return
    }

    if (formData.description.length > 500) {
      enqueueSnackbar('Description must be less than 500 characters', { variant: 'error' })
      return
    }

    if (formData.notes && formData.notes.length > 5000) {
      enqueueSnackbar('Notes must be less than 5000 characters', { variant: 'error' })
      return
    }

    setSubmitting(true)

    try {
      const data = {
        ...formData,
        description: formData.description.trim(),
        amount: amount,
        notes: formData.notes?.trim() || '',
      }

      if (isEdit) {
        await expenseService.update(id, data)
        enqueueSnackbar('Expense updated successfully', { variant: 'success' })
      } else {
        await expenseService.create(data, receiptFile)
        enqueueSnackbar('Expense created successfully', { variant: 'success' })
      }
      navigate('/expenses')
    } catch (error) {
      let errorMessage = 'Error saving expense'
      if (error.response) {
        errorMessage = error.response.data?.message || `Server error: ${error.response.status}`
      } else if (error.request) {
        errorMessage = 'Network error. Please check your connection.'
      } else {
        errorMessage = error.message || 'An unexpected error occurred'
      }
      enqueueSnackbar(errorMessage, { variant: 'error' })
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <Box 
        sx={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center',
          minHeight: '60vh',
          p: 4,
        }}
      >
        <CircularProgress size={48} color="primary" />
      </Box>
    )
  }

  return (
    <Box 
      sx={{ 
        ...pageSx,
        height: 'calc(100vh - 64px)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}
    >
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, ease: [0.25, 0.1, 0.25, 1], type: 'tween' }}
        style={{ willChange: 'transform' }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', mb: sectionGap, flexShrink: 0 }}>
          <motion.div 
            whileTap={{ scale: 0.96 }}
            transition={{ duration: 0.15, ease: [0.25, 0.1, 0.25, 1] }}
            style={{ willChange: 'transform' }}
          >
            <IconButton 
              onClick={() => navigate('/expenses')} 
              sx={{ 
                mr: 2,
                borderRadius: 2,
                backgroundColor: isDark 
                  ? 'rgba(255, 255, 255, 0.1)'
                  : 'rgba(0, 0, 0, 0.05)',
                '&:hover': {
                  backgroundColor: isDark 
                    ? 'rgba(255, 255, 255, 0.15)'
                    : 'rgba(0, 0, 0, 0.1)',
                },
              }}
            >
              <ArrowBackIcon />
            </IconButton>
          </motion.div>
          <Typography
            variant="h4"
            component="h1"
            sx={{
              fontWeight: 600,
              fontSize: { xs: '1.5rem', sm: '1.75rem', md: '2rem' },
            }}
          >
            {isEdit ? 'Edit expense' : 'New expense'}
          </Typography>
        </Box>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1, duration: 0.5 }}
        style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}
      >
        <Card
          sx={{
            ...getSurfaceStyles(isDark, 'card'),
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <CardContent 
            sx={{ 
              ...cardPadComfortableSx,
              p: { xs: 2.5, sm: 3, md: 4 },
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              overflow: 'auto',
              '&::-webkit-scrollbar': {
                width: '8px',
              },
              '&::-webkit-scrollbar-track': {
                background: 'transparent',
              },
              '&::-webkit-scrollbar-thumb': {
                background: isDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.2)',
                borderRadius: '4px',
              },
            }}
          >
            <Box component="form" onSubmit={handleSubmit} sx={{ display: 'flex', flexDirection: 'column', height: '100%', justifyContent: 'flex-start' }}>
              <Grid container spacing={3.5} sx={{ flex: 1, alignContent: 'flex-start' }}>
                <Grid item xs={12}>
                  <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.2 }}
                  >
                    <TextField
                      fullWidth
                      label="Description"
                      name="description"
                      value={formData.description}
                      onChange={handleChange}
                      required
                      multiline
                      rows={2}
                      inputProps={{ maxLength: 500 }}
                      helperText={`${formData.description.length}/500 characters`}
                     
                      size="small"
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start" sx={{ alignSelf: 'flex-start', mt: 1.5 }}>
                            <DescriptionIcon sx={{ opacity: isDark ? 0.75 : 0.7, fontSize: 20, color: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)' }} />
                          </InputAdornment>
                        ),
                      }}
                      FormHelperTextProps={{
                        sx: { 
                          ml: 0, 
                          mt: 1, 
                          opacity: isDark ? 0.7 : 0.65, 
                          fontSize: '0.75rem', 
                          color: isDark ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.65)'
                        }
                      }}
                    />
                  </motion.div>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.3 }}
                  >
                    <TextField
                      fullWidth
                      label="Amount"
                      name="amount"
                      type="number"
                      value={formData.amount}
                      onChange={handleChange}
                      required
                      inputProps={{ step: '0.01', min: '0.01' }}
                     
                      size="small"
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <MoneyIcon sx={{ opacity: isDark ? 0.75 : 0.7, fontSize: 20, color: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)' }} />
                          </InputAdornment>
                        ),
                      }}
                    />
                  </motion.div>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.4 }}
                  >
                    <TextField
                      fullWidth
                      label="Expense Date"
                      name="expenseDate"
                      type="date"
                      value={formData.expenseDate}
                      onChange={handleChange}
                      required
                     
                      size="small"
                      InputLabelProps={{ shrink: true }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">
                            <CalendarIcon sx={{ opacity: isDark ? 0.75 : 0.7, fontSize: 20, color: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)' }} />
                          </InputAdornment>
                        ),
                      }}
                    />
                  </motion.div>
                </Grid>
                <Grid item xs={12} sm={4}>
                  <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.5 }}
                  >
                    <FormControl fullWidth size="small">
                      <InputLabel>Category</InputLabel>
                      <Select
                        name="category"
                        value={formData.category}
                        label="Category"
                        onChange={handleChange}
                        required
                        startAdornment={
                          <InputAdornment position="start">
                            <CategoryIcon sx={{ opacity: isDark ? 0.75 : 0.7, fontSize: 20, color: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)' }} />
                          </InputAdornment>
                        }
                      >
                        {categories.map((cat) => (
                          <MenuItem key={cat} value={cat}>
                            {cat.replace('_', ' ')}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </motion.div>
                </Grid>
                <Grid item xs={12}>
                  <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.6 }}
                  >
                    <TextField
                      fullWidth
                      label="Notes"
                      name="notes"
                      value={formData.notes}
                      onChange={handleChange}
                      multiline
                      rows={2}
                      inputProps={{ maxLength: 5000 }}
                      helperText={formData.notes ? `${formData.notes.length}/5000 characters` : 'Optional'}
                     
                      size="small"
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start" sx={{ alignSelf: 'flex-start', mt: 1.5 }}>
                            <NotesIcon sx={{ opacity: isDark ? 0.75 : 0.7, fontSize: 20, color: isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)' }} />
                          </InputAdornment>
                        ),
                      }}
                      FormHelperTextProps={{
                        sx: { 
                          ml: 0, 
                          mt: 1, 
                          opacity: isDark ? 0.7 : 0.65, 
                          fontSize: '0.75rem', 
                          color: isDark ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.65)'
                        }
                      }}
                    />
                  </motion.div>
                </Grid>
                <Grid item xs={12}>
                  <motion.div
                    initial={{ opacity: 0, y: 8 }}
                    style={{ willChange: 'transform' }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.7 }}
                  >
                    <Typography 
                      variant="subtitle2" 
                      gutterBottom 
                      sx={{ 
                        fontWeight: 600, 
                        mb: 2.5,
                        fontSize: '0.875rem',
                        letterSpacing: '-0.01em',
                      }}
                    >
                      Receipt (Optional)
                    </Typography>
                    {!receiptPreview && (
                      <Paper
                        {...getRootProps()}
                        sx={{
                          p: 4,
                          border: '2px dashed',
                          borderColor: isDragActive ? '#4F7A5C' : isDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.15)',
                          borderRadius: 2,
                          textAlign: 'center',
                          cursor: 'pointer',
                          backgroundColor: isDragActive 
                            ? isDark ? 'rgba(79, 122, 92, 0.15)' : 'rgba(79, 122, 92, 0.08)'
                            : isDark ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.02)',
                          transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                          '&:hover': {
                            borderColor: '#4F7A5C',
                            backgroundColor: isDark 
                              ? 'rgba(79, 122, 92, 0.1)'
                              : 'rgba(79, 122, 92, 0.05)',
                          },
                        }}
                      >
                        <input {...getInputProps()} />
                        <motion.div
                          animate={{ y: isDragActive ? -5 : 0 }}
                          transition={{ type: 'spring', stiffness: 300 }}
                        >
                          <CloudUploadIcon 
                            sx={{ 
                              fontSize: 48, 
                              mb: 2,
                              opacity: isDragActive ? 1 : (isDark ? 0.75 : 0.7),
                              color: isDragActive ? '#4F7A5C' : (isDark ? 'rgba(255, 255, 255, 0.75)' : 'rgba(0, 0, 0, 0.7)'),
                            }} 
                          />
                        </motion.div>
                        <Typography 
                          variant="body2" 
                          sx={{ 
                            color: isDragActive ? '#4F7A5C' : 'text.secondary',
                            fontWeight: 500,
                            mb: 1,
                            fontSize: '0.875rem',
                          }}
                        >
                          {isDragActive
                            ? 'Drop the file here...'
                            : 'Drag & drop a receipt here, or click to select'}
                        </Typography>
                        <Typography 
                          variant="caption" 
                          sx={{ 
                            opacity: isDark ? 0.7 : 0.65,
                            color: isDark ? 'rgba(255, 255, 255, 0.7)' : 'rgba(0, 0, 0, 0.65)',
                            display: 'block',
                            fontSize: '0.75rem',
                          }}
                        >
                          Supports: PNG, JPG, JPEG, PDF (Max 10MB)
                        </Typography>
                      </Paper>
                    )}
                    {receiptPreview && (
                      <Box>
                        <Paper 
                          sx={{ 
                            p: 2, 
                            position: 'relative', 
                            display: 'inline-block',
                            borderRadius: 4,
                            backgroundColor: isDark 
                              ? 'rgba(255, 255, 255, 0.05)'
                              : 'rgba(0, 0, 0, 0.02)',
                          }}
                        >
                          {receiptFile?.type?.startsWith('image/') ? (
                            <img
                              src={receiptPreview}
                              alt="Receipt preview"
                              style={{ 
                                maxWidth: '300px', 
                                maxHeight: '300px', 
                                display: 'block',
                                borderRadius: '12px',
                              }}
                            />
                          ) : (
                            <Box sx={{ p: 2, textAlign: 'center' }}>
                              <Typography sx={{ fontWeight: 500 }}>
                                {receiptFile?.name || 'Receipt uploaded'}
                              </Typography>
                            </Box>
                          )}
                          <motion.div 
                            whileTap={{ scale: 0.92 }}
                            transition={{ duration: 0.15, ease: [0.25, 0.1, 0.25, 1] }}
                            style={{ willChange: 'transform' }}
                          >
                            <IconButton
                              size="small"
                              onClick={() => {
                                setReceiptFile(null)
                                setReceiptPreview(null)
                              }}
                              sx={{ 
                                position: 'absolute', 
                                top: 8, 
                                right: 8, 
                                bgcolor: '#C45B6A',
                                color: 'white',
                                '&:hover': {
                                  bgcolor: '#9A3D4A',
                                },
                              }}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          </motion.div>
                        </Paper>
                      </Box>
                    )}
                  </motion.div>
                </Grid>
              </Grid>
              <Box 
                sx={{ 
                  display: 'flex', 
                  gap: 2, 
                  justifyContent: 'flex-end',
                  mt: 4,
                  pt: 3,
                  borderTop: isDark 
                    ? '1px solid rgba(255, 255, 255, 0.08)'
                    : '1px solid rgba(0, 0, 0, 0.08)',
                  flexShrink: 0,
                }}
              >
                <motion.div 
                  whileTap={{ scale: 0.97 }}
                  transition={{ duration: 0.15, ease: [0.25, 0.1, 0.25, 1] }}
                  style={{ willChange: 'transform' }}
                >
                  <Button 
                    variant="outlined" 
                    onClick={() => navigate('/expenses')} 
                    disabled={submitting}
                    sx={{
                      borderRadius: 2,
                      px: 4,
                      py: 1.25,
                      borderWidth: '1.5px',
                      fontWeight: 600,
                      fontSize: '0.9375rem',
                    }}
                  >
                    Cancel
                  </Button>
                </motion.div>
                <motion.div 
                  whileTap={{ scale: 0.97 }}
                  transition={{ duration: 0.15, ease: [0.25, 0.1, 0.25, 1] }}
                  style={{ willChange: 'transform' }}
                >
                  <Button
                    type="submit"
                    variant="contained"
                    color="primary"
                    disabled={submitting || !formData.description.trim() || !formData.amount || parseFloat(formData.amount) <= 0}
                    sx={{ px: 3.5, py: 1.25 }}
                  >
                    {submitting ? <CircularProgress size={20} sx={{ color: 'white' }} /> : isEdit ? 'Update expense' : 'Create expense'}
                  </Button>
                </motion.div>
              </Box>
            </Box>
          </CardContent>
        </Card>
      </motion.div>
    </Box>
  )
}
