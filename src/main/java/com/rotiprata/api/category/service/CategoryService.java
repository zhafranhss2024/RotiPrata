package com.rotiprata.api.category.service;

import com.rotiprata.api.category.domain.Category;
import java.util.List;

/**
 * Defines the category service operations exposed to the API layer.
 */
public interface CategoryService {
    /**
     * Returns the all.
     */
    /**
     * Retrieves all categories from the database.
     *
     * @return list of categories
     */
    List<Category> getAll();
}
