package com.factory.productmanager.service;

import com.factory.productmanager.exception.DuplicateResourceException;
import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.UpdateUserProfileRequest;
import com.factory.productmanager.model.dto.UserProfileResponse;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * پروفایل کاربر: اطلاعات عمومی کار + آمار وظایف.
 * <p>
 * دسترسی: خودِ کاربر، یا کسی که حق مدیریت وظایف دارد (ادمین/مدیر وظیفه).
 * هر کس دیگر برای هر شناسه‌ای یک پاسخ یکسان «پیدا نشد» می‌گیرد؛ پس نمی‌شود با تست شناسه‌ها
 * فهمید کدام کاربر وجود دارد. هیچ داده‌ی حساسی (رمز، دسترسی‌ها) در پروفایل نیست.
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserAdministrationPolicy administration;
    private final TaskLookup lookup;
    private final TaskQueryService queryService;
    private final TaskStatsService statsService;
    private final TaskAccessPolicy policy;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId, String viewerUsername) {
        User viewer = lookup.actor(viewerUsername);
        queryService.requireCanViewProfile(viewer, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
        return toProfile(user);
    }

    /** ویرایش بخش و فعال/غیرفعال‌بودن حساب (فقط مدیر؛ در کنترلر هم محدود شده است) */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request, String adminUsername) {
        var locked = administration.lockAndRequireManager();
        User user = administration.target(locked, userId);
        var actor = administration.currentManager(locked);
        administration.preserveLastManager(locked, user, user.getRole(), request.active() == null ? user.isDisabled() : !request.active());
        String before = administration.snapshot(user);

        if (request.department() != null) {
            String department = request.department().trim();
            user.setDepartment(department.isEmpty() ? null : department);
        }
        if (request.active() != null) {
            boolean disable = !request.active();
            if (disable && user.getUsername().equals(adminUsername)) {
                throw new DuplicateResourceException("نمی‌توانید حساب کاربری خودتان را غیرفعال کنید");
            }
            user.setDisabled(disable);
        }
        administration.record(actor, user, "PROFILE_UPDATED", before, administration.snapshot(user));
        return toProfile(userRepository.save(user));
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                user.getDepartment(),
                !user.isDisabled(),
                user.getLastActivityAt(),
                user.getCreatedAt(),
                statsService.statsFor(user.getId()),
                policy.canReceiveTasks(user)
        );
    }
}
