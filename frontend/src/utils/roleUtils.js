/**
 * Role-based access control utilities
 */

export const UserRole = {
  EMPLOYEE: 'EMPLOYEE',
  MANAGER: 'MANAGER',
  ADMIN: 'ADMIN',
  FINANCE: 'FINANCE',
  HR: 'HR',
}

/**
 * Check if user has a specific role
 */
export const hasRole = (user, role) => {
  if (!user || !user.role) return false
  return user.role === role
}

/**
 * Check if user has any of the specified roles
 */
export const hasAnyRole = (user, roles) => {
  if (!user || !user.role) return false
  return roles.includes(user.role)
}

/**
 * Check if user can actually approve expenses/leaves/timesheets.
 *
 * Deliberately MANAGER-only, matching the backend: ApprovalService rejects
 * any approver whose role isn't MANAGER (and who isn't the employee's
 * *assigned* manager specifically), regardless of what the frontend route
 * guard allows. ADMIN/FINANCE/HR can open the /approvals page to see the
 * queue for oversight, but this helper controls whether the Approve/Reject
 * actions themselves render -- showing that button to a role the backend
 * will always reject would be a real UX bug, not a permissions nuance.
 */
export const canApprove = (user) => {
  return hasRole(user, UserRole.MANAGER)
}

/**
 * Check if user can approve expenses (MANAGER only)
 */
export const canApproveExpenses = (user) => {
  return hasRole(user, UserRole.MANAGER)
}

/**
 * Check if user can approve leave requests (MANAGER only)
 */
export const canApproveLeaves = (user) => {
  return hasRole(user, UserRole.MANAGER)
}

/**
 * Check if user can view all expenses (not just own)
 */
export const canViewAllExpenses = (user) => {
  return hasAnyRole(user, [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE])
}

/**
 * Check if user can access analytics. Matches the /analytics route guard in
 * App.jsx (MANAGER, ADMIN, FINANCE, HR) -- the analytics endpoints are
 * read-only aggregate reporting with no backend role restriction beyond
 * authentication, so this is intentionally broader than canApprove below.
 */
export const canAccessAnalytics = (user) => {
  return hasAnyRole(user, [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE, UserRole.HR])
}

/**
 * Check if user can access expense analytics specifically.
 */
export const canAccessExpenseAnalytics = (user) => {
  return hasAnyRole(user, [UserRole.MANAGER, UserRole.ADMIN, UserRole.FINANCE])
}

/**
 * Check if user can access leave analytics (Only Manager for approval-related analytics)
 */
export const canAccessLeaveAnalytics = (user) => {
  return hasRole(user, UserRole.MANAGER)
}

/**
 * Check if user can edit expense (own expense or has approval rights)
 */
export const canEditExpense = (user, expense) => {
  if (!user || !expense) return false
  
  // Can't edit if already approved/paid
  if (expense.status === 'APPROVED' || expense.status === 'PAID') return false
  
  // Admin and Manager can edit any pending/submitted expenses
  if (hasAnyRole(user, [UserRole.ADMIN, UserRole.MANAGER])) {
    return expense.status === 'PENDING' || expense.status === 'SUBMITTED'
  }
  
  // Employee can only edit their own pending expenses
  if (hasRole(user, UserRole.EMPLOYEE)) {
    return expense.userId === user.userId && expense.status === 'PENDING'
  }
  
  // Finance can view but typically can't edit
  return false
}

/**
 * Check if user can delete expense
 */
export const canDeleteExpense = (user, expense) => {
  if (!user || !expense) return false
  
  // Can't delete if already approved/paid
  if (expense.status === 'APPROVED' || expense.status === 'PAID') return false
  
  // Admin and Manager can delete any pending/submitted expenses
  if (hasAnyRole(user, [UserRole.ADMIN, UserRole.MANAGER])) {
    return expense.status === 'PENDING' || expense.status === 'SUBMITTED'
  }
  
  // Employee can only delete their own pending expenses
  if (hasRole(user, UserRole.EMPLOYEE)) {
    return expense.userId === user.userId && expense.status === 'PENDING'
  }
  
  return false
}

/**
 * Check if user can submit expense
 */
export const canSubmitExpense = (user, expense) => {
  if (!user || !expense) return false
  
  // Only owner can submit
  if (expense.userId !== user.userId) return false
  
  // Can only submit pending expenses
  return expense.status === 'PENDING'
}

