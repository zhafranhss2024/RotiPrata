package com.rotiprata.api.chat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.rotiprata.api.chat.service.ChatService;
import com.rotiprata.security.ChatRateLimiter;

@RestController
@RequestMapping("/api")
public class ChatController {

    // Maximum allowed length for a chat question
    private static final int MAX_QUESTION_LENGTH = 250;

    // Service for processing chat questions
    private final ChatService chatService;

    // Rate limiter to prevent excessive requests per user
    private final ChatRateLimiter chatRateLimiter;
    
    // Constructor injection of chat service and rate limiter
    public ChatController(ChatService chatService, ChatRateLimiter chatRateLimiter) {
        this.chatService = chatService;
        this.chatRateLimiter = chatRateLimiter;
    }

    // Handles POST /chat requests: validates, rate-limits, and returns AI response
    @PostMapping("/chat")
    public Map<String, String> chat(@AuthenticationPrincipal Jwt jwt , @RequestBody String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String userId = jwt.getSubject();
        chatRateLimiter.consumeOrThrow(userId);
        String accessToken = jwt.getTokenValue();
        String answer = chatService.ask(accessToken, normalizedQuestion);
        return Map.of("reply", answer);
    }

    // Normalizes and validates question length; throws 400 if invalid
    private String normalizeQuestion(String question) {
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
