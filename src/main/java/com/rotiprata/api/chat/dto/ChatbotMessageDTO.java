package com.rotiprata.api.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Documents the chatbot message dto type.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotMessageDTO {

    private String role;
    private String message;
    private Instant timestamp;
}
