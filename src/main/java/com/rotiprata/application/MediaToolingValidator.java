package com.rotiprata.application;

import com.rotiprata.config.MediaProcessingProperties;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MediaToolingValidator {
    private static final Logger log = LoggerFactory.getLogger(MediaToolingValidator.class);

    private final MediaProcessingProperties properties;

    public MediaToolingValidator(MediaProcessingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validateTooling() {
        if (!properties.isCheckToolingOnStartup()) {
            log.info("Media tooling checks are disabled.");
            return;
        }

        assertExecutable(properties.getFfmpegPath(), List.of("-version"), "ffmpeg");
        assertExecutable(properties.getFfprobePath(), List.of("-version"), "ffprobe");
        assertExecutable(properties.getYtdlpPath(), List.of("--version"), "yt-dlp");
    }

    private void assertExecutable(String binary, List<String> args, String name) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(buildCommand(binary, args));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                failStartup(name + " failed to run. Output: " + output.toString().trim());
                return;
            }
            log.info("{} available: {}", name, firstLine(output.toString()));
        } catch (Exception ex) {
            failStartup(
                "Missing or invalid " + name + " executable. Set " + envHint(name)
                    + " or install the binary. Root error: " + ex.getMessage()
            );
        }
    }

    private List<String> buildCommand(String binary, List<String> args) {
        List<String> command = new java.util.ArrayList<>();
        command.add(binary);
        command.addAll(args);
        return command;
    }

    private String envHint(String name) {
        return switch (name) {
            case "ffmpeg" -> "FFMPEG_PATH";
            case "ffprobe" -> "FFPROBE_PATH";
            case "yt-dlp" -> "YTDLP_PATH";
            default -> "PATH";
        };
    }

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return "unknown";
        }
        int idx = output.indexOf('\n');
        return idx >= 0 ? output.substring(0, idx).trim() : output.trim();
    }

    private void failStartup(String message) {
        String script = installScriptHint();
        String full = message + " Run " + script + " before starting the server. "
            + "To bypass this check temporarily, set MEDIA_CHECK_TOOLING_ON_STARTUP=false.";
        throw new IllegalStateException(full);
    }

    private String installScriptHint() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "scripts\\install-media-tools.ps1";
        }
        return "./scripts/install-media-tools.sh";
    }
}
