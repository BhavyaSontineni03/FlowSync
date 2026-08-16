/**
 * Module Configuration
 * Enterprise-style module system for scalable application architecture
 * Each module is self-contained with its own routes, analytics, and workflows
 */

export const MODULES = {
  EXPENSES: {
    id: 'expenses',
    name: 'Expense Management',
    icon: 'Receipt',
    color: '#4F7A5C',
    gradient: 'linear-gradient(135deg, #4F7A5C 0%, #6B9A78 100%)',
    route: '/expenses',
    analyticsRoute: '/analytics?module=expenses',
    roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'FINANCE'], // HR can view but not approve
    approvalRoles: ['MANAGER'], // Only Manager can approve
    analyticsRoles: ['MANAGER', 'FINANCE'], // Manager for approvals, Finance for finance activity
    description: 'Manage business expenses, receipts, and reimbursements',
    features: [
      'Create & submit expenses',
      'Receipt OCR processing',
      'Approval workflows',
      'Category tracking',
      'Export & reporting'
    ],
    stats: {
      total: 'totalExpenses',
      pending: 'pendingExpenses',
      approved: 'approvedExpenses',
      rejected: 'rejectedExpenses'
    }
  },
  LEAVE_REQUESTS: {
    id: 'leave-requests',
    name: 'Leave Management',
    icon: 'EventNote',
    color: '#4A7A8C',
    gradient: 'linear-gradient(135deg, #4A7A8C 0%, #6A9AAB 100%)',
    route: '/leave-requests',
    analyticsRoute: '/analytics?module=leave-requests',
    roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'], // Finance can view own leave data
    approvalRoles: ['MANAGER'], // Only Manager can approve
    analyticsRoles: ['MANAGER'], // Only Manager can access leave analytics (for approval-related analytics)
    description: 'Manage employee leave requests and time-off',
    features: [
      'Request leave',
      'Leave balance tracking',
      'Approval workflows',
      'Leave type management',
      'Calendar integration'
    ],
    stats: {
      total: 'totalLeaves',
      pending: 'pendingLeaves',
      approved: 'approvedLeaves',
      rejected: 'rejectedLeaves'
    }
  },
  TIMESHEETS: {
    id: 'timesheets',
    name: 'Timesheet Management',
    icon: 'AccessTime',
    color: '#6B9A78',
    gradient: 'linear-gradient(135deg, #6B9A78 0%, #4F7A5C 100%)',
    route: '/timesheets',
    analyticsRoute: '/analytics?module=timesheets',
    roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'], // Finance can view own timesheet data
    approvalRoles: ['MANAGER'], // Only Manager can approve
    analyticsRoles: ['MANAGER', 'FINANCE'], // Manager for approvals, Finance for finance activity
    description: 'Submit timesheets and track attendance',
    features: [
      'Submit timesheets',
      'Project code tracking',
      'Bench code support',
      'Approval workflows',
      'Attendance tracking'
    ],
    stats: {
      total: 'totalTimesheets',
      pending: 'pendingTimesheets',
      approved: 'approvedTimesheets',
      rejected: 'rejectedTimesheets'
    }
  },
  PAYROLL: {
    id: 'payroll',
    name: 'Payroll Management',
    icon: 'AccountBalance',
    color: '#B8894A',
    gradient: 'linear-gradient(135deg, #B8894A 0%, #8A652F 100%)',
    route: '/payroll',
    analyticsRoute: '/analytics?module=payroll',
    roles: ['EMPLOYEE', 'MANAGER', 'ADMIN', 'HR', 'FINANCE'],
    approvalRoles: ['FINANCE'], // Only Finance can process payroll
    analyticsRoles: ['MANAGER', 'FINANCE'], // Manager for approvals, Finance for finance activity
    description: 'View and process payroll',
    features: [
      'Calculate payroll',
      'Days worked tracking',
      'Leave deductions',
      'Salary calculations',
      'Payroll processing'
    ],
    stats: {
      total: 'totalPayrolls',
      processed: 'processedPayrolls',
      paid: 'paidPayrolls'
    }
  },
  PROJECTS: {
    id: 'projects',
    name: 'Project Management',
    icon: 'Folder',
    color: '#5A6577',
    gradient: 'linear-gradient(135deg, #5A6577 0%, #7A8799 100%)',
    route: '/projects',
    analyticsRoute: '/analytics?module=projects',
    roles: ['ADMIN'], // Only Admin can manage all projects
    approvalRoles: ['ADMIN'],
    analyticsRoles: ['ADMIN'],
    description: 'Manage projects and assignments',
    features: [
      'Create projects',
      'Project codes',
      'Employee assignments',
      'Project tracking',
      'Assignment management'
    ],
    stats: {
      total: 'totalProjects',
      active: 'activeProjects',
      completed: 'completedProjects'
    }
  }
  // Future modules can be added here:
  // PROJECTS: { ... },
  // INVENTORY: { ... },
  // HRM: { ... },
  // CRM: { ... },
}

/**
 * Get modules accessible by user role
 */
export const getModulesForRole = (userRole) => {
  return Object.values(MODULES).filter(module =>
    module.roles.includes(userRole)
  )
}

/**
 * Check if user can approve items in a module
 */
export const canApproveInModule = (userRole, moduleId) => {
  const module = Object.values(MODULES).find(m => m.id === moduleId)
  if (!module) return false
  return module.approvalRoles?.includes(userRole) || false
}

/**
 * Check if user can view analytics for a module
 */
export const canViewModuleAnalytics = (userRole, moduleId) => {
  const module = Object.values(MODULES).find(m => m.id === moduleId)
  if (!module) return false
  return module.analyticsRoles?.includes(userRole) || false
}

/**
 * Get module by ID
 */
export const getModuleById = (moduleId) => {
  return MODULES[Object.keys(MODULES).find(key => 
    MODULES[key].id === moduleId
  )]
}

