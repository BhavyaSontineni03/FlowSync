package com.expensemanagement.service;

import com.expensemanagement.dto.UserUpsertRequest;
import com.expensemanagement.model.Organization;
import com.expensemanagement.model.User;
import com.expensemanagement.repository.OrganizationRepository;
import com.expensemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceFacade {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(UserUpsertRequest request, Long organizationId) {
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        User manager = resolveAssignee(request.getManagerId(), organizationId, Set.of(User.UserRole.MANAGER, User.UserRole.ADMIN));
        User hr = resolveAssignee(request.getHrId(), organizationId, Set.of(User.UserRole.HR, User.UserRole.ADMIN));

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .organization(org)
                .manager(manager)
                .hr(hr)
                .isOnBench(Boolean.TRUE.equals(request.getIsOnBench()))
                .monthlySalary(request.getMonthlySalary())
                .enabled(request.getEnabled() == null ? true : request.getEnabled())
                .build();
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(Long id, UserUpsertRequest request, Long organizationId) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Forbidden");
        }
        if (!user.getEmail().equals(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setIsOnBench(Boolean.TRUE.equals(request.getIsOnBench()));
        user.setMonthlySalary(request.getMonthlySalary());
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        User manager = resolveAssignee(request.getManagerId(), organizationId, Set.of(User.UserRole.MANAGER, User.UserRole.ADMIN));
        User hr = resolveAssignee(request.getHrId(), organizationId, Set.of(User.UserRole.HR, User.UserRole.ADMIN));
        user.setManager(manager);
        user.setHr(hr);
        return userRepository.save(user);
    }

    @Transactional
    public void disableUser(Long id, Long organizationId) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Forbidden");
        }
        user.setEnabled(false);
        userRepository.save(user);
    }

    private User resolveAssignee(Long id, Long organizationId, Set<User.UserRole> allowedRoles) {
        if (id == null) return null;
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getOrganization().getId().equals(organizationId)) {
            throw new RuntimeException("Assignee must belong to organization");
        }
        if (!allowedRoles.contains(user.getRole())) {
            throw new RuntimeException("Assignee role not permitted");
        }
        return user;
    }
}
