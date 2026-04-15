package com.rotiprata.media.service;

import com.rotiprata.config.MediaProcessingProperties;
import com.rotiprata.config.SupabaseProperties;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseStorageClient;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers media processing service scenarios and regression behavior for the current branch changes.
 */
@ExtendWith(MockitoExtension.class)
class MediaProcessingServiceImplTest {

    @Mock
    private SupabaseAdminRestClient adminRestClient;

    @Mock
    private SupabaseStorageClient storageClient;

    private MediaProcessingProperties properties;
    private MediaProcessingServiceImpl service;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        properties = new MediaProcessingProperties();
        properties.setAutoUpdateYtdlp(false);
        properties.setYtdlpPath("yt-dlp");
        TaskExecutor executor = Runnable::run;
        service = new MediaProcessingServiceImpl(
            properties,
            new SupabaseProperties(),
            adminRestClient,
            storageClient,
            executor
        );
    }

    /**
     * Verifies that build yt dlp download command should omit ffmpeg location when using bare executable name.
     */
    @Test
    void buildYtDlpDownloadCommand_ShouldOmitFfmpegLocation_WhenUsingBareExecutableName() {
        properties.setFfmpegPath("ffmpeg");

        // act
        List<String> command = service.buildYtDlpDownloadCommand("https://example.com/video", Path.of("download.mp4"));

        // assert
        assertFalse(command.contains("--ffmpeg-location"));
        assertEquals("yt-dlp", command.get(0));
    }

    /**
     * Verifies that build yt dlp download command should include ffmpeg location when using absolute linux path.
     */
    @Test
    void buildYtDlpDownloadCommand_ShouldIncludeFfmpegLocation_WhenUsingAbsoluteLinuxPath() {
        properties.setFfmpegPath("/usr/local/bin/ffmpeg");

        // act
        List<String> command = service.buildYtDlpDownloadCommand("https://example.com/video", Path.of("download.mp4"));

        // assert
        int locationIndex = command.indexOf("--ffmpeg-location");
        assertTrue(locationIndex >= 0);
        assertEquals("/usr/local/bin", command.get(locationIndex + 1));
    }

    /**
     * Verifies that build yt dlp download command should include ffmpeg location when using windows style path.
     */
    @Test
    void buildYtDlpDownloadCommand_ShouldIncludeFfmpegLocation_WhenUsingWindowsStylePath() {
        properties.setFfmpegPath("C:\\tools\\ffmpeg.exe");

        // act
        List<String> command = service.buildYtDlpDownloadCommand("https://example.com/video", Path.of("download.mp4"));

        // assert
        int locationIndex = command.indexOf("--ffmpeg-location");
        assertTrue(locationIndex >= 0);
        assertEquals("C:\\tools", command.get(locationIndex + 1));
    }
}
