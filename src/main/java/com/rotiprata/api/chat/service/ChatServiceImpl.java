package com.rotiprata.api.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.api.exception.ChatServiceException;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Implementation of ChatService.
 * Uses OpenAI model to answer user questions, maintains chat history, and applies content moderation.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private final OpenAiChatModel openAiChatModel;
    private final LessonService lessonService;
    private final SupabaseRestClient supabaseRestClient;
    private final ModerationService moderationService;

    // Constant
    private static final String USER_ROLE = "user";
    private static final String ASSISTANT_ROLE = "assistant";
    private static final String CHAT_HISTORY_TABLE = "user_chatbot_history";
    private static final String FLAGGED_USER_MESSAGE = "Your question contains inappropriate content and cannot be processed.";
    private static final String FLAGGED_ASSISTANT_MESSAGE = "The assistant's response was flagged for inappropriate content.";


    public ChatServiceImpl(
            OpenAiChatModel openAiChatModel,
            LessonService lessonService,
            SupabaseRestClient supabaseRestClient,
            ModerationService moderationService
    ) {
        this.openAiChatModel = openAiChatModel;
        this.lessonService = lessonService;
        this.supabaseRestClient = supabaseRestClient;
        this.moderationService = moderationService;
    }

    // Sends a user question to OpenAI, applies moderation, saves messages, and returns the assistant's reply
    @Override
    public String ask(String accessToken, String question) {
        try {
            // Check user input
            if (moderationService.isFlagged(question)) {
                return FLAGGED_USER_MESSAGE;
            }

            // Save user message
            saveMessages(accessToken, question, USER_ROLE);

            // Get relevant lesson context
            String context = lessonService.findRelevantLesson(accessToken, question);

            // Build prompt
            String prompt = """
                You are a helpful learning assistant.

                Answer the question using the provided context.
                Explain in your own words in a simple and friendly way, suitable for a learner.
                If the answer is not explicitly in the context, you may infer the most likely answer based on clues in the context.
                If there is truly no way to answer, politely say: 
                'I'm only able to help with lesson-related questions.'

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

            String result = openAiChatModel.call(new Prompt(new UserMessage(prompt)))
                                           .getResult()
                                           .getOutput()
                                           .getText();

            if (moderationService.isFlagged(result)) {
                result = FLAGGED_ASSISTANT_MESSAGE;
            }

            saveMessages(accessToken, result, ASSISTANT_ROLE);

            return result;

        } catch (Exception e) {
            throw new ChatServiceException("Failed to process chat request", e);
        }
    }

    // Saves a chat message (user or assistant) to the database
    @Override
    public void saveMessages(String accessToken, String message, String role) {
        try {
            ChatbotMessageDTO dto = new ChatbotMessageDTO(role, message, Instant.now());
            List<ChatbotMessageDTO> messages = List.of(dto);

            supabaseRestClient.postList(
                CHAT_HISTORY_TABLE,
                messages,
                accessToken,
                new TypeReference<List<ChatbotMessageDTO>>() {}
            );
        } catch (Exception e) {
            throw new ChatServiceException("Failed to save chat messages", e);
        }
    }

    // Retrieves the chat history for a given user in chronological order
    @Override
    public List<ChatbotMessageDTO> getMessageHistory(String accessToken, String userId) {
        try {
            String query = "user_id=eq." + userId + "&order=timestamp.asc";

            return supabaseRestClient.getList(
                CHAT_HISTORY_TABLE,
                query,
                accessToken,
                new TypeReference<List<ChatbotMessageDTO>>() {}
            );
        } catch (Exception e) {
            throw new ChatServiceException("Failed to fetch chat history", e);
        }
    }

    // Deletes all chat history for a given user
    @Override
    public void deleteMessageHistory(String accessToken, String userId) {
        try {
            String query = "user_id=eq." + userId;

            supabaseRestClient.deleteList(
                CHAT_HISTORY_TABLE,
                query,
                accessToken,
                new TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (Exception e) {
            throw new ChatServiceException("Failed to delete chat history", e);
        }
    }
}