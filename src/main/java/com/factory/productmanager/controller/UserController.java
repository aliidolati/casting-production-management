package com.factory.productmanager.controller;

import com.factory.productmanager.model.dto.AdminResetPasswordRequest;
import com.factory.productmanager.model.dto.ChangePasswordRequest;
import com.factory.productmanager.model.dto.CreateUserRequest;
import com.factory.productmanager.model.dto.UpdateUserPermissionsRequest;
import com.factory.productmanager.model.dto.UserResponse;
import com.factory.productmanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id, Authentication authentication) {
        userService.deleteUser(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/permissions")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserResponse> updateUserPermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserPermissionsRequest request
    ) {
        return ResponseEntity.ok(userService.updateUserPermissions(id, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public UserResponse updateAccount(@PathVariable UUID id,
            @Valid @RequestBody com.factory.productmanager.model.dto.UpdateUserAccountRequest request) {
        return userService.updateAccount(id, request);
    }

    @GetMapping("/{id}/access-history")
    @PreAuthorize("hasRole('MANAGER')")
    public com.factory.productmanager.model.dto.PageResponse<com.factory.productmanager.model.dto.UserAccessEventResponse> accessHistory(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return userService.accessHistory(id, page, size);
    }

    /** ادمین می‌تواند رمز عبور هر کاربر را بدون رمز فعلی تنظیم کند */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> resetUserPassword(
            @PathVariable UUID id,
            @Valid @RequestBody AdminResetPasswordRequest request
    ) {
        userService.resetPasswordByAdmin(id, request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    // هر کاربر (ادمین یا اپراتور) می‌تواند رمز عبور خودش را تغییر بدهد
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
