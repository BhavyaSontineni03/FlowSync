package com.expensemanagement.service;

import com.expensemanagement.model.LeaveBalance;
import com.expensemanagement.model.LeaveRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.LeaveBalanceRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing leave balances.
 * Handles allocation, usage tracking, and balance checks.
 * 
 * Industry Standard:
 * - Unpaid Leave: Fixed 12 days per year (as per many Indian IT companies)
 * - Paid Leave/Vacation: 20 days per year
 * - Sick Leave: 10 days per year
 * - Personal Leave: 5 days per year
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveBalanceService {

    private final LeaveBalanceRepository leaveBalanceRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    // Default allocations (can be made configurable per organization)
    private static final int DEFAULT_PAID_LEAVE = 20;
    private static final int DEFAULT_UNPAID_LEAVE = 12; // Fixed as per requirement
    private static final int DEFAULT_SICK_LEAVE = 10;
    private static final int DEFAULT_PERSONAL_LEAVE = 5;

    /**
     * Get or create leave balance for a user for the current year.
     */
    @Transactional
    public LeaveBalance getOrCreateBalance(Long userId, Integer year) {
        return leaveBalanceRepository.findByUserIdAndYear(userId, year)
                .orElseGet(() -> createBalanceForUser(userId, year));
    }

    /**
     * Create initial leave balance for a user for a year.
     */
    @Transactional
    public LeaveBalance createBalanceForUser(Long userId, Integer year) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LeaveBalance balance = LeaveBalance.builder()
                .user(user)
                .organization(user.getOrganization())
                .year(year)
                .paidLeaveAllocated(DEFAULT_PAID_LEAVE)
                .paidLeaveUsed(0)
                .unpaidLeaveAllocated(DEFAULT_UNPAID_LEAVE)
                .unpaidLeaveUsed(0)
                .sickLeaveAllocated(DEFAULT_SICK_LEAVE)
                .sickLeaveUsed(0)
                .personalLeaveAllocated(DEFAULT_PERSONAL_LEAVE)
                .personalLeaveUsed(0)
                .build();

        return leaveBalanceRepository.save(balance);
    }

    /**
     * Check if user has sufficient leave balance for a request.
     * Returns a map with validation result and remaining balance.
     */
    public Map<String, Object> checkLeaveBalance(Long userId, LeaveRequest.LeaveType leaveType, int daysRequested) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateBalance(userId, year);

        Map<String, Object> result = new HashMap<>();
        int remaining = 0;
        boolean sufficient = false;

        switch (leaveType) {
            case VACATION:
                remaining = balance.getPaidLeaveRemaining();
                sufficient = remaining >= daysRequested;
                result.put("category", "Paid Leave");
                break;
            case SICK_LEAVE:
                remaining = balance.getSickLeaveRemaining();
                sufficient = remaining >= daysRequested;
                result.put("category", "Sick Leave");
                break;
            case PERSONAL_LEAVE:
                remaining = balance.getPersonalLeaveRemaining();
                sufficient = remaining >= daysRequested;
                result.put("category", "Personal Leave");
                break;
            case UNPAID_LEAVE:
                remaining = balance.getUnpaidLeaveRemaining();
                sufficient = remaining >= daysRequested;
                result.put("category", "Unpaid Leave");
                break;
            default:
                // For maternity, paternity, bereavement - usually unlimited or special case
                remaining = Integer.MAX_VALUE;
                sufficient = true;
                result.put("category", "Special Leave");
        }

        result.put("sufficient", sufficient);
        result.put("remaining", remaining);
        result.put("requested", daysRequested);
        result.put("leaveType", leaveType.name());

        return result;
    }

    /**
     * Deduct leave days from balance when leave is approved.
     */
    @Transactional
    public void deductLeave(Long userId, LeaveRequest.LeaveType leaveType, int days) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateBalance(userId, year);

        switch (leaveType) {
            case VACATION:
                balance.setPaidLeaveUsed(balance.getPaidLeaveUsed() + days);
                break;
            case SICK_LEAVE:
                balance.setSickLeaveUsed(balance.getSickLeaveUsed() + days);
                break;
            case PERSONAL_LEAVE:
                balance.setPersonalLeaveUsed(balance.getPersonalLeaveUsed() + days);
                break;
            case UNPAID_LEAVE:
                balance.setUnpaidLeaveUsed(balance.getUnpaidLeaveUsed() + days);
                break;
            default:
                // Special leaves don't deduct from regular balance
                log.info("Special leave type {} - no deduction from regular balance", leaveType);
        }

        leaveBalanceRepository.save(balance);
    }

    /**
     * Restore leave days to balance when leave is cancelled/rejected.
     */
    @Transactional
    public void restoreLeave(Long userId, LeaveRequest.LeaveType leaveType, int days) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateBalance(userId, year);

        switch (leaveType) {
            case VACATION:
                balance.setPaidLeaveUsed(Math.max(0, balance.getPaidLeaveUsed() - days));
                break;
            case SICK_LEAVE:
                balance.setSickLeaveUsed(Math.max(0, balance.getSickLeaveUsed() - days));
                break;
            case PERSONAL_LEAVE:
                balance.setPersonalLeaveUsed(Math.max(0, balance.getPersonalLeaveUsed() - days));
                break;
            case UNPAID_LEAVE:
                balance.setUnpaidLeaveUsed(Math.max(0, balance.getUnpaidLeaveUsed() - days));
                break;
            default:
                log.info("Special leave type {} - no restoration needed", leaveType);
        }

        leaveBalanceRepository.save(balance);
    }

    /**
     * Get complete leave balance summary for a user.
     */
    public Map<String, Object> getBalanceSummary(Long userId) {
        int year = LocalDate.now().getYear();
        LeaveBalance balance = getOrCreateBalance(userId, year);

        Map<String, Object> summary = new HashMap<>();
        summary.put("year", year);
        summary.put("userId", userId);

        // Paid Leave
        Map<String, Integer> paidLeave = new HashMap<>();
        paidLeave.put("allocated", balance.getPaidLeaveAllocated());
        paidLeave.put("used", balance.getPaidLeaveUsed());
        paidLeave.put("remaining", balance.getPaidLeaveRemaining());
        summary.put("paidLeave", paidLeave);

        // Unpaid Leave (fixed 12)
        Map<String, Integer> unpaidLeave = new HashMap<>();
        unpaidLeave.put("allocated", balance.getUnpaidLeaveAllocated());
        unpaidLeave.put("used", balance.getUnpaidLeaveUsed());
        unpaidLeave.put("remaining", balance.getUnpaidLeaveRemaining());
        summary.put("unpaidLeave", unpaidLeave);

        // Sick Leave
        Map<String, Integer> sickLeave = new HashMap<>();
        sickLeave.put("allocated", balance.getSickLeaveAllocated());
        sickLeave.put("used", balance.getSickLeaveUsed());
        sickLeave.put("remaining", balance.getSickLeaveRemaining());
        summary.put("sickLeave", sickLeave);

        // Personal Leave
        Map<String, Integer> personalLeave = new HashMap<>();
        personalLeave.put("allocated", balance.getPersonalLeaveAllocated());
        personalLeave.put("used", balance.getPersonalLeaveUsed());
        personalLeave.put("remaining", balance.getPersonalLeaveRemaining());
        summary.put("personalLeave", personalLeave);

        // Totals
        summary.put("totalAllocated", balance.getTotalAllocated());
        summary.put("totalUsed", balance.getTotalUsed());
        summary.put("totalRemaining", balance.getTotalRemaining());

        return summary;
    }
}
