package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import com.example.auth_service.repository.UserLoginRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security asks this class: "find the user with this username".
 * We answer by reading the user from our own database (auth_db).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserLoginRepository userLoginRepository;
    private final PasswordService passwordService;

    // Spring gives us the two helpers it needs (this is called "constructor injection").
    public AppUserDetailsService(UserLoginRepository userLoginRepository, PasswordService passwordService) {
        this.userLoginRepository = userLoginRepository;
        this.passwordService = passwordService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userLoginRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AppUserPrincipal(user, passwordService.normalizeRole(user.getRole()));
    }
}
