package com.rotiprata.api.content.service;

import com.rotiprata.api.admin.dto.AdminContentQuizRequest;
import com.rotiprata.api.content.dto.ContentQuizQuestionResponse;
import com.rotiprata.api.content.dto.ContentQuizResponse;
import com.rotiprata.api.content.dto.ContentQuizSubmitRequest;
import com.rotiprata.api.content.dto.ContentQuizSubmitResponse;
import java.util.List;
import java.util.UUID;

/**
 * Defines the content quiz service operations exposed to the API layer.
 */
public interface ContentQuizService {

    /**
     * Returns the quiz for an approved content item.
     */
    ContentQuizResponse getContentQuiz(UUID userId, UUID contentId, String accessToken);

    /**
     * Scores and records a quiz submission.
     */
    ContentQuizSubmitResponse submitContentQuiz(
        UUID userId,
        UUID contentId,
        ContentQuizSubmitRequest request,
        String accessToken
    );

    /**
     * Returns the admin quiz definition for a content item.
     */
    List<ContentQuizQuestionResponse> getAdminContentQuiz(
        UUID adminUserId,
        UUID contentId,
        String accessToken
    );

    /**
     * Replaces the admin quiz definition for a content item.
     */
    List<ContentQuizQuestionResponse> replaceAdminContentQuiz(
        UUID adminUserId,
        UUID contentId,
        AdminContentQuizRequest request,
        String accessToken
    );
}
