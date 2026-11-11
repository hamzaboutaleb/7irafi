package com.herafi.api_gateway.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

        // HTTP basic is limited to the scrape endpoint, so every other route
        // keeps answering 401 with a Bearer challenge.
        @Bean
        @Order(1)
        SecurityFilterChain metricsFilterChain(HttpSecurity http) throws Exception {
                return http
                                .securityMatcher("/actuator/prometheus")
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("METRICS"))
                                .httpBasic(Customizer.withDefaults())
                                .build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(csrf -> csrf.disable())
                                .cors(Customizer.withDefaults())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                // The error page keeps its real status (e.g. 500 when a
                                                // service is down) instead of becoming a 401.
                                                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers("/auth/**",
                                                                "/actuator/health",
                                                                "/status",
                                                                "/swagger-ui/**",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                .requestMatchers("/users/**", "/jobs/**", "/applications/**",
                                                                "/conversations/**", "/notifications/**", "/analytics/**")
                                                .authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                                .build();
        }

        @Bean
        UserDetailsService metricsUserDetailsService(
                        @Value("${metrics.username:prometheus}") String username,
                        @Value("${metrics.password:7irafi-development-metrics}") String password) {
                return new InMemoryUserDetailsManager(
                                User.withUsername(username)
                                                .password(passwordEncoder().encode(password))
                                                .roles("METRICS")
                                                .build());
        }

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource(
                        @Value("${gateway.cors.allowed-origin}") String allowedOrigin) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(List.of(allowedOrigin));
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
                configuration.setExposedHeaders(List.of("X-Request-Id"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
