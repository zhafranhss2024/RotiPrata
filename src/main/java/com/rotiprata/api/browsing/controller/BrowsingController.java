package com.rotiprata.api.browsing.controller;

import java.util.List;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.browsing.service.BrowsingService;
import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * Exposes REST endpoints for the browsing controller flows.
 */
@RestController
@RequestMapping("/api")
public class BrowsingController {

    private final BrowsingService browsingService;

    /**
     * Creates a browsing controller instance with its collaborators.
     */
    // Constructor injection of browsing service
    public BrowsingController(BrowsingService browsingService) {
        this.browsingService = browsingService;
    }

    /**
     * Handles get mapping.
     */
    // Searches content with optional query and filter, using the user's access token
    @GetMapping("/search-results")
    public List<ContentSearchDTO> searchResults(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String filter,
            @AuthenticationPrincipal Jwt jwt 
    ) {
        String accessToken = jwt.getTokenValue();
        return browsingService.search(query, filter, accessToken);
    }

    /**
     * Handles search.
     */
    @Hidden
    @Deprecated
    @GetMapping("/search")
    public List<ContentSearchDTO> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String filter,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return searchResults(query, filter, jwt);
    }
        
}
