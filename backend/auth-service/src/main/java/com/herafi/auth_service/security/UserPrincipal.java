package com.herafi.auth_service.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.herafi.auth_service.entity.Role;
import com.herafi.auth_service.entity.UserCredential;

public record UserPrincipal(
        UUID id,
        String email,
        String passwordHash,
        Role role
) implements UserDetails {

    public static UserPrincipal from(UserCredential credential) {
        return new UserPrincipal(
                credential.getId(),
                credential.getEmail(),
                credential.getPasswordHash(),
                credential.getRole()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
