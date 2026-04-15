package com.rotiprata.api.content.service;

import com.rotiprata.api.browsing.dto.ContentSearchDTO;
import com.rotiprata.api.content.dto.ContentCommentCreateRequest;
import com.rotiprata.api.content.dto.ContentCommentResponse;
import com.rotiprata.api.content.dto.ContentFlagRequest;
import com.rotiprata.api.content.dto.ContentPlaybackEventRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Defines the content service operations exposed to the API layer.
 */
public interface ContentService {

    /**
     * Returns the content by id.
     */
    Map<String, Object> getContentById(UUID userId, UUID contentId, String accessToken);

    /**
     * Returns the similar content.
     */
    List<Map<String, Object>> getSimilarContent(UUID userId, UUID contentId, String accessToken, Integer limit);

    /**
     * Returns the profile content collection.
     */
    List<Map<String, Object>> getProfileContentCollection(UUID userId, String accessToken, String collection);

    /**
     * Returns the filtered content.
     */
    List<ContentSearchDTO> getFilteredContent(String query, String filter, String accessToken);

    /**
     * Tracks the view.
     */
    void trackView(UUID userId, UUID contentId);

    /**
     * Records the playback event.
     */
    void recordPlaybackEvent(UUID userId, UUID contentId, ContentPlaybackEventRequest request);

    /**
     * Likes the content.
     */
    void likeContent(UUID userId, UUID contentId, String accessToken);

    /**
     * Removes the like from the content.
     */
    void unlikeContent(UUID userId, UUID contentId, String accessToken);

    /**
     * Saves the content.
     */
    void saveContent(UUID userId, UUID contentId, String accessToken);

    /**
     * Removes the saved marker from the content.
     */
    void unsaveContent(UUID userId, UUID contentId, String accessToken);

    /**
     * Records a share for the content.
     */
    void shareContent(UUID userId, UUID contentId, String accessToken);

    /**
     * Flags the content.
     */
    void flagContent(UUID userId, UUID contentId, ContentFlagRequest request, String accessToken);

    /**
     * Lists the comments.
     */
    List<ContentCommentResponse> listComments(
        UUID userId,
        UUID contentId,
        int limit,
        int offset,
        String accessToken
    );

    /**
     * Creates the comment.
     */
    ContentCommentResponse createComment(
        UUID userId,
        UUID contentId,
        ContentCommentCreateRequest request,
        String accessToken
    );

    /**
     * Deletes the comment.
     */
    void deleteComment(UUID userId, UUID contentId, UUID commentId, String accessToken);

    /**
     * Returns the flagged content by month and year.
     */
    List<Map<String, Object>> getFlaggedContentByMonthAndYear(String accessToken, String month, String year);
}
