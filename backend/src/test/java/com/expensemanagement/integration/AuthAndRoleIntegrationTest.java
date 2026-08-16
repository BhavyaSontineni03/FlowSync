package com.expensemanagement.integration;

import com.expensemanagement.dto.AuthRequest;
import com.expensemanagement.dto.AuthResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthAndRoleIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.driverClassName", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // This test doesn't use the "test" profile (it wires its own H2
        // datasource above), so the Kafka listener auto-startup guard in
        // application-test.yml doesn't apply here -- set it directly so
        // this context doesn't spend the test run retrying a connection to
        // a broker that was never going to exist in this environment.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void onlyAdminCanCreateProject_employeeAndHrAreForbidden() throws Exception {
        // Login as employee
        String employeeToken = loginAndGetToken("employee1@techcorp.com", "employee123");

        // Attempt to create a project as employee (should be forbidden)
        String projectPayload = """
                {
                  "code": "TEST-EMP-BLOCK",
                  "name": "Should Fail",
                  "description": "Employee should not be able to create",
                  "startDate": "2024-01-01",
                  "endDate": "2024-12-31",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + employeeToken)
                        .content(projectPayload))
                .andExpect(status().isForbidden());

        // Login as HR -- project management is Admin-only (see
        // ProjectController.createProject and the frontend's
        // roles: ['ADMIN'] project module config), so HR is blocked too.
        String hrToken = loginAndGetToken("hr1@techcorp.com", "hr123");

        String hrProjectPayload = """
                {
                  "code": "TEST-HR-BLOCK",
                  "name": "Should Also Fail",
                  "description": "HR should not be able to create either",
                  "startDate": "2024-02-01",
                  "endDate": "2024-12-31",
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + hrToken)
                        .content(hrProjectPayload))
                .andExpect(status().isForbidden());

        // Login as Admin -- the only role allowed to create projects.
        String adminToken = loginAndGetToken("admin@techcorp.com", "admin123");

        String adminProjectPayload = """
                {
                  "code": "TEST-ADMIN-OK",
                  "name": "Admin Created Project",
                  "description": "Admin is allowed to create",
                  "startDate": "2024-02-01",
                  "endDate": "2024-12-31",
                  "status": "ACTIVE"
                }
                """;

        String projectResponse = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + adminToken)
                        .content(adminProjectPayload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(projectResponse);
        assertThat(created.path("code").asText()).isEqualTo("TEST-ADMIN-OK");
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        AuthRequest request = new AuthRequest(email, password, null);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        return authResponse.getToken();
    }
}
