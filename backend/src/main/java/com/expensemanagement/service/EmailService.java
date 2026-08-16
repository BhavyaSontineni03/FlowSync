package com.expensemanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@expensemanagement.com}")
    private String fromEmail;

    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
        }
    }

    @Async
    public void sendExpenseApprovalRequest(String to, String employeeName, String expenseDescription, String amount) {
        String subject = "Expense Approval Required";
        String body = String.format(
                "Dear Manager,\n\n" +
                "%s has submitted an expense for your approval:\n\n" +
                "Description: %s\n" +
                "Amount: $%s\n\n" +
                "Please review and approve or reject this expense in the system.\n\n" +
                "Thank you,\n" +
                "Expense Management System",
                employeeName, expenseDescription, amount
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendExpenseApproved(String to, String expenseDescription, String amount, String approverName) {
        String subject = "Expense Approved";
        String body = String.format(
                "Dear Employee,\n\n" +
                "Your expense has been approved:\n\n" +
                "Description: %s\n" +
                "Amount: $%s\n" +
                "Approved by: %s\n\n" +
                "Thank you,\n" +
                "Expense Management System",
                expenseDescription, amount, approverName
        );
        sendEmail(to, subject, body);
    }

    @Async
    public void sendExpenseRejected(String to, String expenseDescription, String amount, String approverName, String comments) {
        String subject = "Expense Rejected";
        String body = String.format(
                "Dear Employee,\n\n" +
                "Your expense has been rejected:\n\n" +
                "Description: %s\n" +
                "Amount: $%s\n" +
                "Rejected by: %s\n" +
                "Comments: %s\n\n" +
                "Please review and resubmit if needed.\n\n" +
                "Thank you,\n" +
                "Expense Management System",
                expenseDescription, amount, approverName, comments != null ? comments : "No comments provided"
        );
        sendEmail(to, subject, body);
    }
    
    @Async
    public void sendExpensePaid(String to, String expenseDescription, String amount) {
        String subject = "Expense Payment Confirmed";
        String body = String.format(
                "Dear Employee,\n\n" +
                "Your expense has been marked as paid:\n\n" +
                "Description: %s\n" +
                "Amount: $%s\n\n" +
                "The payment has been processed by the Finance team.\n\n" +
                "Thank you,\n" +
                "Expense Management System",
                expenseDescription, amount
        );
        sendEmail(to, subject, body);
    }
}

