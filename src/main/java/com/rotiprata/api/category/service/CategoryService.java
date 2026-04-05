package com.rotiprata.api.category.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.category.domain.Category;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private static final TypeReference<List<Category>> CATEGORY_LIST = new TypeReference<>() {};

    private final SupabaseAdminRestClient supabaseRestClient;

    public CategoryService(SupabaseAdminRestClient supabaseRestClient) {
        this.supabaseRestClient = supabaseRestClient;
    }

    public List<Category> getAll() {
        return supabaseRestClient.getList("categories", "select=*", CATEGORY_LIST);
    }
}
