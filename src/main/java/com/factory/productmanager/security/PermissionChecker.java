package com.factory.productmanager.security;

import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * برای استفاده در @PreAuthorize: @permissionChecker.has(authentication, 'MOVE_PIECE_STAGE')
 */
@Component("permissionChecker")
@RequiredArgsConstructor
public class PermissionChecker {

    private final PermissionService permissionService;

    public boolean has(Authentication authentication, String permissionName) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return permissionService.hasPermission(authentication.getName(), Permission.valueOf(permissionName));
    }

    public boolean hasAny(Authentication authentication, String... permissionNames) {
        if (permissionNames == null || permissionNames.length == 0) {
            return false;
        }
        for (String permissionName : permissionNames) {
            if (has(authentication, permissionName)) {
                return true;
            }
        }
        return false;
    }
}
