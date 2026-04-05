package com.rotiprata.api.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.lesson.service.LessonService;
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

    /**
     * Constructor for dependency injection.
     */
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

    // ================= ASK =================

    @Override
    public String ask(String accessToken, String question) {

        // Check user input for inappropriate content
        if (moderationService.isFlagged(question)) {
            return "Your question contains inappropriate content and cannot be processed.";
        }

        // Save user message to history
        saveMessages(accessToken, question, "user");

        // Get relevant lesson context
        String context = lessonService.findRelevantLesson(accessToken, question);

        // Build prompt for OpenAI
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

        // Check AI response for inappropriate content
        if (moderationService.isFlagged(result)) {
            result = "The assistant's response was flagged for inappropriate content.";
        }

        // Save AI message to history
        saveMessages(accessToken, result, "assistant");

        return result;
    }

    // ================= SAVE MESSAGES =================

    @Override
    public void saveMessages(String accessToken, String message, String role) {

        ChatbotMessageDTO dto = new ChatbotMessageDTO(
            role,
            message,
            Instant.now()
        );

        List<ChatbotMessageDTO> messages = List.of(dto);

        supabaseRestClient.postList(
            "user_chatbot_history",
            messages,
            accessToken,
            new TypeReference<List<ChatbotMessageDTO>>() {}
        );
    }

    // ================= FETCH HISTORY =================

    @Override
    public List<ChatbotMessageDTO> getMessageHistory(String accessToken, String userId) {

        String query = "user_id=eq." + userId + "&order=timestamp.asc";

        return supabaseRestClient.getList(
            "user_chatbot_history",
            query,
            accessToken,
            new TypeReference<List<ChatbotMessageDTO>>() {}
        );
    }

    // ================= DELETE HISTORY =================

    @Override
    public void deleteMessageHistory(String accessToken, String userId) {

        String query = "user_id=eq." + userId;

        supabaseRestClient.deleteList(
            "user_chatbot_history",
            query,
            accessToken,
            new TypeReference<List<Map<String, Object>>>() {}
        );
    }
}