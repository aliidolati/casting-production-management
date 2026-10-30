package com.factory.productmanager.service;



import com.factory.productmanager.exception.DuplicateResourceException;

import com.factory.productmanager.exception.ResourceNotFoundException;

import com.factory.productmanager.model.dto.ChangePasswordRequest;

import com.factory.productmanager.model.dto.CreateUserRequest;

import com.factory.productmanager.model.dto.UpdateUserPermissionsRequest;

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

    private final TaskRepository taskRepository;
    private final com.factory.productmanager.repository.PerformancePeriodRepository performancePeriodRepository;



    public UserResponse createUser(CreateUserRequest request) {

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

        return toResponse(saved);

    }



    public UserResponse updateUserPermissions(UUID id, UpdateUserPermissionsRequest request) {

        User user = userRepository.findById(id)

                .orElseThrow(() -> new ResourceNotFoundException("کاربری با شناسه " + id + " پیدا نشد"));



        if (user.getRole() == Role.ADMIN) {

            throw new DuplicateResourceException("دسترسی‌های کاربر ادمین قابل تغییر نیست؛ ادمین همیشه همه دسترسی‌ها را دارد");

        }



        user.setPermissions(permissionService.sanitizePermissionsForRole(Role.OPERATOR, request.getPermissions()));

        return toResponse(userRepository.save(user));

    }



    @Transactional(readOnly = true)

    public List<UserResponse> listUsers() {

        return userRepository.findAll()

                .stream()

                .map(this::toResponse)

                .toList();

    }



    public void deleteUser(UUID id, String currentUsername) {

        User user = userRepository.lockForPerformance(id)

                .orElseThrow(() -> new ResourceNotFoundException("کاربری با شناسه " + id + " پیدا نشد"));



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


