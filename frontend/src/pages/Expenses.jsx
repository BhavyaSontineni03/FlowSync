import React, { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
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
  IconButton,
  Chip,
  Pagination,
  TextField,
  InputAdornment,
  MenuItem,
  Select,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  useTheme,
} from '@mui/material'
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Search as SearchIcon,
  CloudUpload as CloudUploadIcon,
} from '@mui/icons-material'
import { motion } from 'framer-motion'
import { expenseService } from '../services/expenseService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { useAuth } from '../contexts/AuthContext'
import { canEditExpense, canDeleteExpense, canSubmitExpense } from '../utils/roleUtils'
import { TableSkeleton } from '../components/SkeletonLoader'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  sectionGap,
  cardPad,
  stackGapLoose,
} from '../utils/uiTokens'

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

const statusColors = {
  PENDING: { color: '#B8894A', bg: 'rgba(184, 137, 74, 0.12)' },
  SUBMITTED: { color: '#4A7A8C', bg: 'rgba(74, 122, 140, 0.12)' },
  APPROVED: { color: '#4F7A5C', bg: 'rgba(79, 122, 92, 0.12)' },
  REJECTED: { color: '#C45B6A', bg: 'rgba(196, 91, 106, 0.12)' },
  PAID: { color: '#4A5568', bg: 'rgba(74, 85, 104, 0.12)' },
}

export default function Expenses() {
  const [expenses, setExpenses] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [deleteDialog, setDeleteDialog] = useState({ open: false, id: null })
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { enqueueSnackbar } = useSnackbar()
  const { user } = useAuth()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    const status = searchParams.get('status')
    if (status) {
      setStatusFilter(status)
    }
    fetchExpenses()
  }, [page, statusFilter, categoryFilter, searchParams])

  const fetchExpenses = async () => {
    setLoading(true)
    try {
      const params = {
        page,
        size: 20,
        sortBy: 'createdAt',
        sortDir: 'DESC',
      }
      const response = await expenseService.getAll(params)
      setExpenses(response.content || [])
      setTotalPages(response.totalPages || 0)
    } catch (error) {
      enqueueSnackbar('Error loading expenses', { variant: 'error' })
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async () => {
    try {
      await expenseService.delete(deleteDialog.id)
      enqueueSnackbar('Expense deleted successfully', { variant: 'success' })
      setDeleteDialog({ open: false, id: null })
      fetchExpenses()
    } catch (error) {
      enqueueSnackbar('Error deleting expense', { variant: 'error' })
    }
  }

  const handleSubmit = async (id) => {
    try {
      await expenseService.submit(id)
      enqueueSnackbar('Expense submitted successfully', { variant: 'success' })
      fetchExpenses()
    } catch (error) {
      enqueueSnackbar('Error submitting expense', { variant: 'error' })
    }
  }

  const filteredExpenses = expenses.filter((expense) => {
    const matchesSearch =
      !searchTerm ||
      expense.description?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      expense.notes?.toLowerCase().includes(searchTerm.toLowerCase())
    const matchesStatus = !statusFilter || expense.status === statusFilter
    const matchesCategory = !categoryFilter || expense.category === categoryFilter
    return matchesSearch && matchesStatus && matchesCategory
  })

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
              Expenses
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              Track receipts and submissions for your organization.
            </Typography>
          </Box>
          <Button
            variant="contained"
            color="primary"
            startIcon={<AddIcon />}
            onClick={() => navigate('/expenses/new')}
            sx={{ px: 2.5, py: 1.25 }}
          >
            New expense
          </Button>
        </Box>
      </motion.div>

      <Card sx={{ mb: sectionGap, ...getSurfaceStyles(isDark, 'card') }}>
        <CardContent sx={{ p: { xs: cardPad, sm: 2.5 }, '&:last-child': { pb: { xs: cardPad, sm: 2.5 } } }}>
          <Box sx={{ display: 'flex', gap: stackGapLoose, flexWrap: 'wrap' }}>
            <TextField
              placeholder="Search expenses..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              size="small"
              sx={{ flexGrow: 1, minWidth: { xs: '100%', sm: 200 } }}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon sx={{ color: 'text.secondary', fontSize: 20 }} />
                  </InputAdornment>
                ),
              }}
            />
            <FormControl size="small" sx={{ minWidth: 140, flex: { xs: '1 1 140px', sm: '0 0 auto' } }}>
              <InputLabel>Status</InputLabel>
              <Select
                value={statusFilter}
                label="Status"
                onChange={(e) => setStatusFilter(e.target.value)}
              >
                <MenuItem value="">All</MenuItem>
                <MenuItem value="PENDING">Pending</MenuItem>
                <MenuItem value="SUBMITTED">Submitted</MenuItem>
                <MenuItem value="APPROVED">Approved</MenuItem>
                <MenuItem value="REJECTED">Rejected</MenuItem>
                <MenuItem value="PAID">Paid</MenuItem>
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 140, flex: { xs: '1 1 140px', sm: '0 0 auto' } }}>
              <InputLabel>Category</InputLabel>
              <Select
                value={categoryFilter}
                label="Category"
                onChange={(e) => setCategoryFilter(e.target.value)}
              >
                <MenuItem value="">All</MenuItem>
                {categories.map((cat) => (
                  <MenuItem key={cat} value={cat}>
                    {cat.replace('_', ' ')}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
        </CardContent>
      </Card>

      {loading ? (
        <TableSkeleton rows={5} columns={6} />
      ) : (
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
        >
          <TableContainer
            component={Paper}
            sx={{
              ...getSurfaceStyles(isDark, 'card'),
              overflowX: 'auto',
            }}
          >
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell 
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Description
                  </TableCell>
                  <TableCell 
                    align="right"
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Amount
                  </TableCell>
                  <TableCell 
                    align="center"
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Date
                  </TableCell>
                  <TableCell 
                    align="center"
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Category
                  </TableCell>
                  <TableCell 
                    align="center"
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Status
                  </TableCell>
                  <TableCell 
                    align="center"
                    sx={{ 
                      fontWeight: 600, 
                      letterSpacing: '-0.01em',
                      py: 2,
                      px: 3,
                      fontSize: '0.75rem',
                      textTransform: 'uppercase',
                      opacity: isDark ? 0.85 : 0.75,
                      color: isDark ? 'rgba(255, 255, 255, 0.9)' : 'rgba(0, 0, 0, 0.8)',
                      borderBottom: isDark 
                        ? '1px solid rgba(255, 255, 255, 0.12)'
                        : '1px solid rgba(0, 0, 0, 0.12)',
                    }}
                  >
                    Actions
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredExpenses.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 8, border: 'none' }}>
                      <Typography 
                        sx={{ 
                          opacity: isDark ? 0.7 : 0.65, 
                          fontSize: '0.9375rem',
                          color: isDark ? 'rgba(255, 255, 255, 0.8)' : 'rgba(0, 0, 0, 0.7)',
                        }}
                      >
                        No expenses found
                      </Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredExpenses.map((expense) => (
                    <TableRow
                      key={expense.id}
                      hover
                      sx={{
                        '&:hover': {
                          backgroundColor: isDark 
                            ? 'rgba(255, 255, 255, 0.05)'
                            : 'rgba(0, 0, 0, 0.02)',
                        },
                        '& td': {
                          borderBottom: isDark 
                            ? '1px solid rgba(255, 255, 255, 0.05)'
                            : '1px solid rgba(0, 0, 0, 0.05)',
                        },
                      }}
                    >
                      <TableCell sx={{ py: 2, px: 3 }}>
                        <Box>
                          <Typography 
                            variant="body2" 
                            sx={{ 
                              fontWeight: 500, 
                              mb: 0.5,
                              fontSize: '0.9375rem',
                              letterSpacing: '-0.01em',
                            }}
                          >
                            {expense.description}
                          </Typography>
                          {expense.notes && (
                            <Typography 
                              variant="caption" 
                              color="text.secondary" 
                              sx={{ 
                                opacity: 0.6,
                                fontSize: '0.8125rem',
                                display: 'block',
                              }}
                            >
                              {expense.notes}
                            </Typography>
                          )}
                        </Box>
                      </TableCell>
                      <TableCell align="right" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2" 
                          sx={{ 
                            fontWeight: 600, 
                            fontSize: '0.9375rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          ${expense.amount?.toFixed(2)}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2" 
                          sx={{ 
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          {expense.expenseDate
                            ? format(new Date(expense.expenseDate), 'MMM dd, yyyy')
                            : '-'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                          }}
                        >
                          {expense.category?.replace('_', ' ') || 'OTHER'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Typography 
                          variant="body2"
                          sx={{
                            fontSize: '0.875rem',
                            letterSpacing: '-0.01em',
                            fontWeight: 600,
                            color: statusColors[expense.status]?.color || '#9C97A8',
                          }}
                        >
                          {expense.status || 'PENDING'}
                        </Typography>
                      </TableCell>
                      <TableCell align="center" sx={{ py: 2, px: 3 }}>
                        <Box sx={{ display: 'flex', gap: 0.5, justifyContent: 'center' }}>
                          {canEditExpense(user, expense) && (
                            <IconButton
                              size="small"
                              onClick={() => navigate(`/expenses/${expense.id}/edit`)}
                            >
                              <EditIcon fontSize="small" />
                            </IconButton>
                          )}
                          {canSubmitExpense(user, expense) && (
                            <IconButton
                              size="small"
                              color="primary"
                              onClick={() => handleSubmit(expense.id)}
                            >
                              <CloudUploadIcon fontSize="small" />
                            </IconButton>
                          )}
                          {canDeleteExpense(user, expense) && (
                            <IconButton
                              size="small"
                              color="error"
                              onClick={() => setDeleteDialog({ open: true, id: expense.id })}
                            >
                              <DeleteIcon fontSize="small" />
                            </IconButton>
                          )}
                        </Box>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {totalPages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
              <Pagination
                count={totalPages}
                page={page + 1}
                onChange={(e, value) => setPage(value - 1)}
                color="primary"
                sx={{
                  '& .MuiPaginationItem-root': {
                    borderRadius: 10,
                    fontWeight: 500,
                  },
                }}
              />
            </Box>
          )}
        </motion.div>
      )}

      <Dialog
        open={deleteDialog.open}
        onClose={() => setDeleteDialog({ open: false, id: null })}
        PaperProps={{ sx: { ...getSurfaceStyles(isDark, 'card'), borderRadius: 2 } }}
      >
        <DialogTitle sx={{ fontWeight: 600 }}>Delete expense</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary">
            Are you sure you want to delete this expense? This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setDeleteDialog({ open: false, id: null })}>
            Cancel
          </Button>
          <Button onClick={handleDelete} variant="contained" color="error">
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
