package com.rotiprata.infrastructure.supabase;

import com.rotiprata.config.SupabaseProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SupabaseAuthClient {
    private final RestClient restClient;
    private final String authBaseUrl;

    public SupabaseAuthClient(SupabaseProperties supabaseProperties, RestClient.Builder restClientBuilder) {
        String baseUrl = supabaseProperties.getUrl();
        this.authBaseUrl = baseUrl.endsWith("/") ? baseUrl + "auth/v1" : baseUrl + "/auth/v1";
        this.restClient = restClientBuilder
            .baseUrl(authBaseUrl)
            .defaultHeader("apikey", supabaseProperties.getAnonKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public SupabaseSessionResponse login(String email, String password) {
        return restClient.post()
            .uri("/token?grant_type=password")
            .body(new SupabaseLoginRequest(email, password))
            .retrieve()
            .body(SupabaseSessionResponse.class);
    }

    public SupabaseSignupResponse signup(
        String email,
        String password,
        Map<String, Object> userMetadata,
        String redirectTo
    ) {
        SupabaseSignupRequest request = new SupabaseSignupRequest(email, password, userMetadata);
        return restClient.post()
            .uri(uriBuilder -> {
                var builder = uriBuilder.path("/signup");
                if (redirectTo != null && !redirectTo.isBlank()) {
                    builder.queryParam("redirect_to", redirectTo);
                }
                return builder.build();
            })
            .body(request)
            .retrieve()
            .body(SupabaseSignupResponse.class);
    }

    public void recoverPassword(String email, String redirectTo) {
        SupabaseRecoveryRequest request = new SupabaseRecoveryRequest(email, redirectTo);
        restClient.post()
            .uri("/recover")
            .body(request)
            .retrieve()
            .toBodilessEntity();
    }

    public void updatePassword(String accessToken, String newPassword) {
        restClient.put()
            .uri("/user")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .body(new SupabaseUpdateUserRequest(newPassword))
            .retrieve()
            .toBodilessEntity();
    }

    public void logout(String accessToken) {
        restClient.post()
            .uri("/logout")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .toBodilessEntity();
    }

    public URI buildOAuthUrl(String provider, String redirectTo) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(authBaseUrl + "/authorize")
            .queryParam("provider", provider);
        if (redirectTo != null && !redirectTo.isBlank()) {
            builder.queryParam("redirect_to", redirectTo);
        }
        return builder.build().toUri();
    }
}
