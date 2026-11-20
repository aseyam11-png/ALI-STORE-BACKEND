package com.store.admin.service;

import com.store.admin.dto.UpdateUserRoleRequest;
import com.store.admin.dto.UserResponse;
import com.store.common.exception.ResourceNotFoundException;
import com.store.common.exception.BusinessException;
import com.store.user.entity.Role;
import com.store.user.entity.User;
import com.store.user.repository.RoleRepository;
import com.store.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUserRole(Long userId, UpdateUserRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        if (isEnabledAdmin(user) && !"ROLE_ADMIN".equals(role.getName())) {
            ensureAnotherEnabledAdminRemains();
        }
        user.setRole(role);
        User updatedUser = userRepository.save(user);

        return mapToUserResponse(updatedUser);
    }

    @Transactional
    public UserResponse toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (isEnabledAdmin(user)) {
            ensureAnotherEnabledAdminRemains();
        }
        user.setIsEnabled(!user.getIsEnabled());
        User updatedUser = userRepository.save(user);

        return mapToUserResponse(updatedUser);
    }

    private boolean isEnabledAdmin(User user) {
        return Boolean.TRUE.equals(user.getIsEnabled())
                && user.getRole() != null
                && "ROLE_ADMIN".equals(user.getRole().getName());
    }

    private void ensureAnotherEnabledAdminRemains() {
        if (userRepository.findEnabledAdminsForUpdate().size() <= 1) {
            throw new BusinessException("Cannot disable or demote the last enabled administrator");
        }
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .isEnabled(user.getIsEnabled())
                .role(user.getRole().getName())
                .build();
    }
}