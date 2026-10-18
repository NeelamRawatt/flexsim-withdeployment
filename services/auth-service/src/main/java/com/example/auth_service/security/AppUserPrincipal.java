package com.example.auth_service.security;

import com.example.auth_service.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user, in the shape Spring Security understands.
 * (Spring Security knows the interface "UserDetails"; we fill it with our data.)
 *
 * The role gets the prefix ROLE_ : ADMIN becomes ROLE_ADMIN. Spring Security
 * expects this prefix when it checks hasRole("ADMIN").
 */
public class AppUserPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final String role;
    private final List<GrantedAuthority> authorities;

    public AppUserPrincipal(User user, String normalizedRole) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.role = normalizedRole;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
