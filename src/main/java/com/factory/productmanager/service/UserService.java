package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;

import com.factory.productmanager.exception.ResourceNotFoundException;

import com.factory.productmanager.model.dto.ChangePasswordRequest;

import com.factory.productmanager.model.dto.CreateUserRequest;

import com.factory.productmanager.model.dto.UpdateUserPermissionsRequest;

import com.factory.productmanager.model.dto.UserDirectoryResponse;
import com.factory.productmanager.model.dto.UserResponse;

import com.factory.productmanager.model.entity.Role;

import com.factory.productmanager.model.entity.User;

import com.factory.productmanager.repository.TaskRepository;
import com.factory.productmanager.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.UUID;

@Service

@Transactional

@RequiredArgsConstructor

public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final PermissionService permissionService;

    private final UserAdministrationPolicy administration;
    private final com.factory.productmanager.repository.UserAccessEventRepository accessEvents;
    private final TaskRepository taskRepository;
    private final com.factory.productmanager.repository.PerformancePeriodRepository performancePeriodRepository;

    public UserResponse createUser(CreateUserRequest request) {
        var locked = administration.lockAndRequireManager();
        var actor = administration.currentManager(locked);

        if (userRepository.existsByUsername(request.getUsername())) {

            throw new DuplicateResourceException(

                    "کاربری با نام کاربری '" + request.getUsername() + "' از قبل وجود دارد"

            );

        }

        User user = new User();

        user.setUsername(request.getUsername());

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setFullName(request.getFullName());

        user.setRole(request.getRole());

        user.setPermissions(permissionService.sanitizePermissionsForRole(request.getRole(), request.getPermissions()));

        User saved = userRepository.save(user);

        administration.record(actor, saved, "CREATED", "{}", administration.snapshot(saved));
        return toResponse(saved);

    }

    public UserResponse updateUserPermissions(UUID id, UpdateUserPermissionsRequest request) {
        var locked = administration.lockAndRequireManager();
        User user = administration.target(locked, id);
        if (user.getRole() == Role.MANAGER) {
            throw new DuplicateResourceException("مدیر همه دسترسی‌ها را دارد؛ برای محدودکردن ابتدا نقش را تغییر دهید");
        }
        String before = administration.snapshot(user);
        user.setPermissions(permissionService.sanitizePermissionsForRole(user.getRole(), request.getPermissions()));
        administration.record(administration.currentManager(locked), user, "PERMISSIONS_CHANGED", before, administration.snapshot(user));
        return toResponse(userRepository.save(user));
    }

    public UserResponse updateAccount(UUID id, com.factory.productmanager.model.dto.UpdateUserAccountRequest request) {
        var locked = administration.lockAndRequireManager();
        var actor = administration.currentManager(locked);
        User user = administration.target(locked, id);
        administration.preserveLastManager(locked, user, request.role(), user.isDisabled());
        String before = administration.snapshot(user);
        user.setFullName(request.fullName().trim());
        Role previousRole = user.getRole();
        user.setRole(request.role());
        if (previousRole != Role.MANAGER || request.role() != Role.MANAGER) {
            user.setPermissions(permissionService.sanitizePermissionsForRole(request.role(), request.permissions()));
        }
        administration.record(actor, user, "ACCOUNT_UPDATED", before, administration.snapshot(user));
        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public com.factory.productmanager.model.dto.PageResponse<com.factory.productmanager.model.dto.UserAccessEventResponse> accessHistory(UUID id, int page, int size) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !permissionService.isManager(auth.getName()))
            throw new org.springframework.security.access.AccessDeniedException("فقط مدیر");
        return com.factory.productmanager.model.dto.PageResponse.from(accessEvents.findByTargetId(id,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        org.springframework.data.domain.Sort.by("occurredAt").descending().and(org.springframework.data.domain.Sort.by("id").descending()))),
                com.factory.productmanager.model.dto.UserAccessEventResponse::from);
    }

    @Transactional(readOnly = true)

    public List<UserResponse> listUsers() {

        return userRepository.findAll()

                .stream()

                .map(this::toResponse)

                .toList();

    }

    @Transactional(readOnly = true)
    public List<UserDirectoryResponse> directory() {
        return userRepository.findDirectory();
    }

    public void deleteUser(UUID id, String currentUsername) {

        var locked = administration.lockAndRequireManager();
        var actor = administration.currentManager(locked);
        User user = administration.target(locked, id);
        administration.preserveLastManager(locked, user, user.getRole(), true);
        String before = administration.snapshot(user);

        if (user.getUsername().equals(currentUsername)) {

            throw new DuplicateResourceException("نمی‌توانید حساب کاربری خودتان را حذف کنید");

        }

        // کاربری که در وظایف نقش دارد برای حفظ سابقه حذف نمی‌شود؛ باید غیرفعال شود
        if (taskRepository.existsByCreatedByIdOrAssignedToId(user.getId(), user.getId())) {
            throw new DuplicateResourceException(
                    "این کاربر در وظایف نقش دارد و حذف نمی‌شود؛ به‌جای حذف، حساب او را غیرفعال کنید");
        }

        if (performancePeriodRepository.existsByUserId(id)) {
            throw new DuplicateResourceException("این کاربر سابقهٔ ارزیابی دارد؛ به‌جای حذف، حساب او را غیرفعال کنید");
        }
        administration.record(actor, user, "DELETED", before, "{}");
        userRepository.delete(user);

    }

    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.lockForCredentialsByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("رمز عبور فعلی اشتباه است");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /** ادمین رمز عبور کاربر دیگر را بدون دانستن رمز فعلی تنظیم می‌کند (مثلاً در صورت فراموشی) */
    public void resetPasswordByAdmin(UUID id, String newPassword) {
        administration.lockAndRequireManager();
        User user = userRepository.lockForCredentialsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("کاربری با شناسه " + id + " پیدا نشد"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {

        return new UserResponse(

                user.getId(),

                user.getUsername(),

                user.getFullName(),

                user.getRole().name(),

                permissionService.resolvePermissionCodes(user),

                user.getCreatedAt(),
                user.getDepartment(),
                !user.isDisabled()

        );

    }

}

