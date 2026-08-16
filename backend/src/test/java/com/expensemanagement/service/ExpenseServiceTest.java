package com.expensemanagement.service;

import com.expensemanagement.dto.ExpenseDto;
import com.expensemanagement.event.EventPublisher;
import com.expensemanagement.event.ExpenseSubmittedEvent;
import com.expensemanagement.model.Expense;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.ExpenseRepository;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private FileStorageService fileStorageService;

    @Mock
    private OcrService ocrService;

    @Mock
    private AsyncOcrService asyncOcrService;

    @Mock
    private WebSocketService webSocketService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ExpenseService expenseService;

    private Organization testOrganization;
    private User testUser;
    private Expense testExpense;
    private ExpenseDto testExpenseDto;

    @BeforeEach
    void setUp() {
        fileStorageService = new NoOpFileStorageService();
        expenseService = new ExpenseService(
                expenseRepository,
                userRepository,
                organizationRepository,
                fileStorageService,
                ocrService,
                asyncOcrService,
                activityLogService,
                webSocketService,
                eventPublisher,
                applicationEventPublisher
        );

        testOrganization = Organization.builder()
                .id(1L)
                .name("Test Org")
                .subdomain("testorg")
                .address("123 Test St")
                .contactEmail("test@org.com")
                .contactPhone("1234567890")
                .build();

        // submitExpense requires an assigned manager for the approval chain.
        User testManager = User.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Manager")
                .email("jane@test.com")
                .password("password")
                .role(User.UserRole.MANAGER)
                .organization(testOrganization)
                .enabled(true)
                .build();

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .password("password")
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrganization)
                .manager(testManager)
                .enabled(true)
                .build();

        testExpense = Expense.builder()
                .id(1L)
                .description("Test Expense")
                .amount(new BigDecimal("100.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.TRAVEL)
                .status(Expense.ExpenseStatus.PENDING)
                .notes("Test notes")
                .user(testUser)
                .organization(testOrganization)
                .build();

        testExpenseDto = ExpenseDto.builder()
                .description("Test Expense")
                .amount(new BigDecimal("100.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.TRAVEL)
                .notes("Test notes")
                .build();
    }

    private static class NoOpFileStorageService extends FileStorageService {
        @Override
        public String storeFile(MultipartFile file, Long organizationId, Long userId) {
            return "mock/path";
        }

        @Override
        public boolean deleteFile(String filePath) {
            return true;
        }
    }

    @Test
    void testCreateExpense_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrganization));
        when(expenseRepository.save(any(Expense.class))).thenReturn(testExpense);

        // When
        ExpenseDto result = expenseService.createExpense(testExpenseDto, 1L, 1L, null);

        // Then
        assertNotNull(result);
        assertEquals("Test Expense", result.getDescription());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
        verify(expenseRepository, times(1)).save(any(Expense.class));
        verify(activityLogService, times(1)).logActivity(any(), any(), any(), any(), any(), any(), any());
        verify(webSocketService, times(1)).sendExpenseUpdate(eq(1L), any());
    }

    @Test
    void testCreateExpense_UserNotFound() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.createExpense(testExpenseDto, 1L, 1L, null);
        });
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void testCreateExpense_UserNotInOrganization() {
        // Given
        Organization otherOrg = Organization.builder().id(2L).name("Other Org").build();
        User userInOtherOrg = User.builder()
                .id(1L)
                .organization(otherOrg)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(userInOtherOrg));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(testOrganization));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.createExpense(testExpenseDto, 1L, 1L, null);
        });
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void testUpdateExpense_Success() {
        // Given
        ExpenseDto updateDto = ExpenseDto.builder()
                .description("Updated Expense")
                .amount(new BigDecimal("200.00"))
                .expenseDate(LocalDate.now())
                .category(Expense.ExpenseCategory.MEALS)
                .notes("Updated notes")
                .build();

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(expenseRepository.save(any(Expense.class))).thenReturn(testExpense);

        // When
        ExpenseDto result = expenseService.updateExpense(1L, updateDto, 1L, 1L);

        // Then
        assertNotNull(result);
        verify(expenseRepository, times(1)).save(any(Expense.class));
        verify(activityLogService, times(1)).logActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateExpense_NotAuthorized() {
        // Given
        User otherUser = User.builder()
                .id(2L)
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrganization)
                .build();
        ExpenseDto updateDto = ExpenseDto.builder().build();

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.updateExpense(1L, updateDto, 2L, 1L);
        });
    }

    @Test
    void testUpdateExpense_AlreadyApproved() {
        // Given
        testExpense.setStatus(Expense.ExpenseStatus.APPROVED);
        ExpenseDto updateDto = ExpenseDto.builder().build();

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.updateExpense(1L, updateDto, 1L, 1L);
        });
        
        // Verify userRepository was not called since exception is thrown before
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void testDeleteExpense_Success() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // When
        expenseService.deleteExpense(1L, 1L, 1L);

        // Then
        verify(expenseRepository, times(1)).delete(testExpense);
        verify(activityLogService, times(1)).logActivity(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDeleteExpense_NotAuthorized() {
        // Given
        User otherUser = User.builder()
                .id(2L)
                .role(User.UserRole.EMPLOYEE)
                .organization(testOrganization)
                .build();

        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.deleteExpense(1L, 2L, 1L);
        });
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void testSubmitExpense_Success() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));
        when(expenseRepository.save(any(Expense.class))).thenReturn(testExpense);

        // When
        ExpenseDto result = expenseService.submitExpense(1L, 1L, 1L);

        // Then
        assertNotNull(result);
        assertEquals(Expense.ExpenseStatus.SUBMITTED, testExpense.getStatus());
        verify(expenseRepository, times(1)).save(any(Expense.class));
        // Side effects run in ExpenseSubmissionEventListener after commit.
        verify(applicationEventPublisher, times(1))
                .publishEvent(argThat((ExpenseSubmittedEvent evt) ->
                        evt.expenseId().equals(1L) && evt.organizationId().equals(1L)));
        verify(activityLogService, never()).logActivity(any(), any(), any(), any(), any(), any(), any());
        verify(webSocketService, never()).sendExpenseUpdate(anyLong(), any());
    }

    @Test
    void testSubmitExpense_NotOwner() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.submitExpense(1L, 2L, 1L);
        });
    }

    @Test
    void testSubmitExpense_MismatchedOrganization_Fails() {
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                expenseService.submitExpense(1L, 1L, 999L));

        assertEquals("Expense does not belong to this organization", ex.getMessage());
        verify(expenseRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any(), any(), any(), any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void testSubmitExpense_AlreadySubmitted() {
        // Given
        testExpense.setStatus(Expense.ExpenseStatus.SUBMITTED);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.submitExpense(1L, 1L, 1L);
        });
    }

    @Test
    void testGetExpenses_ByUserId() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<Expense> expenses = Arrays.asList(testExpense);
        Page<Expense> expensePage = new PageImpl<>(expenses, pageable, 1);

        when(expenseRepository.findByUserId(1L, pageable)).thenReturn(expensePage);

        // When
        Page<ExpenseDto> result = expenseService.getExpenses(1L, 1L, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(expenseRepository, times(1)).findByUserId(1L, pageable);
    }

    @Test
    void testGetExpenses_ByOrganization() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        List<Expense> expenses = Arrays.asList(testExpense);
        Page<Expense> expensePage = new PageImpl<>(expenses, pageable, 1);

        when(expenseRepository.findByOrganizationId(1L, pageable)).thenReturn(expensePage);

        // When
        Page<ExpenseDto> result = expenseService.getExpenses(1L, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(expenseRepository, times(1)).findByOrganizationId(1L, pageable);
    }

    @Test
    void testGetExpenseById_Success() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When
        ExpenseDto result = expenseService.getExpenseById(1L, 1L);

        // Then
        assertNotNull(result);
        assertEquals("Test Expense", result.getDescription());
    }

    @Test
    void testGetExpenseById_NotFound() {
        // Given
        when(expenseRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.getExpenseById(1L, 1L);
        });
    }

    @Test
    void testGetExpenseById_WrongOrganization() {
        // Given
        Organization otherOrg = Organization.builder().id(2L).build();
        testExpense.setOrganization(otherOrg);
        when(expenseRepository.findById(1L)).thenReturn(Optional.of(testExpense));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            expenseService.getExpenseById(1L, 1L);
        });
    }
}

