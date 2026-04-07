package com.rotiprata.api.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentCreatorEnrichmentServiceImpl tests")
class ContentCreatorEnrichmentServiceImplTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    // Service enriches list items with matched creator profile records.
    @Test
    @SuppressWarnings("unchecked")
    void enrichWithCreatorProfiles_ShouldAttachNormalizedProfiles_WhenValidCreatorIdsAndProfilesExist() {
        //arrange
        ContentCreatorEnrichmentServiceImpl service = new ContentCreatorEnrichmentServiceImpl(supabaseAdminRestClient);
        UUID creatorOne = UUID.randomUUID();
        UUID creatorTwo = UUID.randomUUID();

        Map<String, Object> firstItem = new LinkedHashMap<>();
        firstItem.put("creator_id", creatorOne.toString());
        Map<String, Object> secondItem = new LinkedHashMap<>();
        secondItem.put("creator_id", creatorTwo.toString());
        List<Map<String, Object>> items = new ArrayList<>(List.of(firstItem, secondItem));

        List<Map<String, Object>> rows = List.of(
            Map.of("user_id", creatorOne.toString(), "display_name", "  Ada  ", "avatar_url", "   "),
            Map.of("user_id", creatorTwo.toString(), "display_name", "", "avatar_url", " https://img.example/u2 "),
            Map.of("user_id", "not-a-uuid", "display_name", "ignored", "avatar_url", "ignored")
        );
        when(supabaseAdminRestClient.getList(eq("profiles"), any(), any(TypeReference.class))).thenReturn(rows);

        //act
        List<Map<String, Object>> result = service.enrichWithCreatorProfiles(items);

        //assert
        assertSame(items, result);
        Map<String, Object> creatorOneProfile = (Map<String, Object>) result.get(0).get("creator");
        Map<String, Object> creatorTwoProfile = (Map<String, Object>) result.get(1).get("creator");
        assertEquals(creatorOne, creatorOneProfile.get("user_id"));
        assertEquals("Ada", creatorOneProfile.get("display_name"));
        assertNull(creatorOneProfile.get("avatar_url"));
        assertEquals(creatorTwo, creatorTwoProfile.get("user_id"));
        assertNull(creatorTwoProfile.get("display_name"));
        assertEquals("https://img.example/u2", creatorTwoProfile.get("avatar_url"));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(supabaseAdminRestClient).getList(eq("profiles"), queryCaptor.capture(), any(TypeReference.class));
        String query = queryCaptor.getValue();
        assertTrue(query.contains("select="));
        assertTrue(query.contains(creatorOne.toString()));
        assertTrue(query.contains(creatorTwo.toString()));

        //verify
        verify(supabaseAdminRestClient).getList(eq("profiles"), any(), any(TypeReference.class));
    }

    // Service should skip lookup when the input list is null.
    @Test
    @SuppressWarnings("unchecked")
    void enrichWithCreatorProfiles_ShouldReturnNull_WhenItemsIsNull() {
        //arrange
        ContentCreatorEnrichmentServiceImpl service = new ContentCreatorEnrichmentServiceImpl(supabaseAdminRestClient);

        //act
        List<Map<String, Object>> result = service.enrichWithCreatorProfiles(null);

        //assert
        assertNull(result);

        //verify
        verify(supabaseAdminRestClient, never()).getList(eq("profiles"), any(), any(TypeReference.class));
    }

    // Service should skip lookup when the input list has no items.
    @Test
    @SuppressWarnings("unchecked")
    void enrichWithCreatorProfiles_ShouldReturnSameList_WhenItemsIsEmpty() {
        //arrange
        ContentCreatorEnrichmentServiceImpl service = new ContentCreatorEnrichmentServiceImpl(supabaseAdminRestClient);
        List<Map<String, Object>> items = new ArrayList<>();

        //act
        List<Map<String, Object>> result = service.enrichWithCreatorProfiles(items);

        //assert
        assertSame(items, result);
        assertTrue(result.isEmpty());

        //verify
        verify(supabaseAdminRestClient, never()).getList(eq("profiles"), any(), any(TypeReference.class));
    }

    // Service should skip lookup when all creator identifiers are invalid or missing.
    @Test
    @SuppressWarnings("unchecked")
    void enrichWithCreatorProfiles_ShouldReturnOriginalItems_WhenNoValidCreatorIdsExist() {
        //arrange
        ContentCreatorEnrichmentServiceImpl service = new ContentCreatorEnrichmentServiceImpl(supabaseAdminRestClient);
        Map<String, Object> invalidCreator = new LinkedHashMap<>();
        invalidCreator.put("creator_id", "bad-uuid");
        List<Map<String, Object>> items = new ArrayList<>(Arrays.asList(
                null,
                new LinkedHashMap<>() {{ put("creator_id", "bad-uuid"); }},
                new LinkedHashMap<>() {{ put("creator_id", null); }}
        ));

        //act
        List<Map<String, Object>> result = service.enrichWithCreatorProfiles(items);

        //assert
        assertSame(items, result);
        assertNull(invalidCreator.get("creator"));

        //verify
        verify(supabaseAdminRestClient, never()).getList(eq("profiles"), any(), any(TypeReference.class));
    }

    // Service should preserve first profile row for duplicate user IDs and ignore null items.
    @Test
    @SuppressWarnings("unchecked")
    void enrichWithCreatorProfiles_ShouldUseFirstProfile_WhenDuplicateRowsAreReturned() {
        //arrange
        ContentCreatorEnrichmentServiceImpl service = new ContentCreatorEnrichmentServiceImpl(supabaseAdminRestClient);
        UUID creatorId = UUID.randomUUID();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("creator_id", creatorId);
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item);
        items.add(null);

        List<Map<String, Object>> rows = List.of(
            Map.of("user_id", creatorId.toString(), "display_name", "First", "avatar_url", "first-url"),
            Map.of("user_id", creatorId.toString(), "display_name", "Second", "avatar_url", "second-url")
        );
        when(supabaseAdminRestClient.getList(eq("profiles"), any(), any(TypeReference.class))).thenReturn(rows);

        //act
        List<Map<String, Object>> result = service.enrichWithCreatorProfiles(items);

        //assert
        Map<String, Object> profile = (Map<String, Object>) result.get(0).get("creator");
        assertEquals("First", profile.get("display_name"));
        assertEquals("first-url", profile.get("avatar_url"));

        //verify
        verify(supabaseAdminRestClient).getList(eq("profiles"), any(), any(TypeReference.class));
    }
}