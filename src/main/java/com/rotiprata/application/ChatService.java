package com.rotiprata.application;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

@Service
public class ChatService {

    private final OpenAiChatModel openAiChatModel;
    private final LessonService lessonService;

    public ChatService(OpenAiChatModel openAiChatModel, LessonService lessonService) {
        this.openAiChatModel = openAiChatModel;
        this.lessonService = lessonService;
    }

    public String ask(String question) {

        String context = lessonService.findRelevantLesson(question);

        String prompt = """
                Answer the question using the context.
                If the answer is not in the context, say "I don't know".

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

    return openAiChatModel.call(new Prompt(new UserMessage(prompt)))
                    .getResult()
                    .getOutput()
                    // to check if the below is correct
                    .getText();
    }
}