package com.factory.productmanager.service;

import com.factory.productmanager.model.dto.PermissionInfoResponse;
import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.Role;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    /** Historical defaults retained for compatibility; never reapply to existing users at startup. */
    public static final Set<Permission> DEFAULT_OPERATOR_PERMISSIONS = EnumSet.of(
            Permission.ADVANCE_PIECE_STAGE,
            Permission.MOVE_PIECE_STAGE,
            Permission.UPDATE_RADIOGRAPHY_SUBSTAGE,
            Permission.UPDATE_HEAT_TREATMENT_SUBSTAGE,
            Permission.MANAGE_RADIOGRAPHY_DEFECTS,
            Permission.MANAGE_PIECE_NOTES,
            Permission.MANAGE_WELDING_MAP,
            Permission.MANAGE_PIECE_ASSIGNMENT,
            Permission.RECEIVE_TASKS
    );

    private final UserRepository userRepository;

    public List<PermissionInfoResponse> getAllPermissions() {
        return Arrays.stream(Permission.values())
                .map(p -> new PermissionInfoResponse(p.name(), p.getLabel()))
                .toList();
    }

    public boolean hasPermission(String username, Permission permission) {
        return userRepository.findByUsername(username)
                .map(user -> !user.isDisabled() && (user.getRole() == Role.MANAGER || user.getPermissions().contains(permission)))
                .orElse(false);
    }

    public boolean isManager(String username) {
        return userRepository.findByUsername(username)
                .map(user -> !user.isDisabled() && user.getRole() == Role.MANAGER)
                .orElse(false);
    }

    public boolean canSkipStage(String username) {
        return userRepository.findByUsername(username)
                .map(u -> !u.isDisabled() && (u.getRole() == Role.MANAGER
                    || (u.getRole() == Role.ADMIN && u.getPermissions().contains(Permission.SKIP_PIECE_STAGE))))
                .orElse(false);
    }

    public boolean canCorrectStage(String username) {
        return userRepository.findByUsername(username)
                .filter(user -> !user.isDisabled())
                .map(user -> user.getRole() == Role.MANAGER
                        || (user.getRole() == Role.ADMIN && user.getPermissions().contains(Permission.CORRECT_PIECE_STAGE)))
                .orElse(false);
    }

    public List<String> resolvePermissionCodes(User user) {
        if (user.getRole() == Role.MANAGER) {
            return Arrays.stream(Permission.values()).map(Enum::name).toList();
        }
        return user.getPermissions().stream().map(Enum::name).sorted().toList();
    }

    public Set<Permission> sanitizePermissionsForRole(Role role, Set<Permission> requested) {
        if (role == Role.MANAGER) {
            return EnumSet.noneOf(Permission.class);
        }
        if (requested == null || requested.isEmpty()) {
            return EnumSet.noneOf(Permission.class);
        }
        if (role == Role.OPERATOR && requested.contains(Permission.SKIP_PIECE_STAGE)) {
            throw new IllegalArgumentException("مجوز پرش مرحله فقط برای ادمین قابل واگذاری است");
        }
        if (role == Role.OPERATOR && requested.contains(Permission.CORRECT_PIECE_STAGE)) {
            throw new IllegalArgumentException("مجوز اصلاح وضعیت فقط برای ادمین قابل واگذاری است");
        }
        return requested.stream()
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permission.class)));
    }

}
