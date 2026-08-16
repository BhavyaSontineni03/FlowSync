package com.expensemanagement.service;

import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for asynchronous OCR processing.
 * Processes receipt OCR in the background to avoid blocking the API request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncOcrService {

    private final OcrService ocrService;
    private final ExpenseRepository expenseRepository;
    private final ActivityLogService activityLogService;

    /**
     * Process OCR asynchronously for an expense.
     * This method runs in a separate thread to avoid blocking the main request.
     * 
     * @param expenseId Expense ID
     * @param receiptFile Receipt file
     * @param userId User ID
     * @param organizationId Organization ID
     */
    @Async
    @Transactional
    public void processOcrAsync(Long expenseId, MultipartFile receiptFile, Long userId, Long organizationId) {
        try {
            log.info("Starting async OCR processing for expense {}", expenseId);
            
            Expense expense = expenseRepository.findById(expenseId)
                    .orElseThrow(() -> new RuntimeException("Expense not found: " + expenseId));

            String ocrText = ocrService.extractTextFromImage(receiptFile);
            OcrService.OcrResult ocrResult = ocrService.extractExpenseData(ocrText);
            
            expense.setOcrExtractedData(ocrText);

            // Auto-fill if OCR found data and fields are empty
            boolean updated = false;
            if (ocrResult.getAmount() != null && expense.getAmount() == null) {
                expense.setAmount(ocrResult.getAmount());
                updated = true;
            }
            if (ocrResult.getDate() != null && expense.getExpenseDate() == null) {
                expense.setExpenseDate(ocrResult.getDate());
                updated = true;
            }
            if (ocrResult.getDescription() != null && 
                (expense.getDescription() == null || expense.getDescription().isEmpty())) {
                expense.setDescription(ocrResult.getDescription());
                updated = true;
            }

            if (updated) {
                expenseRepository.save(expense);
                log.info("OCR processing completed and expense updated for expense {}", expenseId);
            } else {
                log.info("OCR processing completed but no updates needed for expense {}", expenseId);
            }

            activityLogService.logActivity(
                    com.expensemanagement.model.ActivityLog.ActivityType.OCR_PROCESSED,
                    "OCR processed asynchronously for expense receipt",
                    expense.getUser(),
                    expense.getOrganization(),
                    "Expense",
                    expense.getId(),
                    "Amount: " + ocrResult.getAmount() + ", Date: " + ocrResult.getDate()
            );

        } catch (Exception e) {
            log.error("Error processing OCR asynchronously for expense {}", expenseId, e);
            // Could send notification to user about OCR failure
        }
    }
}

