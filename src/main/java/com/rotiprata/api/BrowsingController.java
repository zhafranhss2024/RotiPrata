package com.rotiprata.api;

import java.util.List;

import com.rotiprata.application.BrowsingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browse")
public class BrowsingController {
    
    private final BrowsingService browsingService;

    public BrowsingController(BrowsingService broswingService) {
        this.browsingService = broswingService;
    }

    @GetMapping("/query")
    public List<Content> search(@RequestParam(required = false) String query, @RequestParam(required = false) String[] filter) {

        // After I implement the AI suggestions??
        // if (query == null) {
        //     return browsingService.getSuggestions();
        // } else {
        //     return browsingService.search(query, filter);
        // }

        return browsingService.search(query, filter);

    }
}
