package com.rotiprata.config;

import com.rotiprata.security.AuthRateLimitFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final AuthRateLimitFilter authRateLimitFilter;
    private final String allowedOrigins;

    public SecurityConfig(
        AuthRateLimitFilter authRateLimitFilter,
        @Value("${ALLOWED_ORIGINS:}") String allowedOrigins
    ) {
        this.authRateLimitFilter = authRateLimitFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/auth/username-available",
                    "/api/categories"
                ).permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password"
                ).permitAll()
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(resolveAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> resolveAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"
            );
        }
        return Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toList());
    }
}
