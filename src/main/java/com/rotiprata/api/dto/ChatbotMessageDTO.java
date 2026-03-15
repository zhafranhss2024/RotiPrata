package com.rotiprata.api.dto;

import java.time.Instant;

public class ChatbotMessageDTO {

    private String role;       
    private String message;    
    private Instant timestamp; 

    public ChatbotMessageDTO() {}

    public ChatbotMessageDTO(String role, String content, Instant timestamp) { 
        this.role = role; 
        this.message = content; 
        this.timestamp = timestamp; 
    }

    public String getRole() { return role; } 
    public void setRole(String role) { this.role = role; }

    public String getMessage() { return message; } 
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; } 
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
