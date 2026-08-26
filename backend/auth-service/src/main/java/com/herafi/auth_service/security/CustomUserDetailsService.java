package com.herafi.auth_service.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.herafi.auth_service.repository.UserCredentialRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserCredentialRepository repository;

    public CustomUserDetailsService(UserCredentialRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return repository.findByEmail(normalizedEmail)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }
}
