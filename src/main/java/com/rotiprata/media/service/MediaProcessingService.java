package com.rotiprata.media.service;

import com.rotiprata.api.content.domain.ContentType;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/**
 * Defines the media processing service capabilities used by application services.
 */
public interface MediaProcessingService {

    /**
     * Handles process upload.
     */
    void processUpload(UUID contentId, ContentType contentType, MultipartFile file);

    /**
     * Handles process link.
     */
    void processLink(UUID contentId, String sourceUrl);

    /**
     * Handles process lesson upload.
     */
    void processLessonUpload(UUID assetId, String mediaKind, MultipartFile file);

    /**
     * Handles process lesson link.
     */
    void processLessonLink(UUID assetId, String mediaKind, String sourceUrl);
}
