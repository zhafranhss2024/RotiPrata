package com.rotiprata.infrastructure.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.config.SupabaseProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupabaseAdminRestClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    private SupabaseProperties properties;
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        properties = new SupabaseProperties();
        properties.setRestUrl("https://example.supabase.co/rest/v1");
        properties.setServiceRoleKey("service-role");
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);

        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.build()).thenReturn(restClient);
    }

    // Verifies constructor fails when rest url is blank.
    @Test
    void constructor_ShouldThrow_WhenRestUrlIsMissing() {
        //arrange
        properties.setRestUrl(" ");

        //act
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new SupabaseAdminRestClient(properties, restClientBuilder));

        //assert
        assertEquals("Supabase REST URL is not configured", ex.getMessage());

        //verify
    }

    // Verifies GET responses are parsed into list values.
    @Test
    void getList_ShouldReturnRows_WhenResponseHasJson() {
        //arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);
        when(restClient.get().uri("table?select=*\u0026limit=1").retrieve().body(String.class))
            .thenReturn("[{\"id\":1}]");

        //act
        List<Map<String, Object>> rows = client.getList("table", "select=*&limit=1", new TypeReference<>() {});

        //assert
        assertEquals(1, rows.size());
        assertEquals(1, ((Number) rows.get(0).get("id")).intValue());

        //verify
    }

    // Verifies postList handles blank responses as empty lists.
    @Test
    void postList_ShouldReturnEmpty_WhenResponseBodyIsBlank() {
        // Arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        // Mock each step in the chain
        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri("table")).thenReturn(bodySpec);
        when(bodySpec.header("Prefer", "return=representation")).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve().body(String.class)).thenReturn(" "); // blank response

        // Act
        List<Map<String, Object>> rows = client.postList("table", Map.of("a", 1), new TypeReference<>() {});

        // Assert
        assertEquals(0, rows.size());
    }

    // Verifies patchList supports query starting with '?'.
    @Test
    void patchList_ShouldNormalizeQuery_WhenQueryStartsWithQuestionMark() {
        // arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        RestClient.RequestBodyUriSpec patchSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);

        when(restClient.patch()).thenReturn(patchSpec);

        when(patchSpec.uri("table?id=eq.1")).thenReturn(bodySpec);

        when(bodySpec.header("Prefer", "return=representation")).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve().body(String.class)).thenReturn("[{\"id\":1}]");

        // act
        List<Map<String, Object>> rows =
                client.patchList("table", "?id=eq.1", Map.of("name", "x"), new TypeReference<>() {});

        // assert
        assertEquals(1, rows.size());
    }

    // Verifies deleteList delegates to DELETE pipeline.
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deleteList_ShouldReturnRows_WhenDeleteSucceeds() {
        // arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        RestClient.RequestHeadersUriSpec deleteSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_DEEP_STUBS);

        when(restClient.delete()).thenReturn(deleteSpec);
        when(deleteSpec.uri("table?id=eq.1")).thenReturn(headersSpec);
        when(headersSpec.header("Prefer", "return=representation")).thenReturn(headersSpec);
        when(headersSpec.retrieve().body(String.class)).thenReturn("[{\"id\":1}]");

        // act
        List<Map<String, Object>> rows =
                client.deleteList("table", "id=eq.1", new TypeReference<>() {});

        // assert
        assertEquals(1, rows.size());
    }

    // Verifies rpcList posts to rpc endpoint.
    @Test
    void rpcList_ShouldReturnRows_WhenRpcSucceeds() {
        // arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        RestClient.RequestBodyUriSpec postSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);

        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri("rpc/run_me")).thenReturn(bodySpec);
        when(bodySpec.header("Prefer", "return=representation")).thenReturn(bodySpec);
        when(bodySpec.body(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve().body(String.class)).thenReturn("[{\"ok\":true}]");

        // act
        List<Map<String, Object>> rows =
                client.rpcList("run_me", Map.of("x", 1), new TypeReference<>() {});

        // assert
        assertEquals(true, rows.get(0).get("ok"));

        // verify
        verify(restClient).post();
        verify(postSpec).uri("rpc/run_me");
    }

    // Verifies RestClient errors propagate response body message.
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void getList_ShouldThrowResponseStatusException_WhenRestClientThrows() {
        // arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_DEEP_STUBS);

        RestClientResponseException ex = new RestClientResponseException(
                "bad",
                400,
                "Bad Request",
                null,
                "from-supabase".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri("table")).thenReturn(headersSpec);
        when(headersSpec.retrieve().body(String.class)).thenThrow(ex);

        // act
        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> client.getList("table", null, new TypeReference<List<Map<String, Object>>>() {})
        );

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        assertEquals("from-supabase", thrown.getReason());

        // verify
        verify(restClient).get();
    }


    // Verifies malformed json triggers parse failure mapping.
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void getList_ShouldThrowInternalServerError_WhenResponseCannotBeParsed() {
        // arrange
        SupabaseAdminRestClient client = new SupabaseAdminRestClient(properties, restClientBuilder);

        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_DEEP_STUBS);

        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri("table")).thenReturn(headersSpec);
        when(headersSpec.retrieve().body(String.class)).thenReturn("not-json");

        // act
        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> client.getList("table", null, new TypeReference<List<Map<String, Object>>>() {})
        );

        // assert
        assertEquals(500, thrown.getStatusCode().value());
        assertEquals("Failed to parse Supabase response", thrown.getReason());

        // verify
        verify(restClient).get();
    }
}
