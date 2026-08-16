package com.expensemanagement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {
    
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;
    
    public String storeFile(MultipartFile file, Long organizationId, Long userId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            
            String filename = UUID.randomUUID().toString() + extension;
            Path organizationDir = Paths.get(uploadDir, "org_" + organizationId);
            Path userDir = organizationDir.resolve("user_" + userId);
            
            Files.createDirectories(userDir);
            
            Path targetLocation = userDir.resolve(filename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            String relativePath = "org_" + organizationId + "/user_" + userId + "/" + filename;
            log.info("File stored at: {}", relativePath);
            
            return relativePath;
        } catch (IOException ex) {
            log.error("Error storing file", ex);
            throw new RuntimeException("Could not store file: " + ex.getMessage(), ex);
        }
    }
    
    public Path loadFileAsPath(String filePath) {
        return Paths.get(uploadDir).resolve(filePath).normalize();
    }
    
    public boolean deleteFile(String filePath) {
        try {
            Path path = loadFileAsPath(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.error("Error deleting file: {}", filePath, ex);
            return false;
        }
    }
}

