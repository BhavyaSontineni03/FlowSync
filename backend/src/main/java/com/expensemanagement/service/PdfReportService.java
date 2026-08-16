package com.expensemanagement.service;

import com.expensemanagement.model.Expense;
import com.expensemanagement.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final ExpenseRepository expenseRepository;

    public byte[] generateExpenseReport(Long organizationId, LocalDate startDate, LocalDate endDate) throws IOException {
        List<Expense> expenses = expenseRepository.findByOrganizationIdAndDateRange(organizationId, startDate, endDate);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(baos);
             com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
             com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdf)) {
            
            // Add title
            com.itextpdf.layout.element.Paragraph title = new com.itextpdf.layout.element.Paragraph("Expense Report")
                    .setFontSize(24)
                    .setBold()
                    .setMarginBottom(20);
            document.add(title);
            
            // Add date range
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
            com.itextpdf.layout.element.Paragraph dateRange = new com.itextpdf.layout.element.Paragraph(
                    String.format("Period: %s to %s", startDate.format(formatter), endDate.format(formatter)))
                    .setFontSize(12)
                    .setMarginBottom(20);
            document.add(dateRange);
            
            // Calculate totals
            BigDecimal totalAmount = expenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            long totalCount = expenses.size();
            long pendingCount = expenses.stream().filter(e -> e.getStatus() == Expense.ExpenseStatus.PENDING).count();
            long approvedCount = expenses.stream().filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED).count();
            long rejectedCount = expenses.stream().filter(e -> e.getStatus() == Expense.ExpenseStatus.REJECTED).count();
            
            // Add summary
            com.itextpdf.layout.element.Paragraph summary = new com.itextpdf.layout.element.Paragraph(
                    String.format("Total Expenses: $%.2f | Count: %d | Pending: %d | Approved: %d | Rejected: %d",
                            totalAmount, totalCount, pendingCount, approvedCount, rejectedCount))
                    .setFontSize(12)
                    .setBold()
                    .setMarginBottom(20);
            document.add(summary);
            
            // Add expenses table
            com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(5);
            table.setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100));
            
            // Header row
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new com.itextpdf.layout.element.Paragraph("Date").setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new com.itextpdf.layout.element.Paragraph("Description").setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new com.itextpdf.layout.element.Paragraph("Amount").setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new com.itextpdf.layout.element.Paragraph("Category").setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new com.itextpdf.layout.element.Paragraph("Status").setBold()));
            
            // Data rows
            for (Expense expense : expenses) {
                table.addCell(new com.itextpdf.layout.element.Cell().add(
                        new com.itextpdf.layout.element.Paragraph(expense.getExpenseDate().format(formatter))));
                table.addCell(new com.itextpdf.layout.element.Cell().add(
                        new com.itextpdf.layout.element.Paragraph(expense.getDescription())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(
                        new com.itextpdf.layout.element.Paragraph(String.format("$%.2f", expense.getAmount()))));
                table.addCell(new com.itextpdf.layout.element.Cell().add(
                        new com.itextpdf.layout.element.Paragraph(expense.getCategory().name())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(
                        new com.itextpdf.layout.element.Paragraph(expense.getStatus().name())));
            }
            
            document.add(table);
            
            // Add footer
            com.itextpdf.layout.element.Paragraph footer = new com.itextpdf.layout.element.Paragraph(
                    String.format("Generated on: %s", LocalDate.now().format(formatter)))
                    .setFontSize(10)
                    .setMarginTop(20)
                    .setItalic();
            document.add(footer);
        }
        
        return baos.toByteArray();
    }
}

