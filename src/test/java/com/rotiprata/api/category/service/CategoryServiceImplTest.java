package com.rotiprata.api.category.service;

import com.rotiprata.api.category.domain.Category;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl tests")
class CategoryServiceImplTest {

    @Mock
    private SupabaseAdminRestClient supabaseAdminRestClient;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        sampleCategory = new Category();
        sampleCategory.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        sampleCategory.setName("Test Category");
    }

    // Verifies getAll returns categories when Supabase client provides data.
    @Test
    @SuppressWarnings("unchecked")
    void getAll_ShouldReturnCategories_WhenCategoriesExist() {
        //arrange
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any(TypeReference.class)))
                .thenReturn(List.of(sampleCategory));

        //act
        List<Category> result = categoryService.getAll();

        //assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Category", result.get(0).getName());

        //verify
        verify(supabaseAdminRestClient, times(1))
                .getList(anyString(), anyString(), any(TypeReference.class));
    }

    // Verifies getAll returns empty list when no categories exist.
    @Test
    @SuppressWarnings("unchecked")
    void getAll_ShouldReturnEmptyList_WhenNoCategoriesExist() {
        //arrange
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any(TypeReference.class)))
                .thenReturn(List.of());

        //act
        List<Category> result = categoryService.getAll();

        //assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        //verify
        verify(supabaseAdminRestClient, times(1))
                .getList(anyString(), anyString(), any(TypeReference.class));
    }

    // Verifies getAll throws exception when Supabase client fails.
    @Test
    @SuppressWarnings("unchecked")
    void getAll_ShouldThrowException_WhenSupabaseClientFails() {
        //arrange
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("Supabase failure"));

        //act & assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> categoryService.getAll());
        assertEquals("Supabase failure", ex.getMessage());

        //verify
        verify(supabaseAdminRestClient, times(1))
                .getList(anyString(), anyString(), any(TypeReference.class));
    }

    // Verifies getList is called with correct table and query parameters.
    @Test
    @SuppressWarnings("unchecked")
    void getAll_ShouldCallSupabaseWithCorrectParameters() {
        //arrange
        when(supabaseAdminRestClient.getList(anyString(), anyString(), any(TypeReference.class)))
                .thenReturn(List.of(sampleCategory));

        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TypeReference> typeCaptor = ArgumentCaptor.forClass(TypeReference.class);

        //act
        categoryService.getAll();

        //assert
        verify(supabaseAdminRestClient).getList(tableCaptor.capture(), queryCaptor.capture(), typeCaptor.capture());
        assertEquals("categories", tableCaptor.getValue());
        assertEquals("select=*", queryCaptor.getValue());
        assertNotNull(typeCaptor.getValue());

        //verify
        verify(supabaseAdminRestClient, times(1))
                .getList(anyString(), anyString(), any(TypeReference.class));
    }
}
