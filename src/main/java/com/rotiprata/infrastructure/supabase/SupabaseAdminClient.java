package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.config.SupabaseProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SupabaseAdminClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SupabaseAdminClient(SupabaseProperties supabaseProperties, RestClient.Builder restClientBuilder) {
        String baseUrl = supabaseProperties.getUrl();
        String serviceRoleKey = supabaseProperties.getServiceRoleKey();
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException("Supabase service role key is not configured");
        }
        String adminBase = baseUrl.endsWith("/") ? baseUrl + "auth/v1/admin" : baseUrl + "/auth/v1/admin";
        this.restClient = restClientBuilder
            .baseUrl(adminBase)
            .defaultHeader("apikey", serviceRoleKey)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public boolean emailExists(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String target = email.trim().toLowerCase();
        int page = 1;
        int perPage = 1000;
        int maxPages = 100;
        try {
            while (page <= maxPages) {
                String uri = "/users?page=" + page + "&per_page=" + perPage;
                String responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
                if (responseBody == null || responseBody.isBlank()) {
                    return false;
                }
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode users = root.isArray() ? root : root.get("users");
                if (users == null || !users.isArray()) {
                    return false;
                }

                for (JsonNode user : users) {
                    JsonNode emailNode = user.get("email");
                    if (emailNode != null && !emailNode.isNull()) {
                        String candidate = emailNode.asText("").trim().toLowerCase();
                        if (!candidate.isBlank() && candidate.equals(target)) {
                            return true;
                        }
                    }
                }

                if (users.size() < perPage) {
                    return false;
                }
                page += 1;
            }
            return false;
        } catch (RestClientResponseException ex) {
            int status = ex.getRawStatusCode();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Email check unavailable",
                    ex
                );
            }
            throw new ResponseStatusException(ex.getStatusCode(), "Supabase admin request failed", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Supabase admin response parse failed", ex);
        }
    }
}
