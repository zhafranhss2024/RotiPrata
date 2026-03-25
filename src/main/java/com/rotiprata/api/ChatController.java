package com.rotiprata.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.rotiprata.application.ChatService;
import com.rotiprata.security.ChatRateLimiter;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final int MAX_QUESTION_LENGTH = 250;

    private final ChatService chatService;
    private final ChatRateLimiter chatRateLimiter;
    
    public ChatController(ChatService chatService, ChatRateLimiter chatRateLimiter) {
        this.chatService = chatService;
        this.chatRateLimiter = chatRateLimiter;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@AuthenticationPrincipal Jwt jwt , @RequestBody String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String userId = jwt.getSubject();
        chatRateLimiter.consumeOrThrow(userId);
        String accessToken = jwt.getTokenValue();
        String answer = chatService.ask(accessToken, normalizedQuestion);
        return Map.of("reply", answer);
    }

    private String normalizeQuestion(String question) {
        if (question == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
        String normalized = question.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question is required");
        }
        if (normalized.length() > MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must be 250 characters or fewer");
        }
        return normalized;
    }
}
