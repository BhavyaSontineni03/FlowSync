package com.expensemanagement.service;

import com.expensemanagement.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled service for automatic payroll generation.
 * Generates payroll for all employees at the beginning of each month.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayrollSchedulerService {
    
    private final PayrollService payrollService;
    private final OrganizationRepository organizationRepository;
    
    /**
     * Automatically generate payroll for all employees on the 1st day of each month.
     * This runs at 2 AM on the 1st of every month to generate payroll for the previous month.
     * 
     * Cron format: second minute hour day month day-of-week
     * "0 0 2 1 * ?" = 2:00 AM on the 1st day of every month
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void generateMonthlyPayroll() {
        log.info("Starting automatic payroll generation for previous month");
        
        try {
            // Get previous month and year
            LocalDate now = LocalDate.now();
            int previousMonth = now.minusMonths(1).getMonthValue();
            int previousYear = now.minusMonths(1).getYear();
            
            log.info("Generating payroll for month: {}/{}", previousMonth, previousYear);
            
            // Get all organizations and generate payroll for each
            organizationRepository.findAll().forEach(organization -> {
                try {
                    log.info("Generating payroll for organization: {} ({})", organization.getId(), organization.getName());
                    payrollService.generatePayrollForOrganization(previousMonth, previousYear, organization.getId());
                    log.info("Successfully generated payroll for organization: {} ({})", organization.getId(), organization.getName());
                } catch (Exception e) {
                    log.error("Error generating payroll for organization {} ({}): {}", 
                            organization.getId(), organization.getName(), e.getMessage(), e);
                    // Continue with other organizations even if one fails
                }
            });
            
            log.info("Completed automatic payroll generation for month: {}/{}", previousMonth, previousYear);
        } catch (Exception e) {
            log.error("Error in automatic payroll generation: {}", e.getMessage(), e);
        }
    }
}
