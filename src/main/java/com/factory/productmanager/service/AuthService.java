package com.factory.productmanager.service;

import com.factory.productmanager.exception.ResourceNotFoundException;
import com.factory.productmanager.model.dto.LoginRequest;
import com.factory.productmanager.model.dto.LoginResponse;
import com.factory.productmanager.model.dto.UserResponse;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import com.factory.productmanager.security.JwtService;
import com.factory.productmanager.security.AuthenticatedUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PermissionService permissionService;
    private final UserActivityService userActivityService;

    public LoginResponse login(LoginRequest request) {
        org.springframework.security.core.Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("نام کاربری یا رمز عبور اشتباه است");
        } catch (DisabledException e) {
            throw new BadCredentialsException("حساب کاربری شما غیرفعال شده است");
        }

        if (!(authenticated.getPrincipal() instanceof AuthenticatedUserDetails principal)) {
            throw new BadCredentialsException("اطلاعات ورود معتبر نیست");
        }
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));

        // Use the credentials actually checked, even if a reset races with this login.
        String token = jwtService.generateToken(principal, user.getRole().name());
        userActivityService.touch(user.getUsername());

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name(),
                permissionService.resolvePermissionCodes(user)
        );
    }

    public UserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر پیدا نشد"));

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
