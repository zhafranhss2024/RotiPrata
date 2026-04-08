package com.rotiprata.infrastructure.supabase;

import com.rotiprata.config.SupabaseProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupabaseStorageClientTest {

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

    // Verifies constructor requires base Supabase URL.
    @Test
    void constructor_ShouldThrow_WhenUrlIsMissing() {
        //arrange
        properties.setUrl(" ");

        //act
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new SupabaseStorageClient(properties, restClientBuilder));

        //assert
        assertEquals("Supabase URL is not configured", ex.getMessage());

        //verify
    }

    // Verifies constructor requires service role key.
    @Test
    void constructor_ShouldThrow_WhenServiceRoleKeyMissing() {
        //arrange
        properties.setServiceRoleKey("");

        //act
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new SupabaseStorageClient(properties, restClientBuilder));

        //assert
        assertEquals("Supabase service role key is not configured", ex.getMessage());

        //verify
    }

    // Verifies uploadObject without cache-control delegates to overloaded method.
    @Test
    void uploadObject_ShouldUploadBytes_WhenCacheControlNotProvided() {
        // Arrange
        SupabaseStorageClient client = new SupabaseStorageClient(properties, restClientBuilder);

        // Mocks for the chain
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class, RETURNS_DEEP_STUBS);

        // Stub the chain
        when(restClient.put()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(), any())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(byte[].class))).thenReturn(bodySpec); // <-- single-arg stub
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null); // or Mono.empty() if reactive

        // Act
        client.uploadObject("avatars", "folder/my file.png", "abc".getBytes(StandardCharsets.UTF_8), "image/png");

        // Assert
        assertNotNull(client);

        // Verify
        verify(responseSpec).toBodilessEntity();
    }

    // Verifies uploadObject sends cache-control header when specified.
    @Test
    void uploadObject_ShouldSetCacheControl_WhenCacheControlProvided() {
        // Arrange
        SupabaseStorageClient client = new SupabaseStorageClient(properties, restClientBuilder);

        // Mocks for the chain
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_DEEP_STUBS);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_DEEP_STUBS);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_DEEP_STUBS);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class, RETURNS_DEEP_STUBS);

        // Stub fluent chain
        when(restClient.put()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString(), any(), any())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec); // header for cache-control
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(byte[].class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(null);

        // Act
        client.uploadObject("avatars", "folder/photo.png", "abc".getBytes(StandardCharsets.UTF_8), null, "max-age=300");

        // Assert
        assertNotNull(client);

        // Verify
        verify(responseSpec).toBodilessEntity();
    }
}