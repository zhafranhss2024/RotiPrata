package com.rotiprata.api;

import com.rotiprata.api.dto.FeedResponse;
import com.rotiprata.application.FeedService;
import com.rotiprata.security.SecurityUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FeedController {
    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feed")
    public FeedResponse feed(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(value = "page", defaultValue = "1") int page
    ) {
        if (jwt == null) {
            return new FeedResponse(java.util.List.of(), false);
        }
        return feedService.getFeed(SecurityUtils.getAccessToken(), page);
    }
}
