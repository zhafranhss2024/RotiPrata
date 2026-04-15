package com.rotiprata.api.category.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.category.domain.Category;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements the category service workflows and persistence coordination used by the API layer.
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final TypeReference<List<Category>> CATEGORY_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseRestClient;

    /**
     * Creates a category service impl instance with its collaborators.
     */
    public CategoryServiceImpl(SupabaseAdminRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }


    /**
     * Returns the all.
     */
    /* Retrieves all categories from the database using Supabase client.*/
    @Override
    public List<Category> getAll() {
        return supabaseRestClient.getList("categories", "select=*", CATEGORY_LIST);
    }
}
