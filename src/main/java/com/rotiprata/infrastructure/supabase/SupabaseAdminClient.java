package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.config.SupabaseProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        try {
            for (JsonNode user : listUsers()) {
                JsonNode emailNode = user.get("email");
                if (emailNode != null && !emailNode.isNull()) {
                    String candidate = emailNode.asText("").trim().toLowerCase();
                    if (!candidate.isBlank() && candidate.equals(target)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Email check unavailable",
                    ex
                );
            }
            throw ex;
        }
    }

    public List<JsonNode> listUsers() {
        int page = 1;
        int perPage = 1000;
        int maxPages = 100;
        List<JsonNode> users = new ArrayList<>();
        while (page <= maxPages) {
            JsonNode pageNode = requestNode("GET", "/users?page=" + page + "&per_page=" + perPage, null);
            JsonNode batch = pageNode.isArray() ? pageNode : pageNode.get("users");
            if (batch == null || !batch.isArray() || batch.isEmpty()) {
                break;
            }
            batch.forEach(users::add);
            if (batch.size() < perPage) {
                break;
            }
            page += 1;
        }
        return users;
    }

    public JsonNode getUser(UUID userId) {
        return requestNode("GET", "/users/" + userId, null);
    }

    public JsonNode updateUser(UUID userId, Map<String, Object> attributes) {
        return requestNode("PUT", "/users/" + userId, attributes);
    }

    private JsonNode requestNode(String method, String uri, Object body) {
        try {
            String responseBody;
            if ("GET".equals(method)) {
                responseBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            } else if ("PUT".equals(method)) {
                responseBody = restClient.put()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            } else {
                throw new IllegalArgumentException("Unsupported method " + method);
            }
            if (responseBody == null || responseBody.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(responseBody);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), "Supabase admin request failed", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Supabase admin response parse failed", ex);
        }
    }
}
