package com.rotiprata.api.tag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TagServiceImpl tests")
class TagServiceImplTest {

    @Mock
    private SupabaseAdminRestClient adminRestClient;

    private TagServiceImpl tagService;

    private static final TypeReference<List<Map<String, Object>>> TAG_ROWS = new TypeReference<>() {};

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(adminRestClient);
    }

    // Verifies that searchTags returns unique and trimmed tags.
    @Test
    @SuppressWarnings("unchecked")
    void searchTags_ShouldReturnUniqueTags_WhenRowsExist() {
        //arrange
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("tag", "java");
        Map<String, Object> row2 = new HashMap<>();
        row2.put("tag", "spring");
        Map<String, Object> row3 = new HashMap<>();
        row3.put("tag", "java");
        Map<String, Object> row4 = new HashMap<>();
        row4.put("tag", " kotlin ");
        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        doReturn(rows).when(adminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));

        //act
        List<String> result = tagService.searchTags(null);

        //assert
        assertEquals(3, result.size());
        assertTrue(result.contains("java"));
        assertTrue(result.contains("spring"));
        assertTrue(result.contains("kotlin"));

        //verify
        verify(adminRestClient, times(1)).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    // Verifies that searchTags skips null or blank tag values.
    @Test
    @SuppressWarnings("unchecked")
    void searchTags_ShouldSkipNullOrBlankTags_WhenRowsContainInvalidValues() {
        //arrange
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("tag", null);
        Map<String, Object> row2 = new HashMap<>();
        row2.put("tag", "");
        Map<String, Object> row3 = new HashMap<>();
        row3.put("tag", " validTag ");
        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        doReturn(rows).when(adminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));

        //act
        List<String> result = tagService.searchTags("");

        //assert
        assertEquals(1, result.size());
        assertEquals("validTag", result.get(0));

        //verify
        verify(adminRestClient, times(1)).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    // Verifies searchTags applies query normalization and escapes characters for ilike.
    @Test
    @SuppressWarnings("unchecked")
    void searchTags_ShouldEscapeQuery_WhenSpecialCharactersProvided() {
        //arrange
        String query = " Java%* ";
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(List.of(new HashMap<>(Map.of("tag", "java"))))
                .when(adminRestClient).getList(eq("content_tags"), queryCaptor.capture(), any(TypeReference.class));

        //act
        List<String> result = tagService.searchTags(query);

        //assert
        assertEquals(1, result.size());
        assertEquals("java", result.get(0));
        String capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.contains("ilike.*Java*") || capturedQuery.contains("ilike.*Java"));

        //verify
        verify(adminRestClient, times(1)).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    // Verifies searchTags returns empty list when no rows are returned.
    @Test
    @SuppressWarnings("unchecked")
    void searchTags_ShouldReturnEmptyList_WhenNoRowsExist() {
        //arrange
        doReturn(Collections.emptyList())
                .when(adminRestClient).getList(eq("content_tags"), anyString(), any(TypeReference.class));

        //act
        List<String> result = tagService.searchTags("anything");

        //assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        //verify
        verify(adminRestClient, times(1)).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }

    // Verifies that escapeForIlike removes % and * characters.
    @Test
    void escapeForIlike_ShouldRemoveSpecialCharacters_WhenInputContainsPercentOrAsterisk() throws Exception {
        //arrange
        String input = "%Hello*World%";

        //act
        var method = tagService.getClass().getDeclaredMethod("escapeForIlike", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(tagService, input);

        //assert
        assertEquals("HelloWorld", result);
    }

    // Verifies buildQuery correctly encodes parameters.
    @Test
    void buildQuery_ShouldBuildQueryString_WhenParamsProvided() throws Exception {
        //arrange
        Map<String, String> params = Map.of("select", "tag", "limit", "10");

        //act
        var method = tagService.getClass().getDeclaredMethod("buildQuery", Map.class);
        method.setAccessible(true);
        String result = (String) method.invoke(tagService, params);

        //assert
        assertNotNull(result);
        assertTrue(result.contains("select=tag"));
        assertTrue(result.contains("limit=10"));
    }

    // Verifies searchTags applies limit and order parameters correctly.
    @Test
    @SuppressWarnings("unchecked")
    void searchTags_ShouldIncludeLimitAndOrder_WhenQueryIsBlank() {
        //arrange
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        doReturn(List.of(new HashMap<>(Map.of("tag", "java"))))
                .when(adminRestClient).getList(eq("content_tags"), queryCaptor.capture(), any(TypeReference.class));

        //act
        tagService.searchTags("");

        //assert
        String capturedQuery = queryCaptor.getValue();
        assertTrue(capturedQuery.contains("order=tag.asc"));
        assertTrue(capturedQuery.contains("limit=20"));

        //verify
        verify(adminRestClient, times(1)).getList(eq("content_tags"), anyString(), any(TypeReference.class));
    }
}