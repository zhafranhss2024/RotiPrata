package com.rotiprata.api;

import java.util.List;

import com.rotiprata.api.dto.ContentSearchDTO;
import com.rotiprata.application.BrowsingService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browse")
public class BrowsingController {

    private final BrowsingService browsingService;

    public BrowsingController(BrowsingService browsingService) {
        this.browsingService = browsingService;
    }

    @GetMapping("/search")
    public List<ContentSearchDTO> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String filter,
            @AuthenticationPrincipal Jwt jwt 
    ) {
        String accessToken = jwt.getTokenValue();

        return browsingService.search(query, filter, accessToken);
    }
}
