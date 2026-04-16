package com.rotiprata.api.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers login streak service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class LoginStreakServiceImplTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;

    private LoginStreakServiceImpl service;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        service = new LoginStreakServiceImpl(supabaseRestClient);
    }

    /**
     * Verifies that touch login streak should throw unauthorized when access token is blank.
     */
    // Ensures missing tokens are rejected before any profile lookup is attempted.
    @Test
    void touchLoginStreak_ShouldThrowUnauthorized_WhenAccessTokenIsBlank() {
        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> service.touchLoginStreak(UUID.randomUUID(), " ", "UTC")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(supabaseRestClient, never()).getList(anyString(), anyString(), anyString(), any(TypeReference.class));
    }

    /**
     * Verifies that touch login streak should update consecutive streak values when the user returns the next day.
     */
    // Ensures streak continuation increments current and longest streak values and persists the resolved timezone.
    @Test
    void touchLoginStreak_ShouldIncrementStreak_WhenLastActivityWasYesterday() {
        UUID userId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "current_streak", 2,
                "longest_streak", 3,
                "last_activity_date", yesterday.toString(),
                "timezone", "UTC"
            ))));

        var result = service.touchLoginStreak(userId, "token", "UTC");

        assertEquals(3, result.currentStreak());
        assertEquals(3, result.longestStreak());
        assertFalse(result.touchedToday());
        verify(supabaseRestClient).patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any(TypeReference.class));
    }

    /**
     * Verifies that touch login streak should retry without timezone when the database schema lacks that column.
     */
    // Ensures timezone fallback preserves the streak update even when the timezone column is unavailable.
    @Test
    void touchLoginStreak_ShouldRetryWithoutTimezone_WhenTimezoneColumnIsMissing() {
        UUID userId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(supabaseRestClient.getList(eq("profiles"), anyString(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(new LinkedHashMap<>(Map.of(
                "id", UUID.randomUUID().toString(),
                "current_streak", 1,
                "longest_streak", 1,
                "last_activity_date", yesterday.toString(),
                "timezone", "UTC"
            ))));

        HttpClientErrorException missingTimezoneColumn = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "bad request",
            org.springframework.http.HttpHeaders.EMPTY,
            "{\"message\":\"column timezone does not exist\"}".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        when(supabaseRestClient.patchList(eq("profiles"), anyString(), anyMap(), eq("token"), any(TypeReference.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request", missingTimezoneColumn))
            .thenReturn(List.of());

        ArgumentCaptor<Map<String, Object>> patchCaptor = ArgumentCaptor.forClass(Map.class);

        var result = service.touchLoginStreak(userId, "token", "Asia/Singapore");

        assertEquals(2, result.currentStreak());
        assertEquals(2, result.longestStreak());
        assertTrue(result.lastActivityDate().equals(LocalDate.now()));
        verify(supabaseRestClient, times(2))
            .patchList(eq("profiles"), anyString(), patchCaptor.capture(), eq("token"), any(TypeReference.class));
        assertTrue(patchCaptor.getAllValues().get(0).containsKey("timezone"));
        assertFalse(patchCaptor.getAllValues().get(1).containsKey("timezone"));
    }
}
