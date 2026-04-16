package com.rotiprata.api.chat.service;

import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.exception.ChatServiceException;

import java.util.List;

/**
 * Service interface for chat-related operations.
 * Handles user questions, chatbot responses, and chat history.
 */
public interface ChatService {

    /**
     * Handles ask.
     */
    /**
     * Processes a user's question and returns the assistant's response.
     *
     * @param accessToken user's access token for authorization
     * @param question    the question string
     * @return assistant's answer
     * @throws ChatServiceException if the request cannot be processed or an internal error occurs
     */
    String ask(String accessToken, String question);

    /**
     * Handles message.
     */
    /**
     * Saves a message (from user or assistant) to the chat history.
     *
     * @param accessToken user's access token for authorization
     * @param message     the message content
     * @param role        the role of the message sender ("user" or "assistant")
     * @throws ChatServiceException if saving the message fails
     */
    void saveMessages(String accessToken, String message, String role);

    /**
     * Returns the message history.
     */
    /**
     * Fetches the complete chat history for a given user.
     *
     * @param accessToken user's access token for authorization
     * @param userId      the user ID
     * @return list of messages in chronological order
     * @throws ChatServiceException if fetching chat history fails
     */
    List<ChatbotMessageDTO> getMessageHistory(String accessToken, String userId);

    /**
     * Deletes the message history.
     */
    /**
     * Deletes all chat history for a given user.
     *
     * @param accessToken user's access token for authorization
     * @param userId      the user ID
     * @throws ChatServiceException if deleting chat history fails
     */
    void deleteMessageHistory(String accessToken, String userId);
}
