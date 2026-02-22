package com.rotiprata.infrastructure.supabase;

import com.rotiprata.config.SupabaseProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SupabaseStorageClient {
    private final RestClient restClient;

    public SupabaseStorageClient(SupabaseProperties supabaseProperties, RestClient.Builder restClientBuilder) {
        String baseUrl = supabaseProperties.getUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Supabase URL is not configured");
        }
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        String serviceRoleKey = supabaseProperties.getServiceRoleKey();
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new IllegalStateException("Supabase service role key is not configured");
        }
        this.restClient = restClientBuilder
            .baseUrl(baseUrl + "storage/v1")
            .defaultHeader("apikey", serviceRoleKey)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
            .build();
    }

    public void uploadObject(String bucket, String path, byte[] data, String contentType) {
        String encodedPath = encodePath(path);
        restClient.put()
            .uri("/object/{bucket}/{path}", bucket, encodedPath)
            .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
            .body(data)
            .retrieve()
            .toBodilessEntity();
    }

    private String encodePath(String path) {
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return encoded.toString();
    }
}
