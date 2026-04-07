package com.rotiprata.api.chat.service;

import com.rotiprata.api.exception.ModerationServiceException;

/**
 * Service interface for content moderation.
 * Determines whether a given text contains inappropriate content.
 */
public interface ModerationService {

    /**
     * Checks whether the provided text is flagged as inappropriate.
     *
     * @param text the text to evaluate
     * @return true if the text is flagged, false otherwise
     * @throws ModerationServiceException if the moderation check fails due to an internal or external error
     */
    boolean isFlagged(String text);
}