package com.factory.productmanager.service;

import com.factory.productmanager.model.entity.*;
import com.factory.productmanager.repository.*;
import com.factory.productmanager.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional
public class UserAdministrationPolicy {
    private final UserRepository users;
    private final UserAccessEventRepository events;
    private final JsonMapper json;
    private final Clock clock;

    /** Every role, permission, create, delete and disable write takes the same ordered row locks. */
    public List<User> lockAndRequireManager() {
        List<User> locked = users.lockForAdministration();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null || !auth.isAuthenticated() ? null : auth.getName();
        if (locked.stream().noneMatch(u -> u.getUsername().equals(username)
                && !u.isDisabled() && u.getRole() == Role.MANAGER)) {
            throw new AccessDeniedException("مدیریت کاربران فقط در اختیار مدیر است");
        }
        return locked;
    }

    public User target(List<User> locked, UUID id) {
        return locked.stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));
    }

    public void preserveLastManager(List<User> locked, User target, Role nextRole, boolean nextDisabled) {
        if (target.getRole() == Role.MANAGER && !target.isDisabled()
                && (nextRole != Role.MANAGER || nextDisabled)
                && locked.stream().noneMatch(u -> !u.getId().equals(target.getId())
                    && u.getRole() == Role.MANAGER && !u.isDisabled())) {
            throw new DuplicateResourceException("آخرین مدیر فعال را نمی‌توان حذف، غیرفعال یا تنزل نقش داد");
        }
    }

    public String snapshot(User user) {
        if (user == null) return "{}";
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("username", user.getUsername()); value.put("fullName", user.getFullName());
        value.put("role", user.getRole().name()); value.put("active", !user.isDisabled());
        value.put("department", user.getDepartment());
        value.put("permissions", user.getRole() == Role.MANAGER ? List.of("ALL")
                : user.getPermissions().stream().map(Enum::name).sorted().toList());
        return json.writeValueAsString(value);
    }

    public void record(User actor, User target, String action, String before, String after) {
        if (before.equals(after)) return;
        events.save(new UserAccessEvent(actor, target, action, before, after, LocalDateTime.now(clock)));
    }

    public User currentManager(List<User> locked) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return locked.stream().filter(u -> u.getUsername().equals(username)).findFirst().orElseThrow();
    }
}
