package com.expensemanagement.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OcrService {
    
    @Value("${app.tesseract.data-path}")
    private String tessDataPath;
    
    @Value("${app.tesseract.language}")
    private String language;
    
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:total|amount|sum|\\$|€|£|₹)\\s*:?\\s*([0-9]+[.,]?[0-9]*\\.?[0-9]{0,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})|(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})");
    
    public String extractTextFromImage(MultipartFile file) {
        try {
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataPath);
            tesseract.setLanguage(language);
            tesseract.setPageSegMode(1);
            tesseract.setOcrEngineMode(1);
            
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IOException("Could not read image from file");
            }
            
            String extractedText = tesseract.doOCR(image);
            log.info("OCR extracted text: {}", extractedText);
            return extractedText;
        } catch (TesseractException | IOException e) {
            log.error("Error during OCR processing", e);
            throw new RuntimeException("Failed to process OCR: " + e.getMessage(), e);
        }
    }
    
    public OcrResult extractExpenseData(String ocrText) {
        OcrResult result = new OcrResult();
        result.setRawText(ocrText);
        
        // Extract amount
        BigDecimal amount = extractAmount(ocrText);
        if (amount != null) {
            result.setAmount(amount);
        }
        
        // Extract date
        LocalDate date = extractDate(ocrText);
        if (date != null) {
            result.setDate(date);
        }
        
        // Extract description (first few lines or merchant name)
        String description = extractDescription(ocrText);
        if (description != null && !description.isEmpty()) {
            result.setDescription(description);
        }
        
        return result;
    }
    
    private BigDecimal extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        BigDecimal maxAmount = null;
        
        while (matcher.find()) {
            try {
                String amountStr = matcher.group(1).replace(",", "");
                BigDecimal amount = new BigDecimal(amountStr);
                if (maxAmount == null || amount.compareTo(maxAmount) > 0) {
                    maxAmount = amount;
                }
            } catch (NumberFormatException e) {
                log.warn("Could not parse amount: {}", matcher.group(1));
            }
        }
        
        return maxAmount;
    }
    
    private LocalDate extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        
        while (matcher.find()) {
            String dateStr = matcher.group();
            try {
                // Try MM/dd/yyyy or dd/MM/yyyy
                String[] formats = {
                    "MM/dd/yyyy", "dd/MM/yyyy", "yyyy/MM/dd",
                    "MM-dd-yyyy", "dd-MM-yyyy", "yyyy-MM-dd",
                    "MM/dd/yy", "dd/MM/yy", "yy/MM/dd"
                };
                
                for (String format : formats) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(format);
                        sdf.setLenient(false);
                        java.util.Date date = sdf.parse(dateStr);
                        return LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
                    } catch (ParseException ignored) {
                        // Try next format
                    }
                }
            } catch (Exception e) {
                log.warn("Could not parse date: {}", dateStr);
            }
        }
        
        return null;
    }
    
    private String extractDescription(String text) {
        String[] lines = text.split("\n");
        StringBuilder description = new StringBuilder();
        
        // Take first 2-3 non-empty lines that don't look like amounts or dates
        int count = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.length() < 3) continue;
            if (AMOUNT_PATTERN.matcher(line).find() || DATE_PATTERN.matcher(line).find()) continue;
            
            if (count < 2) {
                if (description.length() > 0) description.append(" - ");
                description.append(line);
                count++;
            }
        }
        
        return description.length() > 0 ? description.toString() : null;
    }
    
    public static class OcrResult {
        private String rawText;
        private BigDecimal amount;
        private LocalDate date;
        private String description;
        
        public String getRawText() { return rawText; }
        public void setRawText(String rawText) { this.rawText = rawText; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

