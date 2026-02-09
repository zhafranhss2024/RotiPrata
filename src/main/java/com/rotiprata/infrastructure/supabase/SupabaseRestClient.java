package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.rotiprata.config.SupabaseProperties;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SupabaseRestClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SupabaseRestClient(SupabaseProperties supabaseProperties, RestClient.Builder restClientBuilder) {
        String baseUrl = supabaseProperties.getRestUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Supabase REST URL is not configured");
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        this.restClient = restClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("apikey", supabaseProperties.getAnonKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
        this.objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public <T> List<T> getList(String path, String query, String accessToken, TypeReference<List<T>> typeRef) {
        return exchangeList("GET", path, query, null, accessToken, typeRef);
    }

    public <T> List<T> postList(String path, Object body, String accessToken, TypeReference<List<T>> typeRef) {
        return exchangeList("POST", path, null, body, accessToken, typeRef);
    }

    public <T> List<T> patchList(String path, String query, Object body, String accessToken, TypeReference<List<T>> typeRef) {
        return exchangeList("PATCH", path, query, body, accessToken, typeRef);
    }

    private <T> List<T> exchangeList(
        String method,
        String path,
        String query,
        Object body,
        String accessToken,
        TypeReference<List<T>> typeRef
    ) {
        String uri = buildUri(path, query);
        try {
            String responseBody;
            if ("GET".equals(method)) {
                responseBody = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            } else if ("POST".equals(method)) {
                responseBody = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("Prefer", "return=representation")
                    .body(serialize(body))
                    .retrieve()
                    .body(String.class);
            } else if ("PATCH".equals(method)) {
                responseBody = restClient.patch()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("Prefer", "return=representation")
                    .body(serialize(body))
                    .retrieve()
                    .body(String.class);
            } else {
                throw new IllegalArgumentException("Unsupported method " + method);
            }

            if (responseBody == null || responseBody.isBlank()) {
                return Collections.emptyList();
            }
            return objectMapper.readValue(responseBody, typeRef);
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            String message = ex.getResponseBodyAsString();
            throw new ResponseStatusException(status, message == null || message.isBlank() ? "Supabase request failed" : message, ex);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(500), "Failed to parse Supabase response", ex);
        }
    }

    private String buildUri(String path, String query) {
        if (query == null || query.isBlank()) {
            return path;
        }
        String normalized = query.startsWith("?") ? query.substring(1) : query;
        return path + "?" + normalized;
    }

    private String serialize(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(500), "Failed to serialize Supabase request body", ex);
        }
    }
}
