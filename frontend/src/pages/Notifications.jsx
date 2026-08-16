import React, { useState, useEffect } from 'react'
import {
  Box,
  Card,
  CardContent,
  Typography,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  Divider,
  Paper,
  Button,
  CircularProgress,
  useTheme,
} from '@mui/material'
import {
  CheckCircle as CheckCircleIcon,
  Info as InfoIcon,
  Warning as WarningIcon,
  Error as ErrorIcon,
  DoneAll as DoneAllIcon,
  NotificationsNone as EmptyIcon,
  AccountBalance as AccountBalanceIcon,
} from '@mui/icons-material'
import { motion, AnimatePresence } from 'framer-motion'
import { notificationService } from '../services/notificationService'
import { useSnackbar } from 'notistack'
import { format } from 'date-fns'
import { getSurfaceStyles, motionPresets } from '../utils/glassStyles'
import {
  pageSx,
  pageHeaderSx,
  pageTitleSx,
  pageSubtitleSx,
  paletteTone,
} from '../utils/uiTokens'

const TYPE_TO_KEY = {
  EXPENSE_SUBMITTED: 'info',
  EXPENSE_APPROVED: 'success',
  EXPENSE_REJECTED: 'error',
  EXPENSE_PAID: 'success',
  APPROVAL_REQUESTED: 'warning',
  PAYROLL_PAID: 'success',
  SYSTEM_ANNOUNCEMENT: 'primary',
}

const TYPE_ICONS = {
  EXPENSE_SUBMITTED: InfoIcon,
  EXPENSE_APPROVED: CheckCircleIcon,
  EXPENSE_REJECTED: ErrorIcon,
  EXPENSE_PAID: CheckCircleIcon,
  APPROVAL_REQUESTED: WarningIcon,
  PAYROLL_PAID: AccountBalanceIcon,
  SYSTEM_ANNOUNCEMENT: InfoIcon,
}

const TYPE_LABELS = {
  EXPENSE_SUBMITTED: 'Submitted',
  EXPENSE_APPROVED: 'Approved',
  EXPENSE_REJECTED: 'Rejected',
  EXPENSE_PAID: 'Paid',
  APPROVAL_REQUESTED: 'Approval needed',
  PAYROLL_PAID: 'Payroll paid',
  SYSTEM_ANNOUNCEMENT: 'Announcement',
}

export default function Notifications() {
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [markingAsRead, setMarkingAsRead] = useState(false)
  const { enqueueSnackbar } = useSnackbar()
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  useEffect(() => {
    fetchNotifications()
  }, [])

  const fetchNotifications = async () => {
    setLoading(true)
    try {
      const response = await notificationService.getAll({ page: 0, size: 50 })
      setNotifications(response.content || [])
    } catch (error) {
      if (error.response?.status === 403 || error.response?.status === 401) {
        enqueueSnackbar('Unauthorized to view notifications', { variant: 'error' })
      } else {
        enqueueSnackbar('Error loading notifications', { variant: 'error' })
      }
      setNotifications([])
    } finally {
      setLoading(false)
    }
  }

  const handleMarkAsRead = async (notificationId) => {
    try {
      await notificationService.markAsRead(notificationId)
      setNotifications((prev) =>
        prev.map((notif) =>
          notif.id === notificationId ? { ...notif, isRead: true } : notif
        )
      )
      window.dispatchEvent(new Event('notification-updated'))
    } catch (error) {
      enqueueSnackbar('Error marking notification as read', { variant: 'error' })
    }
  }

  const handleMarkAllAsRead = async () => {
    setMarkingAsRead(true)
    try {
      await notificationService.markAllAsRead()
      setNotifications((prev) => prev.map((notif) => ({ ...notif, isRead: true })))
      enqueueSnackbar('All notifications marked as read', { variant: 'success' })
      window.dispatchEvent(new Event('notification-updated'))
    } catch (error) {
      enqueueSnackbar('Error marking all as read', { variant: 'error' })
    } finally {
      setMarkingAsRead(false)
    }
  }

  const unreadCount = notifications.filter((n) => !n.isRead).length

  const resolveType = (type) => {
    const key = TYPE_TO_KEY[type] || 'primary'
    const tone = paletteTone(theme, key)
    const Icon = TYPE_ICONS[type] || InfoIcon
    return {
      Icon,
      ...tone,
      label: TYPE_LABELS[type] || type?.replace(/_/g, ' ') || 'Notification',
    }
  }

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 4 }}>
        <CircularProgress color="primary" />
      </Box>
    )
  }

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
              Notifications
            </Typography>
            <Typography variant="body2" sx={pageSubtitleSx}>
              {unreadCount > 0
                ? `${unreadCount} unread. Approvals, payments, and system notes.`
                : 'You are caught up.'}
            </Typography>
          </Box>
          {unreadCount > 0 && (
            <Button
              variant="contained"
              color="primary"
              startIcon={<DoneAllIcon />}
              onClick={handleMarkAllAsRead}
              disabled={markingAsRead}
              sx={{ px: 2.5, py: 1.25 }}
            >
              {markingAsRead ? 'Marking…' : 'Mark all read'}
            </Button>
          )}
        </Box>
      </motion.div>

      <AnimatePresence mode="wait">
        {notifications.length === 0 ? (
          <motion.div
            initial={motionPresets.cardInitial}
            animate={motionPresets.cardAnimate}
            exit={{ opacity: 0 }}
            transition={motionPresets.cardTransition}
          >
            <Card sx={getSurfaceStyles(isDark, 'card')}>
              <CardContent>
                <Box sx={{ textAlign: 'center', py: 6 }}>
                  <EmptyIcon sx={{ fontSize: 64, color: 'primary.main', mb: 2, opacity: 0.45 }} />
                  <Typography variant="h6" sx={{ fontWeight: 600, mb: 0.5 }}>
                    Inbox clear
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    New expense and payroll updates will land here.
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          </motion.div>
        ) : (
          <motion.div
            initial={motionPresets.cardInitial}
            animate={motionPresets.cardAnimate}
            transition={motionPresets.cardTransition}
          >
            <Paper sx={{ ...getSurfaceStyles(isDark, 'card'), overflow: 'hidden' }}>
              <List sx={{ py: 0 }}>
                {notifications.map((notification, index) => {
                  const config = resolveType(notification.type)
                  const Icon = config.Icon
                  return (
                    <React.Fragment key={notification.id}>
                      <ListItem
                        sx={{
                          py: 2.5,
                          px: { xs: 2, sm: 3 },
                          backgroundColor: notification.isRead
                            ? 'transparent'
                            : isDark
                              ? 'rgba(79, 122, 92, 0.1)'
                              : 'rgba(79, 122, 92, 0.05)',
                          transition: 'background-color 0.2s ease',
                          '&:hover': {
                            backgroundColor: isDark
                              ? 'rgba(255,255,255,0.04)'
                              : 'rgba(30,41,59,0.03)',
                          },
                        }}
                        secondaryAction={
                          !notification.isRead && (
                            <IconButton
                              onClick={() => handleMarkAsRead(notification.id)}
                              aria-label="Mark as read"
                              sx={{ color: 'primary.main' }}
                            >
                              <CheckCircleIcon />
                            </IconButton>
                          )
                        }
                      >
                        <ListItemIcon>
                          <Box
                            sx={{
                              width: 44,
                              height: 44,
                              borderRadius: 2,
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              backgroundColor: config.bg,
                              color: config.color,
                            }}
                          >
                            <Icon sx={{ fontSize: 22 }} />
                          </Box>
                        </ListItemIcon>
                        <ListItemText
                          primary={
                            <Typography
                              variant="body1"
                              sx={{
                                fontWeight: notification.isRead ? 500 : 600,
                                fontSize: '0.9375rem',
                                pr: 5,
                                lineHeight: 1.4,
                              }}
                            >
                              {notification.message || notification.title}
                            </Typography>
                          }
                          secondary={
                            <Box
                              sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1.25,
                                mt: 1,
                                flexWrap: 'wrap',
                              }}
                            >
                              <Typography variant="caption" color="text.secondary">
                                {notification.createdAt
                                  ? format(new Date(notification.createdAt), 'MMM dd, yyyy · h:mm a')
                                  : ''}
                              </Typography>
                              <Box
                                sx={{
                                  px: 1.25,
                                  py: 0.25,
                                  borderRadius: 1,
                                  backgroundColor: config.bg,
                                }}
                              >
                                <Typography
                                  variant="caption"
                                  sx={{
                                    color: config.color,
                                    fontWeight: 600,
                                    fontSize: '0.6875rem',
                                    letterSpacing: '0.04em',
                                    textTransform: 'uppercase',
                                  }}
                                >
                                  {config.label}
                                </Typography>
                              </Box>
                              {!notification.isRead && (
                                <Box
                                  sx={{
                                    width: 7,
                                    height: 7,
                                    borderRadius: '50%',
                                    backgroundColor: 'primary.main',
                                  }}
                                />
                              )}
                            </Box>
                          }
                        />
                      </ListItem>
                      {index < notifications.length - 1 && (
                        <Divider sx={{ mx: 3, borderColor: 'divider' }} />
                      )}
                    </React.Fragment>
                  )
                })}
              </List>
            </Paper>
          </motion.div>
        )}
      </AnimatePresence>
    </Box>
  )
}
