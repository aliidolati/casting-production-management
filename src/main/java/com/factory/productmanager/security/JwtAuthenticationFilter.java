package com.factory.productmanager.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.factory.productmanager.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * روی هر درخواست، هدر Authorization را می‌خواند و در صورت معتبر بودن JWT،
 * کاربر را در SecurityContext قرار می‌دهد تا Controller/Service ها بتوانند
 * دسترسی را بر اساس نقش او (ADMIN/OPERATOR) کنترل کنند.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserActivityService userActivityService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER_NAME);

        if (authHeader == null || !authHeader.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(PREFIX.length());

        try {
            JwtService.TokenIdentity identity = jwtService.parseToken(token);
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                AuthenticatedUserDetails userDetails = userDetailsService.loadUserById(identity.userId());

                // حساب غیرفعال‌شده حتی با توکن هنوز معتبر هم نباید وارد شود
                if (jwtService.isTokenValid(identity, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // توکن نامعتبر/منقضی -> کاربر احراز هویت نمی‌شود و درخواست به‌عنوان ناشناس ادامه می‌یابد
            SecurityContextHolder.clearContext();
        }

        recordActivity();

        filterChain.doFilter(request, response);
    }

    /** ثبت «آخرین فعالیت»؛ جدا از احراز هویت تا خطای آن هرگز ورود کاربر را خراب نکند */
    private void recordActivity() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                userActivityService.touch(authentication.getName());
            }
        } catch (Exception ignored) {
            // عمداً نادیده گرفته می‌شود
        }
    }
}
