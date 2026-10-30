package com.factory.productmanager.security;

import com.factory.productmanager.model.entity.Permission;
import com.factory.productmanager.model.entity.User;
import com.factory.productmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("کاربری با نام کاربری '" + username + "' پیدا نشد"));

        return details(user);
    }

    public AuthenticatedUserDetails loadUserById(UUID id) {
        return details(userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("کاربر پیدا نشد")));
    }

    private AuthenticatedUserDetails details(User user) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        if (user.getPermissions() != null) {
            for (Permission permission : user.getPermissions()) {
                authorities.add(new SimpleGrantedAuthority("PERMISSION_" + permission.name()));
            }
        }

        return new AuthenticatedUserDetails(user.getId(), user.getUsername(), user.getPassword(),
                !user.isDisabled(), authorities, jwtService.credentialStamp(user));
    }
}
