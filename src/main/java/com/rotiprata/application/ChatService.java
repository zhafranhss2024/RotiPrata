package com.rotiprata.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.rotiprata.infrastructure.openai.OpenAiRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import com.rotiprata.application.ModerationService;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import com.fasterxml.jackson.core.type.TypeReference;

import com.rotiprata.api.dto.ChatbotMessageDTO;

@Service
public class ChatService {

    private final OpenAiChatModel openAiChatModel;
    private final LessonService lessonService;
    private final SupabaseRestClient supabaseRestClient;
    private final ModerationService moderationService;

    public ChatService(OpenAiChatModel openAiChatModel, LessonService lessonService, SupabaseRestClient supabaseRestClient, ModerationService moderationService) {
        this.openAiChatModel = openAiChatModel;
        this.lessonService = lessonService;
        this.supabaseRestClient = supabaseRestClient;
        this.moderationService = moderationService;
    }
  
    public String ask(String accessToken, String question) {

        if (moderationService.isFlagged(question)) {
            return "Your question contains inappropriate content and cannot be processed.";
        }

        saveMessages(accessToken, question, "user");

        String context = lessonService.findRelevantLesson(accessToken, question);

        String prompt = """
        You are a learning assistant.

        Answer the question using the provided context.
        Explain in your own words in a simple and friendly way, suitable for a learner.
        If the answer is not explicitly in the context, you may infer the most likely answer based on clues in the context.
        If there is truly no way to answer, reply with "I don't know".

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
            result = "The assistant's response was flagged for inappropriate content.";
        }

        saveMessages(accessToken, result, "assistant");

        return result;
    }

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

    public List<ChatbotMessageDTO> getMessageHistory(String accessToken, String userId) {

        String query = "user_id=eq." + userId + "&order=timestamp.asc";

        return supabaseRestClient.getList(
            "user_chatbot_history",
            query,
            accessToken,
            new TypeReference<List<ChatbotMessageDTO>>() {}
        );
    }

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