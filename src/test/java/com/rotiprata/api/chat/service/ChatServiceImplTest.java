package com.rotiprata.api.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rotiprata.api.chat.dto.ChatbotMessageDTO;
import com.rotiprata.api.exception.ChatServiceException;
import com.rotiprata.api.lesson.service.LessonService;
import com.rotiprata.infrastructure.supabase.SupabaseRestClient;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl tests")
class ChatServiceImplTest {

    @Mock
    private OpenAiChatModel openAiChatModel;

    @Mock
    private LessonService lessonService;

    @Mock
    private SupabaseRestClient supabaseRestClient;

    @Mock
    private ModerationService moderationService;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(openAiChatModel, lessonService, supabaseRestClient, moderationService);
    }

    @Test
    // Returns a moderation warning when the user question is flagged
    void ask_ShouldReturnModerationMessage_WhenQuestionIsFlagged() {
        when(moderationService.isFlagged("bad question")).thenReturn(true);

        String result = service.ask("token", "bad question");

        assertEquals("Your question contains inappropriate content and cannot be processed.", result);
        verify(lessonService, never()).findRelevantLesson(any(), any());
        verify(openAiChatModel, never()).call(any(Prompt.class));
        verify(supabaseRestClient, never()).postList(any(), any(), any(), ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Returns assistant response and persists both user and assistant messages when content is safe
    void ask_ShouldReturnAssistantResponseAndPersistMessages_WhenQuestionAndResponseAreSafe() {
        // Arrange: Test that a safe question and assistant response are returned and both messages are saved
        when(moderationService.isFlagged("What is Newton's first law?")).thenReturn(false);
        when(lessonService.findRelevantLesson("token", "What is Newton's first law?"))
            .thenReturn("Newton's first law is inertia.");
        when(openAiChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("An object keeps moving unless a force changes it.");
        when(moderationService.isFlagged("An object keeps moving unless a force changes it.")).thenReturn(false);

        // Act
        String result = service.ask("token", "What is Newton's first law?");

        // Assert response text
        assertEquals("An object keeps moving unless a force changes it.", result);

        // Capture both calls to supabaseRestClient.postList
        ArgumentCaptor<List<ChatbotMessageDTO>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(supabaseRestClient, times(2)).postList(
            eq("user_chatbot_history"),
            messagesCaptor.capture(),
            eq("token"),
            ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()
        );

        // Verify both saved messages
        List<List<ChatbotMessageDTO>> captured = messagesCaptor.getAllValues();
        assertEquals(2, captured.size());

        // First call: user message
        ChatbotMessageDTO userMessage = captured.get(0).get(0);
        assertEquals("user", userMessage.getRole());
        assertEquals("What is Newton's first law?", userMessage.getMessage());
        assertNotNull(userMessage.getTimestamp());

        // Second call: assistant message
        ChatbotMessageDTO assistantSavedMessage = captured.get(1).get(0);
        assertEquals("assistant", assistantSavedMessage.getRole());
        assertEquals("An object keeps moving unless a force changes it.", assistantSavedMessage.getMessage());
        assertNotNull(assistantSavedMessage.getTimestamp());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Returns fallback message and persists both messages when assistant response is flagged
    void ask_ShouldReturnFallbackMessageAndPersist_WhenAssistantResponseIsFlagged() {
        // Arrange: Test that flagged assistant responses return fallback message and both messages are saved
        when(moderationService.isFlagged("Tell me about photosynthesis.")).thenReturn(false);
        when(lessonService.findRelevantLesson("token", "Tell me about photosynthesis."))
            .thenReturn("Plants use sunlight to make food.");
        when(openAiChatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("unsafe answer");
        when(moderationService.isFlagged("unsafe answer")).thenReturn(true);

        // Act
        String result = service.ask("token", "Tell me about photosynthesis.");

        // Assert fallback response
        assertEquals("The assistant's response was flagged for inappropriate content.", result);

        // Capture both calls to supabaseRestClient.postList
        ArgumentCaptor<List<ChatbotMessageDTO>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(supabaseRestClient, times(2)).postList(
            eq("user_chatbot_history"),
            messagesCaptor.capture(),
            eq("token"),
            ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()
        );

        List<List<ChatbotMessageDTO>> captured = messagesCaptor.getAllValues();
        assertEquals(2, captured.size());

        // Second call: assistant fallback message
        ChatbotMessageDTO assistantSavedMessage = captured.get(1).get(0);
        assertEquals("assistant", assistantSavedMessage.getRole());
        assertEquals("The assistant's response was flagged for inappropriate content.", assistantSavedMessage.getMessage());
    }

    @Test
    // Throws ChatServiceException when the OpenAI call fails
    void ask_ShouldThrowChatServiceException_WhenOpenAiCallFails() {
        when(moderationService.isFlagged("some question")).thenReturn(false);
        when(lessonService.findRelevantLesson(any(), any())).thenReturn("context");
        when(openAiChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI failure"));

        ChatServiceException ex = assertThrows(
            ChatServiceException.class,
            () -> service.ask("token", "some question")
        );

        assertEquals("Failed to process chat request", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    @SuppressWarnings("unchecked")
    // Persists messages with correct role and content
    void saveMessages_ShouldPersistRoleAndMessage_WhenCalled() {
        service.saveMessages("token", "hello", "assistant");

        ArgumentCaptor<List<ChatbotMessageDTO>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(supabaseRestClient).postList(
            eq("user_chatbot_history"),
            messagesCaptor.capture(),
            eq("token"),
            ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()
        );

        List<ChatbotMessageDTO> messages = messagesCaptor.getValue();
        assertEquals(1, messages.size());
        assertEquals("assistant", messages.get(0).getRole());
        assertEquals("hello", messages.get(0).getMessage());
        assertNotNull(messages.get(0).getTimestamp());
    }

    @Test
    // Throws ChatServiceException when saving messages to Supabase fails
    void saveMessages_ShouldThrowChatServiceException_WhenSupabaseFails() {
        doThrow(new RuntimeException("Supabase failure"))
            .when(supabaseRestClient)
            .postList(anyString(), any(), anyString(), ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any());

        ChatServiceException ex = assertThrows(
            ChatServiceException.class,
            () -> service.saveMessages("token", "hello", "user")
        );

        assertEquals("Failed to save chat messages", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    // Returns chat message history for a given user
    void getMessageHistory_ShouldReturnRecordsFromSupabase_WhenUserIdProvided() {
        List<ChatbotMessageDTO> expected = List.of(new ChatbotMessageDTO("user", "hi", null));
        when(supabaseRestClient.getList(
            eq("user_chatbot_history"),
            eq("user_id=eq.user-1&order=timestamp.asc"),
            eq("token"),
            ArgumentMatchers.<TypeReference<List<ChatbotMessageDTO>>>any()
        )).thenReturn(expected);

        List<ChatbotMessageDTO> result = service.getMessageHistory("token", "user-1");

        assertEquals(expected, result);
    }

    @Test
    // Throws ChatServiceException when fetching message history fails
    void getMessageHistory_ShouldThrowChatServiceException_WhenSupabaseFails() {
        when(supabaseRestClient.getList(anyString(), anyString(), anyString(), ArgumentMatchers.<TypeReference<List<ChatbotMessageDTO>>>any()))
            .thenThrow(new RuntimeException("Supabase failure"));

        ChatServiceException ex = assertThrows(
            ChatServiceException.class,
            () -> service.getMessageHistory("token", "user-1")
        );

        assertEquals("Failed to fetch chat history", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    // Deletes message history for a specific user
    void deleteMessageHistory_ShouldCallDeleteWithUserFilter_WhenUserIdProvided() {
        service.deleteMessageHistory("token", "user-1");

        verify(supabaseRestClient).deleteList(
            eq("user_chatbot_history"),
            eq("user_id=eq.user-1"),
            eq("token"),
            ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any()
        );
    }

    @Test
    // Throws ChatServiceException when deleting message history fails
    void deleteMessageHistory_ShouldThrowChatServiceException_WhenSupabaseFails() {
        doThrow(new RuntimeException("Supabase failure"))
            .when(supabaseRestClient)
            .deleteList(anyString(), anyString(), anyString(), ArgumentMatchers.<TypeReference<List<Map<String, Object>>>>any());

        ChatServiceException ex = assertThrows(
            ChatServiceException.class,
            () -> service.deleteMessageHistory("token", "user-1")
        );

        assertEquals("Failed to delete chat history", ex.getMessage());
        assertNotNull(ex.getCause());
    }
}
