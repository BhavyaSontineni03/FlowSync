/**
 * Enterprise Permission Matrix
 * Defines granular permissions for each role and module
 * Implements separation of duties and role-based access control
 */

export const UserRole = {
  EMPLOYEE: 'EMPLOYEE',
  MANAGER: 'MANAGER',
  ADMIN: 'ADMIN',
  FINANCE: 'FINANCE',
  HR: 'HR',
}

/**
 * Module-specific permissions
 */
export const Permissions = {
  // Expense Management Module
  EXPENSE: {
    CREATE: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN],
    VIEW_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE, UserRole.HR],
    VIEW_TEAM: [UserRole.MANAGER, UserRole.ADMIN],
    VIEW_ALL: [UserRole.ADMIN, UserRole.FINANCE],
    APPROVE: [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE], // Finance for financial approval
    REJECT: [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE],
    EDIT_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN],
    EDIT_ANY: [UserRole.ADMIN],
    DELETE_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN],
    DELETE_ANY: [UserRole.ADMIN],
    ANALYTICS: [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE], // Finance for financial analytics
    EXPORT: [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE],
  },

  // Leave Management Module
  LEAVE: {
    CREATE: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
    VIEW_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE, UserRole.HR],
    VIEW_TEAM: [UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
    VIEW_ALL: [UserRole.ADMIN, UserRole.HR],
    APPROVE: [UserRole.MANAGER, UserRole.ADMIN, UserRole.HR], // HR for policy compliance
    REJECT: [UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
    EDIT_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
    EDIT_ANY: [UserRole.ADMIN, UserRole.HR],
    DELETE_OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
    DELETE_ANY: [UserRole.ADMIN, UserRole.HR],
    ANALYTICS: [UserRole.MANAGER, UserRole.ADMIN, UserRole.HR], // HR for leave analytics
    EXPORT: [UserRole.MANAGER, UserRole.ADMIN, UserRole.HR],
  },

  // System Administration
  SYSTEM: {
    USER_MANAGEMENT: [UserRole.ADMIN],
    ORGANIZATION_SETTINGS: [UserRole.ADMIN],
    VIEW_ACTIVITY_LOGS: {
      OWN: [UserRole.EMPLOYEE, UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE, UserRole.HR],
      TEAM: [UserRole.MANAGER, UserRole.ADMIN],
      ALL: [UserRole.ADMIN],
    },
  },
}

/**
 * Check if user has permission for a specific action
 */
export const hasPermission = (user, module, action) => {
  if (!user || !user.role) return false
  
  const modulePermissions = Permissions[module]
  if (!modulePermissions) return false
  
  const allowedRoles = modulePermissions[action]
  if (!allowedRoles) return false
  
  return allowedRoles.includes(user.role)
}

/**
 * Check if user can approve expenses
 */
export const canApproveExpenses = (user) => {
  return hasPermission(user, 'EXPENSE', 'APPROVE')
}

/**
 * Check if user can approve leave requests
 */
export const canApproveLeaves = (user) => {
  return hasPermission(user, 'LEAVE', 'APPROVE')
}

/**
 * Check if user can view expense analytics
 */
export const canViewExpenseAnalytics = (user) => {
  return hasPermission(user, 'EXPENSE', 'ANALYTICS')
}

/**
 * Check if user can view leave analytics
 */
export const canViewLeaveAnalytics = (user) => {
  return hasPermission(user, 'LEAVE', 'ANALYTICS')
}

/**
 * Check if user can view all expenses (not just own)
 */
export const canViewAllExpenses = (user) => {
  return hasPermission(user, 'EXPENSE', 'VIEW_ALL') || 
         hasPermission(user, 'EXPENSE', 'VIEW_TEAM')
}

/**
 * Check if user can view all leave requests (not just own)
 */
export const canViewAllLeaves = (user) => {
  return hasPermission(user, 'LEAVE', 'VIEW_ALL') || 
         hasPermission(user, 'LEAVE', 'VIEW_TEAM')
}

/**
 * Get accessible modules for a role
 */
export const getAccessibleModules = (userRole) => {
  const modules = []
  
  // Expense Management - accessible to all roles
  if (Permissions.EXPENSE.VIEW_OWN.includes(userRole)) {
    modules.push('expenses')
  }
  
  // Leave Management - accessible to all roles
  if (Permissions.LEAVE.VIEW_OWN.includes(userRole)) {
    modules.push('leave-requests')
  }
  
  return modules
}

/**
 * Check if user can access a specific module
 */
export const canAccessModule = (user, moduleId) => {
  if (!user || !user.role) return false
  
  const accessibleModules = getAccessibleModules(user.role)
  return accessibleModules.includes(moduleId)
}

