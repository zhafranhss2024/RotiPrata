package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.rotiprata.config.SupabaseProperties;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import com.rotiprata.infrastructure.supabase.SupabaseAdminClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupabaseAdminClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    private SupabaseProperties properties;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        properties = new SupabaseProperties();
        properties.setUrl("https://example.supabase.co");
        properties.setServiceRoleKey("service-role");
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
    }

    // Verifies constructor rejects missing service role key.
    @Test
    void constructor_ShouldThrow_WhenServiceRoleKeyIsMissing() {
        //arrange
        properties.setServiceRoleKey(" ");

        //act
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new SupabaseAdminClient(properties, restClientBuilder));

        //assert
        assertEquals("Supabase service role key is not configured", ex.getMessage());

        //verify
    }

    // Verifies blank emails short-circuit to false.
    @Test
    void emailExists_ShouldReturnFalse_WhenEmailIsBlank() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);

        //act
        boolean result = client.emailExists("   ");

        //assert
        assertFalse(result);

        //verify
    }

    // Verifies email matching is case-insensitive and trimmed.
    @Test
    void emailExists_ShouldReturnTrue_WhenMatchingEmailExists() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        when(restClient.get().uri("/users?page=1&per_page=1000").retrieve().body(String.class))
            .thenReturn("[{\"email\":\" User@Example.com \"}]");

        //act
        boolean result = client.emailExists(" user@example.com ");

        //assert
        assertTrue(result);

        //verify
        verify(restClient.get().uri("/users?page=1&per_page=1000").retrieve()).body(String.class);
    }

    // Verifies 401 from Supabase is mapped to service unavailable.
    @Test
    void emailExists_ShouldThrowServiceUnavailable_WhenUnauthorizedFromSupabase() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        RestClientResponseException ex = new RestClientResponseException(
            "unauthorized", 401, "Unauthorized", null, "{}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restClient.get().uri("/users?page=1&per_page=1000").retrieve().body(String.class))
            .thenThrow(ex);

        //act
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
            () -> client.emailExists("user@example.com"));

        //assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, thrown.getStatusCode());
        assertEquals("Email check unavailable", thrown.getReason());

        //verify
    }

    // Verifies listUsers handles paginated array responses.
    @Test
    void listUsers_ShouldAggregatePages_WhenBatchSizeHitsPerPage() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        String pageOne = "[" + "{\"id\":1},".repeat(999) + "{\"id\":1000}]";
        when(restClient.get().uri("/users?page=1&per_page=1000").retrieve().body(String.class)).thenReturn(pageOne);
        when(restClient.get().uri("/users?page=2&per_page=1000").retrieve().body(String.class)).thenReturn("[{\"id\":1001}]");

        //act
        var users = client.listUsers();

        //assert
        assertEquals(1001, users.size());

        //verify
        verify(restClient.get().uri("/users?page=2&per_page=1000").retrieve()).body(String.class);
    }

    // Verifies listUsers supports object wrapper with users field.
    @Test
    void listUsers_ShouldReadUsersField_WhenResponseIsObject() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        when(restClient.get().uri("/users?page=1&per_page=1000").retrieve().body(String.class))
            .thenReturn("{\"users\":[{\"id\":\"u1\"}]}");

        //act
        var users = client.listUsers();

        //assert
        assertEquals(1, users.size());

        //verify
    }

    // Verifies getUser returns parsed json payload.
    @Test
    void getUser_ShouldReturnJsonNode_WhenSupabaseReturnsBody() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        UUID userId = UUID.randomUUID();
        when(restClient.get().uri("/users/" + userId).retrieve().body(String.class))
            .thenReturn("{\"id\":\"abc\"}");

        //act
        JsonNode result = client.getUser(userId);

        //assert
        assertEquals("abc", result.get("id").asText());

        //verify
    }

    // Verifies updateUser sends put and parses empty body as object node.
    @Test
    void updateUser_ShouldReturnEmptyObject_WhenResponseBodyIsBlank() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        UUID userId = UUID.randomUUID();
        lenient().when(restClient.put().uri("/users/" + userId).body(any()).retrieve().body(String.class))
            .thenReturn(" ");

        //act
        JsonNode result = client.updateUser(userId, Map.of("role", "admin"));

        //assert
        assertNotNull(result);
        assertTrue(result.isObject());

        //verify
    }

    // Verifies parse failures are wrapped as bad gateway.
    @Test
    void listUsers_ShouldThrowBadGateway_WhenResponseCannotBeParsed() {
        //arrange
        SupabaseAdminClient client = new SupabaseAdminClient(properties, restClientBuilder);
        when(restClient.get().uri("/users?page=1&per_page=1000").retrieve().body(String.class))
            .thenReturn("not-json");

        //act
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, client::listUsers);

        //assert
        assertEquals(HttpStatus.BAD_GATEWAY, thrown.getStatusCode());
        assertEquals("Supabase admin response parse failed", thrown.getReason());

        //verify
    }
}
