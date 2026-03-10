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

        System.out.println(question);
        String context = lessonService.findRelevantLesson(question);
        // System.out.println(context);

        String prompt = """
            You are a learning assistant.

            Answer the question ONLY using the provided context.
            If the answer is not in the context, reply with "I don't know".
            Always be positive and supportive.

            Context:
            %s

            Question:
            %s
            """.formatted(context, question);

    return null;
    
    // openAiChatModel.call(new Prompt(new UserMessage(prompt)))
    //                 .getResult()
    //                 .getOutput()
    //                 // to check if the below is correct
    //                 .getText();
    }
}