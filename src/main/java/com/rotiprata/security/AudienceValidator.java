package com.rotiprata.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error("invalid_token", "Invalid audience", null);
    private final List<String> allowedAudiences;

    public AudienceValidator(List<String> allowedAudiences) {
        this.allowedAudiences = allowedAudiences;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() == null || token.getAudience().isEmpty()) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        }
        boolean matches = token.getAudience().stream().anyMatch(allowedAudiences::contains);
        return matches ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
