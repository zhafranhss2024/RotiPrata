package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers content engagement service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class ContentEngagementServiceImplTest {

    @Mock
    private SupabaseRestClient supabaseRestClient;

    // Ensures unauthenticated callers receive default false engagement flags.
    @Test
    @SuppressWarnings("unchecked")
    void decorateItemsWithUserEngagement_ShouldSetDefaultFlags_WhenMissingAuthContext() {
        //arrange
        ContentEngagementService service = new ContentEngagementServiceImpl(supabaseRestClient);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        List<Map<String, Object>> items = new ArrayList<>(List.of(item));

        //act
        List<Map<String, Object>> result = service.decorateItemsWithUserEngagement(items, null, "");

        //assert
        assertSame(items, result);
        assertEquals(false, result.get(0).get("is_liked"));
        assertEquals(false, result.get(0).get("is_saved"));

        //verify
        verify(supabaseRestClient, never()).getList(any(), any(), any(), any(TypeReference.class));
    }

    // Ensures no remote lookup happens when input has no usable content ids.
    @Test
    @SuppressWarnings("unchecked")
    void decorateItemsWithUserEngagement_ShouldReturnSameItems_WhenNoContentIdsExist() {
        //arrange
        ContentEngagementService service = new ContentEngagementServiceImpl(supabaseRestClient);
        List<Map<String, Object>> items = new ArrayList<>(List.of(new LinkedHashMap<>()));

        //act
        List<Map<String, Object>> result = service.decorateItemsWithUserEngagement(items, UUID.randomUUID(), "token");

        //assert
        assertSame(items, result);

        //verify
        verify(supabaseRestClient, never()).getList(any(), any(), any(), any(TypeReference.class));
    }

    // Ensures liked and saved flags are mapped from both engagement tables.
    @Test
    @SuppressWarnings("unchecked")
    void decorateItemsWithUserEngagement_ShouldDecorateFlags_WhenLikesAndSavesExist() {
        //arrange
        ContentEngagementService service = new ContentEngagementServiceImpl(supabaseRestClient);
        UUID userId = UUID.randomUUID();
        String contentA = UUID.randomUUID().toString();
        String contentB = UUID.randomUUID().toString();

        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", contentA);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", contentB);
        List<Map<String, Object>> items = new ArrayList<>(List.of(a, b));

        when(supabaseRestClient.getList(eq("content_likes"), any(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", contentA)));
        when(supabaseRestClient.getList(eq("content_saves"), any(), eq("token"), any(TypeReference.class)))
            .thenReturn(List.of(Map.of("content_id", contentB)));

        //act
        List<Map<String, Object>> result = service.decorateItemsWithUserEngagement(items, userId, "token");

        //assert
        assertTrue((Boolean) result.get(0).get("is_liked"));
        assertFalse((Boolean) result.get(0).get("is_saved"));
        assertFalse((Boolean) result.get(1).get("is_liked"));
        assertTrue((Boolean) result.get(1).get("is_saved"));

        //verify
        verify(supabaseRestClient).getList(eq("content_likes"), any(), eq("token"), any(TypeReference.class));
        verify(supabaseRestClient).getList(eq("content_saves"), any(), eq("token"), any(TypeReference.class));
    }
}


