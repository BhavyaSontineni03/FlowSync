package com.expensemanagement.controller;

import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import com.expensemanagement.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private Organization testOrg;
    private User adminUser;
    private User hrUser;
    private String adminToken;
    private String hrToken;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .name("Test Org")
                .subdomain("testorg")
                .address("123 Test St")
                .contactEmail("contact@testorg.com")
                .contactPhone("1234567890")
                .build();
        testOrg = organizationRepository.save(testOrg);

        adminUser = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@test.com")
                .password(passwordEncoder.encode("password"))
                .role(User.UserRole.ADMIN)
                .organization(testOrg)
                .enabled(true)
                .build();
        adminUser = userRepository.save(adminUser);
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                adminUser.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + adminUser.getRole().name()))
        );
        adminToken = tokenProvider.generateToken(adminAuth, adminUser.getId(), testOrg.getId(), adminUser.getRole().name());

        hrUser = User.builder()
                .firstName("HR")
                .lastName("User")
                .email("hr@test.com")
                .password(passwordEncoder.encode("password"))
                .role(User.UserRole.HR)
                .organization(testOrg)
                .enabled(true)
                .build();
        hrUser = userRepository.save(hrUser);
        Authentication hrAuth = new UsernamePasswordAuthenticationToken(
                hrUser.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + hrUser.getRole().name()))
        );
        hrToken = tokenProvider.generateToken(hrAuth, hrUser.getId(), testOrg.getId(), hrUser.getRole().name());
    }

    @Test
    void testCreateProject_AsAdmin_Success() throws Exception {
        Map<String, Object> project = new HashMap<>();
        project.put("code", "PROJ001");
        project.put("name", "Test Project");
        project.put("description", "Test Description");
        project.put("startDate", LocalDate.now().toString());
        project.put("endDate", LocalDate.now().plusMonths(6).toString());
        project.put("status", "ACTIVE");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROJ001"))
                .andExpect(jsonPath("$.name").value("Test Project"));
    }

    @Test
    void testCreateProject_AsHR_Forbidden() throws Exception {
        // Project management is Admin-only across both the API
        // (ProjectController.createProject) and the frontend module config
        // (roles: ['ADMIN'] in config/modules.js) -- HR can view projects
        // but not create them.
        Map<String, Object> project = new HashMap<>();
        project.put("code", "PROJ002");
        project.put("name", "HR Project");
        project.put("startDate", LocalDate.now().toString());
        project.put("status", "ACTIVE");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCreateProject_AsEmployee_Forbidden() throws Exception {
        User employee = User.builder()
                .firstName("Employee")
                .lastName("User")
                .email("emp@test.com")
                .password(passwordEncoder.encode("password"))
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrg)
                .enabled(true)
                .build();
        employee = userRepository.save(employee);
        Authentication empAuth = new UsernamePasswordAuthenticationToken(
                employee.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name()))
        );
        String empToken = tokenProvider.generateToken(empAuth, employee.getId(), testOrg.getId(), employee.getRole().name());

        Map<String, Object> project = new HashMap<>();
        project.put("code", "PROJ003");
        project.put("name", "Employee Project");
        project.put("startDate", LocalDate.now().toString());
        project.put("status", "ACTIVE");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + empToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetProjects_AsAnyUser_Success() throws Exception {
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}

