package com.rotiprata.api.browsing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.dto.GetHistoryDTO;
import com.rotiprata.api.browsing.dto.SaveHistoryDTO;
import com.rotiprata.api.content.service.ContentService;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("BrowsingServiceImpl tests")
class BrowsingServiceImplTest {

    @Mock
    private ContentService contentService;

    @Mock
    private LessonService lessonService;

    @Mock
    private SupabaseRestClient supabaseRestClient;

    @InjectMocks
    private BrowsingServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Initialize mocks before each test
    }

    // ===== SEARCH =====
    @Test
    // Should return all content and lessons when no filter is applied
    void search_ShouldReturnAllResults_WhenFilterIsEmpty() {
        // Arrange: mock content and lesson search results
        when(contentService.getFilteredContent(anyString(), isNull(), anyString()))
                .thenReturn(List.of(new ContentSearchDTO("1", "video", "title1", "desc1", "snippet1")));
        when(lessonService.searchLessons(anyString(), anyString()))
                .thenReturn(List.of(Map.of("id", "2", "title", "lessonTitle", "description", "lessonDesc")));

        // Act: perform search with empty filter
        List<ContentSearchDTO> results = service.search("query", "", "token");

        // Assert: results contain both video and lesson
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.content_type().equals("video")));
        assertTrue(results.stream().anyMatch(r -> r.content_type().equals("lesson")));

        // Verify: services called exactly once
        verify(contentService, times(1)).getFilteredContent(anyString(), isNull(), anyString());
        verify(lessonService, times(1)).searchLessons(anyString(), anyString());
    }

    @Test
    // Should return only video results when filter is "video"
    void search_ShouldReturnOnlyVideos_WhenFilterIsVideo() {
        // Arrange: mock video content only
        when(contentService.getFilteredContent(anyString(), eq("video"), anyString()))
                .thenReturn(List.of(new ContentSearchDTO("1", "video", "title1", "desc1", "snippet1")));

        // Act: search with video filter
        List<ContentSearchDTO> results = service.search("query", "video", "token");

        // Assert: all results are videos
        assertEquals(1, results.size());
        assertEquals("video", results.get(0).content_type());

        // Verify service call
        verify(contentService, times(1)).getFilteredContent(anyString(), eq("video"), anyString());
    }

    @Test
    // Should return only lesson results when filter is "lesson"
    void search_ShouldReturnOnlyLessons_WhenFilterIsLesson() {
        // Arrange: mock lesson search only
        when(lessonService.searchLessons(anyString(), anyString()))
                .thenReturn(List.of(Map.of("id", "2", "title", "lessonTitle", "description", "lessonDesc")));

        // Act: search with lesson filter
        List<ContentSearchDTO> results = service.search("query", "lesson", "token");

        // Assert: all results are lessons and mapped correctly
        assertEquals(1, results.size());
        assertEquals("lesson", results.get(0).content_type());
        assertEquals("lessonTitle", results.get(0).title());

        // Verify lesson service was called
        verify(lessonService, times(1)).searchLessons(anyString(), anyString());
    }

    // ===== SAVE HISTORY =====
    @Test
    // Should upsert search history into Supabase when valid data provided
    void saveHistory_ShouldUpsert_WhenValid() {
        // Arrange: valid history entry
        Instant now = Instant.now();

        // Act & Assert: no exception thrown
        assertDoesNotThrow(() -> service.saveHistory("user1", "query", now, "token"));

        // Verify upsert called with correct table and conflict keys
        verify(supabaseRestClient, times(1)).upsertList(
                eq("search_history"),
                eq("on_conflict=user_id,query"),
                anyList(),
                eq("token"),
                ArgumentMatchers.<TypeReference<List<SaveHistoryDTO>>>any()
        );
    }

    @Test
    // Should skip saving when search query is blank
    void saveHistory_ShouldSkip_WhenQueryEmpty() {
        // Arrange: blank query

        // Act: saveHistory with empty query
        service.saveHistory("user1", " ", Instant.now(), "token");

        // Assert: supabase client never called
        verifyNoInteractions(supabaseRestClient);
    }

    @Test
    // Should throw RuntimeException if Supabase upsert fails
    void saveHistory_ShouldThrowRuntime_WhenUpsertFails() {
        // Arrange: simulate Supabase failure on upsert
        doThrow(new RuntimeException("upsert fail")).when(supabaseRestClient)
                .upsertList(eq("search_history"), eq("on_conflict=user_id,query"), anyList(),
                        eq("token"), ArgumentMatchers.<TypeReference<List<SaveHistoryDTO>>>any());

        // Act & Assert: exception thrown when saving history
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.saveHistory("user1", "query", Instant.now(), "token"));
        assertTrue(ex.getMessage().contains("Failed to save search history"));
    }

    // ===== FETCH HISTORY =====
    @Test
    // Should fetch list of search history successfully
    void fetchHistory_ShouldReturnList_WhenDataExists() {
        // Arrange: mock returned history
        List<GetHistoryDTO> expected = List.of(new GetHistoryDTO());
        when(supabaseRestClient.getList(eq("search_history"), anyString(), eq("token"), ArgumentMatchers.<TypeReference<List<GetHistoryDTO>>>any()))
                .thenReturn(expected);

        // Act: fetch history
        List<GetHistoryDTO> result = service.fetchHistory("user1", "token");

        // Assert: returned list matches expected
        assertEquals(expected, result);

        // Verify supabase client called
        verify(supabaseRestClient, times(1)).getList(
                eq("search_history"),
                anyString(),
                eq("token"),
                ArgumentMatchers.<TypeReference<List<GetHistoryDTO>>>any()
        );
    }

    @Test
    // Should throw RuntimeException if Supabase client fails
    void fetchHistory_ShouldThrowRuntime_WhenClientFails() {
        // Arrange: simulate supabase failure
        when(supabaseRestClient.getList(anyString(), anyString(), anyString(), ArgumentMatchers.<TypeReference<List<GetHistoryDTO>>>any()))
                .thenThrow(new RuntimeException("fail"));

        // Act & Assert: exception thrown
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.fetchHistory("user1", "token"));
        assertTrue(ex.getMessage().contains("Failed to fetch search history"));
    }

    // ===== DELETE HISTORY =====
    @Test
    // Should call Supabase deleteList for a valid history ID
    void deleteHistoryById_ShouldCallDelete_WhenValidId() {
        // Arrange: valid history ID
        String id = "id1";

        // Act & Assert: deletion should not throw
        assertDoesNotThrow(() -> service.deleteHistoryById(id, "user1", "token"));

        // Verify deleteList called
        verify(supabaseRestClient, times(1)).deleteList(
                eq("search_history"),
                anyString(),
                eq("token"),
                ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()
        );
    }

    @Test
    // Should skip deletion when ID is blank
    void deleteHistoryById_ShouldSkip_WhenIdBlank() {
        // Arrange: blank ID

        // Act: deleteHistoryById called with blank ID
        service.deleteHistoryById(" ", "user1", "token");

        // Assert: supabase client never called
        verifyNoInteractions(supabaseRestClient);
    }

    @Test
    // Should throw RuntimeException if Supabase delete fails
    void deleteHistoryById_ShouldThrowRuntime_WhenDeleteFails() {
        // Arrange: simulate Supabase failure on delete
        doThrow(new RuntimeException("delete fail")).when(supabaseRestClient)
                .deleteList(eq("search_history"), anyString(), eq("token"),
                        ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any());

        // Act & Assert: exception thrown when deleting history
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteHistoryById("id1", "user1", "token"));
        assertTrue(ex.getMessage().contains("Failed to delete search history"));
    }
    
    // ===== INTERNAL HELPER - buildSnippet =====
    @Test
    // Should truncate long description into snippet with "..."
    void buildSnippet_ShouldReturnTruncated_WhenLong() throws Exception {
        // Arrange: long description string
        String longDesc = "a".repeat(200);

        // Act: invoke private method using reflection
        Method method = service.getClass().getDeclaredMethod("buildSnippet", String.class);
        method.setAccessible(true);
        String snippet = (String) method.invoke(service, longDesc);

        // Assert: snippet truncated correctly
        assertTrue(snippet.length() <= 103); // 100 + "..."
        assertTrue(snippet.endsWith("..."));
    }

    @Test
    // Should return original description if short
    void buildSnippet_ShouldReturnOriginal_WhenShort() throws Exception {
        // Arrange: short description string
        String shortDesc = "short";

        // Act: invoke private method
        Method method = service.getClass().getDeclaredMethod("buildSnippet", String.class);
        method.setAccessible(true);
        String snippet = (String) method.invoke(service, shortDesc);

        // Assert: snippet equals original
        assertEquals(shortDesc, snippet);
    }

    // ===== INTERNAL HELPER - toStringValue =====
    @Test
    // Should convert non-null object to string
    void toStringValue_ShouldReturnString_WhenValueNotNull() throws Exception {
        // Arrange: integer object
        Integer value = 42;

        // Act: invoke private method
        Method method = service.getClass().getDeclaredMethod("toStringValue", Object.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, value);

        // Assert: returns string
        assertEquals("42", result);
    }

    @Test
    // Should return null when object is null
    void toStringValue_ShouldReturnNull_WhenValueIsNull() throws Exception {
        // Arrange: null object

        // Act: invoke private method
        Method method = service.getClass().getDeclaredMethod("toStringValue", Object.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, (Object) null);

        // Assert: returns null
        assertNull(result);
    }
}
