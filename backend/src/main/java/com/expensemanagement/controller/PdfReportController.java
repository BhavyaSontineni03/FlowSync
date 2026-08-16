package com.expensemanagement.controller;

import com.expensemanagement.security.JwtTokenProvider;
import com.expensemanagement.service.PdfReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class PdfReportController {

    private final PdfReportService pdfReportService;
    private final JwtTokenProvider tokenProvider;

    @GetMapping("/expenses/pdf")
    public ResponseEntity<byte[]> generateExpenseReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpServletRequest request) throws Exception {

        String token = getTokenFromRequest(request);
        String userRole = tokenProvider.getRoleFromToken(token);

        // Only MANAGER, ADMIN, and FINANCE can generate reports
        if (!isReportRole(userRole)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        Long organizationId = tokenProvider.getOrganizationIdFromToken(token);

        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        byte[] pdfBytes = pdfReportService.generateExpenseReport(organizationId, startDate, endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", 
                "expense_report_" + LocalDate.now() + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isReportRole(String role) {
        return "MANAGER".equals(role) || "ADMIN".equals(role) || "FINANCE".equals(role);
    }
}

