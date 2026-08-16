import { describe, it, expect } from 'vitest'
import {
  UserRole,
  hasRole,
  hasAnyRole,
  canApprove,
  canViewAllExpenses,
  canAccessAnalytics,
  canEditExpense,
  canDeleteExpense,
  canSubmitExpense,
} from '../roleUtils'

describe('Role Utils', () => {
  const employeeUser = {
    userId: 1,
    role: 'EMPLOYEE',
    email: 'employee@test.com',
  }

  const managerUser = {
    userId: 2,
    role: 'MANAGER',
    email: 'manager@test.com',
  }

  const adminUser = {
    userId: 3,
    role: 'ADMIN',
    email: 'admin@test.com',
  }

  const financeUser = {
    userId: 4,
    role: 'FINANCE',
    email: 'finance@test.com',
  }

  describe('hasRole', () => {
    it('should return true for matching role', () => {
      expect(hasRole(employeeUser, UserRole.EMPLOYEE)).toBe(true)
      expect(hasRole(managerUser, UserRole.MANAGER)).toBe(true)
    })

    it('should return false for non-matching role', () => {
      expect(hasRole(employeeUser, UserRole.MANAGER)).toBe(false)
      expect(hasRole(managerUser, UserRole.EMPLOYEE)).toBe(false)
    })

    it('should return false for null user', () => {
      expect(hasRole(null, UserRole.EMPLOYEE)).toBe(false)
    })
  })

  describe('hasAnyRole', () => {
    it('should return true if user has any of the roles', () => {
      expect(hasAnyRole(managerUser, [UserRole.MANAGER, UserRole.ADMIN])).toBe(true)
      expect(hasAnyRole(adminUser, [UserRole.MANAGER, UserRole.ADMIN])).toBe(true)
    })

    it('should return false if user has none of the roles', () => {
      expect(hasAnyRole(employeeUser, [UserRole.MANAGER, UserRole.ADMIN])).toBe(false)
    })
  })

  describe('canApprove', () => {
    // Deliberately MANAGER-only: the backend (ApprovalService) rejects any
    // approver who isn't the employee's assigned manager, so this helper
    // must not tell the UI to render an Approve button for roles the
    // backend will reject regardless of frontend page access.
    it('should return true for MANAGER', () => {
      expect(canApprove(managerUser)).toBe(true)
    })

    it('should return false for ADMIN (can view the approvals queue, cannot action it)', () => {
      expect(canApprove(adminUser)).toBe(false)
    })

    it('should return false for FINANCE (can view the approvals queue, cannot action it)', () => {
      expect(canApprove(financeUser)).toBe(false)
    })

    it('should return false for EMPLOYEE', () => {
      expect(canApprove(employeeUser)).toBe(false)
    })
  })

  describe('canViewAllExpenses', () => {
    it('should return true for MANAGER, ADMIN, FINANCE', () => {
      expect(canViewAllExpenses(managerUser)).toBe(true)
      expect(canViewAllExpenses(adminUser)).toBe(true)
      expect(canViewAllExpenses(financeUser)).toBe(true)
    })

    it('should return false for EMPLOYEE', () => {
      expect(canViewAllExpenses(employeeUser)).toBe(false)
    })
  })

  describe('canAccessAnalytics', () => {
    it('should return true for MANAGER, ADMIN, FINANCE', () => {
      expect(canAccessAnalytics(managerUser)).toBe(true)
      expect(canAccessAnalytics(adminUser)).toBe(true)
      expect(canAccessAnalytics(financeUser)).toBe(true)
    })

    it('should return false for EMPLOYEE', () => {
      expect(canAccessAnalytics(employeeUser)).toBe(false)
    })

    it('should return true for HR, matching the /analytics route guard', () => {
      expect(canAccessAnalytics({ userId: 5, role: 'HR', email: 'hr@test.com' })).toBe(true)
    })
  })

  describe('canEditExpense', () => {
    const ownPendingExpense = {
      userId: 1,
      status: 'PENDING',
    }

    const ownSubmittedExpense = {
      userId: 1,
      status: 'SUBMITTED',
    }

    const ownApprovedExpense = {
      userId: 1,
      status: 'APPROVED',
    }

    const otherPendingExpense = {
      userId: 2,
      status: 'PENDING',
    }

    it('should allow EMPLOYEE to edit own pending expense', () => {
      expect(canEditExpense(employeeUser, ownPendingExpense)).toBe(true)
    })

    it('should not allow EMPLOYEE to edit own submitted expense', () => {
      expect(canEditExpense(employeeUser, ownSubmittedExpense)).toBe(false)
    })

    it('should not allow EMPLOYEE to edit own approved expense', () => {
      expect(canEditExpense(employeeUser, ownApprovedExpense)).toBe(false)
    })

    it('should not allow EMPLOYEE to edit other user expense', () => {
      expect(canEditExpense(employeeUser, otherPendingExpense)).toBe(false)
    })

    it('should allow MANAGER to edit any pending expense', () => {
      expect(canEditExpense(managerUser, ownPendingExpense)).toBe(true)
      expect(canEditExpense(managerUser, otherPendingExpense)).toBe(true)
    })

    it('should allow MANAGER to edit submitted expenses', () => {
      expect(canEditExpense(managerUser, ownSubmittedExpense)).toBe(true)
    })

    it('should not allow MANAGER to edit approved expenses', () => {
      expect(canEditExpense(managerUser, ownApprovedExpense)).toBe(false)
    })

    it('should allow ADMIN to edit any pending/submitted expense', () => {
      expect(canEditExpense(adminUser, ownPendingExpense)).toBe(true)
      expect(canEditExpense(adminUser, ownSubmittedExpense)).toBe(true)
    })

    it('should not allow FINANCE to edit expenses', () => {
      expect(canEditExpense(financeUser, ownPendingExpense)).toBe(false)
    })
  })

  describe('canDeleteExpense', () => {
    const ownPendingExpense = {
      userId: 1,
      status: 'PENDING',
    }

    const ownApprovedExpense = {
      userId: 1,
      status: 'APPROVED',
    }

    it('should allow EMPLOYEE to delete own pending expense', () => {
      expect(canDeleteExpense(employeeUser, ownPendingExpense)).toBe(true)
    })

    it('should not allow EMPLOYEE to delete own approved expense', () => {
      expect(canDeleteExpense(employeeUser, ownApprovedExpense)).toBe(false)
    })

    it('should allow MANAGER to delete any pending expense', () => {
      expect(canDeleteExpense(managerUser, ownPendingExpense)).toBe(true)
    })

    it('should not allow MANAGER to delete approved expenses', () => {
      expect(canDeleteExpense(managerUser, ownApprovedExpense)).toBe(false)
    })
  })

  describe('canSubmitExpense', () => {
    const ownPendingExpense = {
      userId: 1,
      status: 'PENDING',
    }

    const ownSubmittedExpense = {
      userId: 1,
      status: 'SUBMITTED',
    }

    const otherPendingExpense = {
      userId: 2,
      status: 'PENDING',
    }

    it('should allow EMPLOYEE to submit own pending expense', () => {
      expect(canSubmitExpense(employeeUser, ownPendingExpense)).toBe(true)
    })

    it('should not allow EMPLOYEE to submit own submitted expense', () => {
      expect(canSubmitExpense(employeeUser, ownSubmittedExpense)).toBe(false)
    })

    it('should not allow EMPLOYEE to submit other user expense', () => {
      expect(canSubmitExpense(employeeUser, otherPendingExpense)).toBe(false)
    })
  })
})

