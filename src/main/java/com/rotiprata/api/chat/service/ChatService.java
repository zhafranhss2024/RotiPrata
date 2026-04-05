package com.rotiprata.api.chat.service;

import com.rotiprata.api.chat.dto.ChatbotMessageDTO;

import java.util.List;

/**
 * Service interface for chat-related operations.
 * Handles user questions, chatbot responses, and chat history.
 */
public interface ChatService {

    /**
     * Processes a user's question and returns the assistant's response.
     *
     * @param accessToken user's access token for authorization
     * @param question    the question string
     * @return assistant's answer
     */
    String ask(String accessToken, String question);

    /**
     * Saves a message (from user or assistant) to the chat history.
     *
     * @param accessToken user's access token for authorization
     * @param message     the message content
     * @param role        the role of the message sender ("user" or "assistant")
     */
    void saveMessages(String accessToken, String message, String role);

    /**
     * Fetches the complete chat history for a given user.
     *
     * @param accessToken user's access token for authorization
     * @param userId      the user ID
     * @return list of messages in chronological order
     */
    List<ChatbotMessageDTO> getMessageHistory(String accessToken, String userId);

    /**
     * Deletes all chat history for a given user.
     *
     * @param accessToken user's access token for authorization
     * @param userId      the user ID
     */
    void deleteMessageHistory(String accessToken, String userId);
}