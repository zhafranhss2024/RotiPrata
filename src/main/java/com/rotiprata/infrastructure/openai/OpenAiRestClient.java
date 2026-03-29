package com.rotiprata.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OpenAiRestClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiRestClient(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            RestClient.Builder builder
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public <T> T post(String path, Object body, TypeReference<T> typeRef) {
        try {
            String responseBody = restClient.post()
                    .uri(path)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            return objectMapper.readValue(responseBody, typeRef);

        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex
            );
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "OpenAI request failed", ex);
        }
    }
}