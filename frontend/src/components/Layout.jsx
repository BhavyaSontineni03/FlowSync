import React, { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  List,
  Typography,
  Divider,
  IconButton,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Badge,
  Avatar,
  Popover,
  useTheme,
  useMediaQuery,
} from '@mui/material'
import {
  Dashboard as DashboardIcon,
  Receipt as ReceiptIcon,
  CheckCircle as CheckCircleIcon,
  Analytics as AnalyticsIcon,
  History as HistoryIcon,
  Notifications as NotificationsIcon,
  Menu as MenuIcon,
  Logout as LogoutIcon,
  AccountCircle as AccountCircleIcon,
  DarkMode as DarkModeIcon,
  LightMode as LightModeIcon,
  EventNote as EventNoteIcon,
  AccessTime as AccessTimeIcon,
  Folder as FolderIcon,
  AccountBalance as AccountBalanceIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon,
  People as PeopleIcon,
  Assignment as AssignmentIcon,
  Groups as GroupsIcon,
  SupervisorAccount as SupervisorAccountIcon,
} from '@mui/icons-material'
import { useAuth } from '../contexts/AuthContext'
import { useThemeMode } from '../contexts/ThemeContext'
import { notificationService } from '../services/notificationService'
import { useSnackbar } from 'notistack'
import { getSurfaceStyles } from '../utils/glassStyles'
import { SPACE } from '../utils/uiTokens'

const drawerWidth = 260

const allMenuItems = [
  { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'Profile', icon: <AccountCircleIcon />, path: '/profile', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'Expenses', icon: <ReceiptIcon />, path: '/expenses', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'Leave Requests', icon: <EventNoteIcon />, path: '/leave-requests', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'] },
  { text: 'Timesheets', icon: <AccessTimeIcon />, path: '/timesheets', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'] },
  { text: 'Projects', icon: <FolderIcon />, path: '/projects', roles: ['ADMIN'] },
  { text: 'My Team', icon: <GroupsIcon />, path: '/my-team', roles: ['MANAGER'] },
  { text: 'My Employees', icon: <SupervisorAccountIcon />, path: '/my-employees', roles: ['HR'] },
  { text: 'My Projects', icon: <FolderIcon />, path: '/my-projects', roles: ['EMPLOYEE'] },
  { text: 'Payroll', icon: <AccountBalanceIcon />, path: '/payroll', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'] },
  { text: 'Finance', icon: <AccountBalanceWalletIcon />, path: '/finance', roles: ['FINANCE'] },
  { text: 'Approvals', icon: <CheckCircleIcon />, path: '/approvals', roles: ['MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'Analytics', icon: <AnalyticsIcon />, path: '/analytics', roles: ['MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'Activity Logs', icon: <HistoryIcon />, path: '/activity-logs', roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'FINANCE', 'HR'] },
  { text: 'User Management', icon: <PeopleIcon />, path: '/user-management', roles: ['ADMIN'] },
  { text: 'Admin Requests', icon: <AssignmentIcon />, path: '/admin-requests', roles: ['ADMIN'] },
]

const getMenuItems = (user) => {
  if (!user || !user.role) return []
  return allMenuItems.filter((item) => item.roles.includes(user.role))
}

export default function Layout({ children }) {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const isDark = theme.palette.mode === 'dark'
  const [mobileOpen, setMobileOpen] = useState(false)
  const [anchorEl, setAnchorEl] = useState(null)
  const [unreadCount, setUnreadCount] = useState(0)
  const { user, logout } = useAuth()
  const { mode, toggleMode } = useThemeMode()
  const navigate = useNavigate()
  const location = useLocation()
  const { enqueueSnackbar } = useSnackbar()

  useEffect(() => {
    fetchUnreadCount()
    const interval = setInterval(fetchUnreadCount, 30000)

    const handleNotificationUpdate = () => {
      fetchUnreadCount()
    }
    window.addEventListener('notification-updated', handleNotificationUpdate)

    return () => {
      clearInterval(interval)
      window.removeEventListener('notification-updated', handleNotificationUpdate)
    }
  }, [])

  const fetchUnreadCount = async () => {
    try {
      const count = await notificationService.getUnreadCount()
      setUnreadCount(count)
    } catch (error) {
      console.error('Error fetching unread count:', error)
    }
  }

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen)
  }

  const handleMenuOpen = (event) => {
    setAnchorEl(event.currentTarget)
  }

  const handleMenuClose = () => {
    setAnchorEl(null)
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
    enqueueSnackbar('Logged out successfully', { variant: 'info' })
  }

  const pageTitle =
    getMenuItems(user).find((item) => item.path === location.pathname)?.text ||
    allMenuItems.find((item) => item.path === location.pathname)?.text ||
    'FlowSync'

  const drawer = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Toolbar
        sx={{
          display: 'flex',
          alignItems: 'center',
          px: 2.5,
          py: 2,
          minHeight: '72px !important',
        }}
      >
        <Typography
          component="div"
          sx={{
            fontFamily: '"Fraunces", Georgia, serif',
            fontWeight: 600,
            fontSize: '1.375rem',
            letterSpacing: '-0.02em',
            color: 'text.primary',
          }}
        >
          FlowSync
        </Typography>
      </Toolbar>
      <Divider />
      <List sx={{ flex: 1, px: 1, pt: 1.5, pb: 2 }}>
        {getMenuItems(user).map((item) => {
          const selected = location.pathname === item.path
          return (
            <ListItem key={item.text} disablePadding sx={{ mb: 0.25 }}>
              <ListItemButton
                selected={selected}
                onClick={() => {
                  navigate(item.path)
                  if (isMobile) setMobileOpen(false)
                }}
                sx={{
                  borderRadius: 2,
                  py: 1.15,
                  px: 1.75,
                  '&.Mui-selected': {
                    backgroundColor: 'primary.main',
                    color: 'primary.contrastText',
                    '&:hover': {
                      backgroundColor: 'primary.dark',
                    },
                    '& .MuiListItemIcon-root': {
                      color: 'primary.contrastText',
                    },
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: selected ? 'inherit' : 'text.secondary',
                    minWidth: 36,
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText
                  primary={item.text}
                  primaryTypographyProps={{
                    fontWeight: selected ? 600 : 500,
                    fontSize: '0.9rem',
                  }}
                />
              </ListItemButton>
            </ListItem>
          )
        })}
      </List>
    </Box>
  )

  const navSurface = getSurfaceStyles(isDark, 'navigation')

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { md: `calc(100% - ${drawerWidth}px)` },
          ml: { md: `${drawerWidth}px` },
          ...navSurface,
          color: 'text.primary',
        }}
      >
        <Toolbar sx={{ px: SPACE.page, minHeight: '64px !important' }}>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 1.5, display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography
            variant="h6"
            noWrap
            component="div"
            sx={{
              flexGrow: 1,
              color: 'text.primary',
              fontWeight: 600,
              fontSize: '1.05rem',
            }}
          >
            {pageTitle}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <IconButton color="inherit" onClick={toggleMode} aria-label="toggle theme">
              {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
            </IconButton>
            <IconButton
              color="inherit"
              onClick={() => navigate('/notifications')}
              aria-label="notifications"
            >
              <Badge
                badgeContent={unreadCount}
                color="error"
                sx={{
                  '& .MuiBadge-badge': {
                    backgroundColor: 'error.main',
                  },
                }}
              >
                <NotificationsIcon />
              </Badge>
            </IconButton>
            <IconButton onClick={handleMenuOpen} sx={{ p: 0.5, ml: 0.5 }}>
              <Avatar
                sx={{
                  bgcolor: 'primary.main',
                  width: 34,
                  height: 34,
                  fontSize: '0.8rem',
                  fontWeight: 600,
                }}
              >
                {user?.firstName?.[0]}
                {user?.lastName?.[0]}
              </Avatar>
            </IconButton>
            <Popover
              open={Boolean(anchorEl)}
              anchorEl={anchorEl}
              onClose={handleMenuClose}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              disableRestoreFocus
              slotProps={{
                paper: {
                  sx: {
                    mt: 1,
                    minWidth: 260,
                    borderRadius: 2,
                    ...getSurfaceStyles(isDark, 'card'),
                    overflow: 'hidden',
                  },
                },
              }}
            >
              <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', gap: 2 }}>
                <Avatar
                  sx={{
                    bgcolor: 'primary.main',
                    width: 44,
                    height: 44,
                    fontWeight: 600,
                  }}
                >
                  {user?.firstName?.[0]}
                  {user?.lastName?.[0]}
                </Avatar>
                <Box sx={{ minWidth: 0 }}>
                  <Typography sx={{ fontWeight: 600, fontSize: '0.95rem', lineHeight: 1.3 }}>
                    {user?.firstName} {user?.lastName}
                  </Typography>
                  <Typography
                    variant="caption"
                    sx={{ color: 'text.secondary', textTransform: 'capitalize' }}
                  >
                    {user?.role?.toLowerCase()?.replace('_', ' ') || 'User'}
                  </Typography>
                </Box>
              </Box>
              <Divider />
              <Box
                onClick={handleLogout}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  px: 2.5,
                  py: 1.75,
                  cursor: 'pointer',
                  color: 'error.main',
                  '&:hover': {
                    backgroundColor: isDark ? 'rgba(196, 91, 106, 0.12)' : 'rgba(196, 91, 106, 0.08)',
                  },
                }}
              >
                <LogoutIcon sx={{ fontSize: 20 }} />
                <Typography variant="body2" sx={{ fontWeight: 500 }}>
                  Log out
                </Typography>
              </Box>
            </Popover>
          </Box>
        </Toolbar>
      </AppBar>
      <Box component="nav" sx={{ width: { md: drawerWidth }, flexShrink: { md: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={handleDrawerToggle}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
              ...navSurface,
            },
          }}
        >
          {drawer}
        </Drawer>
        <Drawer
          variant="permanent"
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width: drawerWidth,
              ...navSurface,
            },
          }}
          open
        >
          {drawer}
        </Drawer>
      </Box>
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          // Pages own padding via pageSx (16/24/32); keep shell flush.
          p: 0,
          width: { md: `calc(100% - ${drawerWidth}px)` },
          mt: '64px',
          minHeight: 'calc(100vh - 64px)',
        }}
      >
        {children}
      </Box>
    </Box>
  )
}
