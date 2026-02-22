package com.rotiprata.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotiprata.config.MediaProcessingProperties;
import com.rotiprata.config.SupabaseProperties;
import com.rotiprata.domain.ContentType;
import com.rotiprata.infrastructure.supabase.SupabaseAdminRestClient;
import com.rotiprata.infrastructure.supabase.SupabaseStorageClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
public class MediaProcessingService {
    private static final Logger log = LoggerFactory.getLogger(MediaProcessingService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final MediaProcessingProperties properties;
    private final SupabaseProperties supabaseProperties;
    private final SupabaseAdminRestClient adminRestClient;
    private final SupabaseStorageClient storageClient;
    private final TaskExecutor mediaTaskExecutor;

    public MediaProcessingService(
        MediaProcessingProperties properties,
        SupabaseProperties supabaseProperties,
        SupabaseAdminRestClient adminRestClient,
        SupabaseStorageClient storageClient,
        @Qualifier("mediaTaskExecutor") TaskExecutor mediaTaskExecutor
    ) {
        this.properties = properties;
        this.supabaseProperties = supabaseProperties;
        this.adminRestClient = adminRestClient;
        this.storageClient = storageClient;
        this.mediaTaskExecutor = mediaTaskExecutor;
        maybeUpdateYtDlp();
    }

    public void processUpload(UUID contentId, ContentType contentType, MultipartFile file) {
        Path tempFile;
        try {
            validateUploadSize(file.getSize());
            tempFile = saveTempFile(file);
        } catch (Exception ex) {
            log.warn("Media processing failed for content {}: {}", contentId, ex.getMessage(), ex);
            markFailed(contentId, classifyError(ex.getMessage()));
            return;
        }

        mediaTaskExecutor.execute(() -> {
            try {
                if (contentType == ContentType.IMAGE) {
                    processImage(contentId, tempFile);
                } else {
                    processVideoToHls(contentId, tempFile, null);
                }
            } catch (Exception ex) {
                log.warn("Media processing failed for content {}: {}", contentId, ex.getMessage(), ex);
                markFailed(contentId, classifyError(ex.getMessage()));
            } finally {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ex) {
                    log.debug("Failed to delete temp file {}", tempFile, ex);
                }
            }
        });
    }

    public void processLink(UUID contentId, String sourceUrl) {
    mediaTaskExecutor.execute(() -> {
        try {
            long start = System.nanoTime();
            long stepStart = System.nanoTime();
            validateLink(sourceUrl);
            log.info("TIMING content {} validate link {}s", contentId, elapsedSeconds(stepStart));
            stepStart = System.nanoTime();
            YtDlpInfo info = fetchYtDlpInfo(sourceUrl);
            log.info("TIMING content {} yt-dlp info {}s", contentId, elapsedSeconds(stepStart));
            validateDurationSeconds(info.durationSeconds());

            Path tempFile = Files.createTempFile(resolveTempDir(), "link-", ".mp4");
            try {
               
                Files.deleteIfExists(tempFile);

                stepStart = System.nanoTime();
                downloadWithYtDlp(sourceUrl, tempFile);
                log.info("TIMING content {} yt-dlp download {}s", contentId, elapsedSeconds(stepStart));

           
                long size = Files.exists(tempFile) ? Files.size(tempFile) : 0L;
                if (size < 100_000) { // 100KB sanity threshold
                    throw new IOException("Downloaded file too small (" + size + " bytes) - likely blocked/HTML or skipped download");
                }

                stepStart = System.nanoTime();
                processVideoToHls(contentId, tempFile, sourceUrl);
                log.info("TIMING content {} hls pipeline {}s", contentId, elapsedSeconds(stepStart));
            } finally {
                Files.deleteIfExists(tempFile);
            }
            log.info("TIMING content {} link ingest total {}s", contentId, elapsedSeconds(start));
        } catch (Exception ex) {
            log.warn("Link processing failed for content {}: {}", contentId, ex.getMessage(), ex);
            markFailed(contentId, classifyError(ex.getMessage()));
        }
    });
}

    private void processVideoToHls(UUID contentId, Path input, String sourceUrl) throws IOException, InterruptedException {
        long start = System.nanoTime();
        long stepStart = System.nanoTime();
        MediaProbe probe = probeMedia(input);
        log.info("TIMING content {} ffprobe {}s", contentId, elapsedSeconds(stepStart));
        validateDurationSeconds(probe.durationSeconds());

        int hlsTimeSeconds = chooseSegmentDuration(probe.durationSeconds());
        log.info("TIMING content {} hls_time {}s", contentId, hlsTimeSeconds);

        Path outputDir = Files.createTempDirectory(resolveTempDir(), "hls-" + contentId + "-");
        try {
            stepStart = System.nanoTime();
            generateHlsVariants(input, outputDir, probe.hasAudio(), hlsTimeSeconds);
            log.info("TIMING content {} ffmpeg variants {}s", contentId, elapsedSeconds(stepStart));
            stepStart = System.nanoTime();
            Path posterPath = generatePoster(input, outputDir);
            log.info("TIMING content {} poster {}s", contentId, elapsedSeconds(stepStart));
            stepStart = System.nanoTime();
            uploadHlsOutputs(contentId, outputDir, posterPath);
            log.info("TIMING content {} upload {}s", contentId, elapsedSeconds(stepStart));
            stepStart = System.nanoTime();
            markReady(contentId, probe);
            log.info("TIMING content {} mark-ready {}s", contentId, elapsedSeconds(stepStart));
        } finally {
            deleteDirectory(outputDir);
        }
        log.info("TIMING content {} hls total {}s", contentId, elapsedSeconds(start));
    }

    private void processImage(UUID contentId, Path input) throws IOException {
        long start = System.nanoTime();
        String bucket = supabaseProperties.getStorage().getContentMedia();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Supabase storage bucket for content media is not configured");
        }
        String extension = guessSuffix(input.getFileName().toString());
        String objectPath = "images/" + contentId + "/original" + extension;
        String contentType = extension.equalsIgnoreCase(".png") ? "image/png" : "image/jpeg";
        long stepStart = System.nanoTime();
        storageClient.uploadObject(bucket, objectPath, Files.readAllBytes(input), contentType);
        log.info("TIMING content {} image upload {}s", contentId, elapsedSeconds(stepStart));
        String publicUrlBase = normalizeBaseUrl();
        String imageUrl = publicUrlBase + "/storage/v1/object/public/" + bucket + "/" + objectPath;
        stepStart = System.nanoTime();
        patchContentSafely(
            contentId,
            Map.of(
                "media_url", imageUrl,
                "thumbnail_url", imageUrl,
                "media_status", "ready",
                "updated_at", OffsetDateTime.now()
            )
        );
        log.info("TIMING content {} image patch content {}s", contentId, elapsedSeconds(stepStart));
        stepStart = System.nanoTime();
        adminRestClient.patchList(
            "content_media",
            "content_id=eq." + contentId,
            Map.of(
                "status", "ready",
                "hls_url", imageUrl,
                "thumbnail_url", imageUrl,
                "updated_at", OffsetDateTime.now()
            ),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
        );
        log.info("TIMING content {} image patch media {}s", contentId, elapsedSeconds(stepStart));
        log.info("TIMING content {} image total {}s", contentId, elapsedSeconds(start));
    }

    private void validateUploadSize(long sizeBytes) {
        long maxBytes = properties.getMaxUploadMb() * 1024L * 1024L;
        if (sizeBytes > maxBytes) {
            throw new IllegalArgumentException("UPLOAD_TOO_LARGE");
        }
    }

    private void validateDurationSeconds(int durationSeconds) {
        if (durationSeconds > properties.getMaxDurationSeconds()) {
            throw new IllegalArgumentException("DURATION_LIMIT");
        }
    }

    private Path saveTempFile(MultipartFile file) throws IOException {
        String suffix = guessSuffix(file.getOriginalFilename());
        Path tempFile = Files.createTempFile(resolveTempDir(), "upload-", suffix);
        file.transferTo(tempFile);
        return tempFile;
    }

    private String guessSuffix(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".bin";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }

    private Path resolveTempDir() throws IOException {
        if (properties.getTempDir() != null && !properties.getTempDir().isBlank()) {
            return Path.of(properties.getTempDir());
        }
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    private void generateHlsVariants(Path input, Path outputDir, boolean hasAudio, int hlsTimeSeconds) throws IOException, InterruptedException {
        List<Variant> variants = List.of(
            new Variant("1080", 1080, 4500),
            new Variant("720", 720, 2500)
        );

        for (Variant variant : variants) {
            Path variantDir = outputDir.resolve("v" + variant.label());
            Files.createDirectories(variantDir);
            List<String> command = new ArrayList<>();
            command.add(properties.getFfmpegPath());
            command.add("-y");
            command.add("-i");
            command.add(input.toString());
            command.add("-vf");
            command.add("scale=-2:" + variant.height());
            command.add("-c:v");
            command.add("h264");
            command.add("-preset");
            command.add("veryfast");
            command.add("-b:v");
            command.add(variant.bitrateKbps() + "k");
            command.add("-maxrate");
            command.add((int) (variant.bitrateKbps() * 1.07) + "k");
            command.add("-bufsize");
            command.add((variant.bitrateKbps() * 2) + "k");
            command.add("-g");
            command.add("48");
            command.add("-keyint_min");
            command.add("48");
            command.add("-sc_threshold");
            command.add("0");
            if (hasAudio) {
                command.add("-c:a");
                command.add("aac");
                command.add("-b:a");
                command.add("128k");
                command.add("-ar");
                command.add("48000");
            } else {
                command.add("-an");
            }
            command.add("-hls_time");
            command.add(String.valueOf(hlsTimeSeconds));
            command.add("-hls_playlist_type");
            command.add("vod");
            command.add("-hls_segment_filename");
            command.add(variantDir.resolve("seg_%03d.ts").toString());
            command.add(variantDir.resolve("index.m3u8").toString());

            runProcess(command, "ffmpeg");
        }

        writeMasterPlaylist(outputDir, variants);
    }

    private void writeMasterPlaylist(Path outputDir, List<Variant> variants) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("#EXTM3U");
        lines.add("#EXT-X-VERSION:3");
        for (Variant variant : variants) {
            int bandwidth = variant.bitrateKbps() * 1000;
            int width = switch (variant.height()) {
                case 1080 -> 1920;
                case 720 -> 1280;
                default -> 1280;
            };
            lines.add("#EXT-X-STREAM-INF:BANDWIDTH=" + bandwidth + ",RESOLUTION=" + width + "x" + variant.height());
            lines.add("v" + variant.label() + "/index.m3u8");
        }
        Files.write(outputDir.resolve("master.m3u8"), lines, StandardCharsets.UTF_8);
    }

    private Path generatePoster(Path input, Path outputDir) throws IOException, InterruptedException {
        Path poster = outputDir.resolve("poster.jpg");
        List<String> command = List.of(
            properties.getFfmpegPath(),
            "-y",
            "-i",
            input.toString(),
            "-ss",
            "00:00:01",
            "-vframes",
            "1",
            poster.toString()
        );
        runProcess(command, "ffmpeg-poster");
        return poster;
    }

    private MediaProbe probeMedia(Path input) throws IOException, InterruptedException {
        List<String> command = List.of(
            properties.getFfprobePath(),
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
            input.toString()
        );
        ProcessResult result = runProcess(command, "ffprobe");
        JsonNode root = OBJECT_MAPPER.readTree(result.output());
        JsonNode format = root.path("format");
        int durationSeconds = (int) Math.round(format.path("duration").asDouble(0));
        boolean hasAudio = false;
        int width = 0;
        int height = 0;
        for (JsonNode stream : root.path("streams")) {
            String codecType = stream.path("codec_type").asText();
            if ("video".equals(codecType) && width == 0) {
                width = stream.path("width").asInt(0);
                height = stream.path("height").asInt(0);
            }
            if ("audio".equals(codecType)) {
                hasAudio = true;
            }
        }
        return new MediaProbe(durationSeconds, width, height, hasAudio);
    }

    private void uploadHlsOutputs(UUID contentId, Path outputDir, Path posterPath) throws IOException {
        String bucket = supabaseProperties.getStorage().getContentMedia();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("Supabase storage bucket for content media is not configured");
        }
        Path master = outputDir.resolve("master.m3u8");
        storageClient.uploadObject(bucket, "hls/" + contentId + "/master.m3u8", Files.readAllBytes(master), "application/x-mpegURL");

        List<Path> files;
        try (java.util.stream.Stream<Path> stream = Files.walk(outputDir)) {
            files = stream
                .filter(path -> !Files.isDirectory(path))
                .filter(path -> {
                    String filename = outputDir.relativize(path).toString().replace("\\", "/");
                    return !"master.m3u8".equals(filename) && !"poster.jpg".equals(filename);
                })
                .toList();
        }

        int concurrency = 8;
        log.info("TIMING content {} upload files {} with concurrency {}", contentId, files.size(), concurrency);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
        List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
        for (Path path : files) {
            futures.add(executor.submit(() -> {
                String filename = outputDir.relativize(path).toString().replace("\\", "/");
                String targetPath = "hls/" + contentId + "/" + filename;
                String contentType = filename.endsWith(".m3u8") ? "application/x-mpegURL"
                    : filename.endsWith(".ts") ? "video/MP2T"
                    : filename.endsWith(".jpg") ? "image/jpeg"
                    : "application/octet-stream";
                storageClient.uploadObject(bucket, targetPath, Files.readAllBytes(path), contentType);
                return null;
            }));
        }
        executor.shutdown();
        for (java.util.concurrent.Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Upload interrupted", ex);
            } catch (java.util.concurrent.ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                throw new IOException("Upload failed", cause);
            }
        }

        if (posterPath != null && Files.exists(posterPath)) {
            storageClient.uploadObject(
                bucket,
                "thumbs/" + contentId + "/poster.jpg",
                Files.readAllBytes(posterPath),
                "image/jpeg"
            );
        }
    }

    private int chooseSegmentDuration(int durationSeconds) {
        if (durationSeconds <= 0) {
            return 4;
        }
        if (durationSeconds < 60) {
            return 4;
        }
        if (durationSeconds < 300) {
            return 6;
        }
        if (durationSeconds < 900) {
            return 10;
        }
        return 15;
    }

    private void markReady(UUID contentId, MediaProbe probe) {
        String publicUrlBase = normalizeBaseUrl();
        String bucket = supabaseProperties.getStorage().getContentMedia();
        String hlsUrl = publicUrlBase + "/storage/v1/object/public/" + bucket + "/hls/" + contentId + "/master.m3u8";
        String posterUrl = publicUrlBase + "/storage/v1/object/public/" + bucket + "/thumbs/" + contentId + "/poster.jpg";

        patchContentSafely(
            contentId,
            Map.of(
                "media_url", hlsUrl,
                "thumbnail_url", posterUrl,
                "media_status", "ready",
                "updated_at", OffsetDateTime.now()
            )
        );
        adminRestClient.patchList(
            "content_media",
            "content_id=eq." + contentId,
            Map.of(
                "status", "ready",
                "hls_url", hlsUrl,
                "thumbnail_url", posterUrl,
                "duration_ms", probe.durationSeconds() * 1000,
                "width", probe.width(),
                "height", probe.height(),
                "updated_at", OffsetDateTime.now()
            ),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
        );
    }

    private void markFailed(UUID contentId, String errorCode) {
        patchContentSafely(
            contentId,
            Map.of(
                "media_status", "failed",
                "updated_at", OffsetDateTime.now()
            )
        );
        adminRestClient.patchList(
            "content_media",
            "content_id=eq." + contentId,
            Map.of(
                "status", "failed",
                "error_message", errorCode,
                "updated_at", OffsetDateTime.now()
            ),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
        );
    }

    private void patchContentSafely(UUID contentId, Map<String, Object> patch) {
        try {
            adminRestClient.patchList(
                "content",
                "id=eq." + contentId,
                patch,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
            );
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (!shouldRetryWithoutColumn(ex, "media_status")) {
                throw ex;
            }
            Map<String, Object> fallback = new java.util.HashMap<>(patch);
            fallback.remove("media_status");
            if (fallback.isEmpty()) {
                return;
            }
            adminRestClient.patchList(
                "content",
                "id=eq." + contentId,
                fallback,
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
            );
        }
    }

    private boolean shouldRetryWithoutColumn(org.springframework.web.server.ResponseStatusException ex, String column) {
        String reason = ex.getReason();
        String message = ex.getMessage();
        String needle = column == null ? "" : column.toLowerCase();
        if (reason != null) {
            String lower = reason.toLowerCase();
            if (lower.contains("pgrst204") || lower.contains(needle)) {
                return true;
            }
        }
        if (message != null) {
            String lower = message.toLowerCase();
            return lower.contains("pgrst204") || lower.contains(needle);
        }
        return false;
    }

    private String normalizeBaseUrl() {
        String base = supabaseProperties.getUrl();
        if (base == null) {
            return "";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private void validateLink(String sourceUrl) {
        String lower = sourceUrl.toLowerCase();
        boolean supported = lower.contains("tiktok.com")
            || lower.contains("instagram.com/reel")
            || lower.contains("instagram.com/reels");
        if (!supported) {
            throw new IllegalArgumentException("UNSUPPORTED_URL");
        }
    }

    private YtDlpInfo fetchYtDlpInfo(String url) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(properties.getYtdlpPath());
        if (properties.isYtdlpVerbose()) {
            command.add("-v");
        }
        command.add("--dump-json");
        command.add("--skip-download");
        command.add("--no-update");
        command.add("--no-warnings");
        command.add("--no-progress");
        command.add("--no-playlist");
        command.add(url);
        ProcessResult result = runProcess(command, "yt-dlp-info");
        JsonNode root = parseJsonFromYtDlpOutput(result.output());
        int duration = root.path("duration").asInt(0);
        return new YtDlpInfo(duration);
    }

    private void downloadWithYtDlp(String url, Path output) throws IOException, InterruptedException {
    List<String> command = List.of(
        properties.getYtdlpPath(),
        "--no-playlist",
        //"--impersonate", "chrome:windows-10",
        "--ffmpeg-location", Path.of(properties.getFfmpegPath()).getParent().toString(), // or exact dir containing ffmpeg
        "--merge-output-format", "mp4",
        "-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b",
        "-o", output.toString(),
        url
    );
    runProcess(command, "yt-dlp-download");
}

    private void maybeUpdateYtDlp() {
        if (!properties.isAutoUpdateYtdlp()) {
            return;
        }
        try {
            List<String> command = List.of(properties.getYtdlpPath(), "-U");
            runProcess(command, "yt-dlp-update");
        } catch (Exception ex) {
            log.warn("yt-dlp update failed: {}", ex.getMessage());
        }
    }

    private ProcessResult runProcess(List<String> command, String label) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IOException(label + " failed: " + output.toString().trim());
        }
        return new ProcessResult(exit, output.toString());
    }

    private JsonNode parseJsonFromYtDlpOutput(String output) throws IOException {
        if (output == null) {
            throw new IOException("yt-dlp returned empty output");
        }
        try {
            return OBJECT_MAPPER.readTree(output);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            String trimmed = output.trim();
            int first = trimmed.indexOf('{');
            int last = trimmed.lastIndexOf('}');
            if (first >= 0 && last > first) {
                String candidate = trimmed.substring(first, last + 1);
                try {
                    return OBJECT_MAPPER.readTree(candidate);
                } catch (com.fasterxml.jackson.core.JsonProcessingException ex2) {
                    log.warn("Failed to parse yt-dlp JSON output. Snippet: {}", safeSnippet(trimmed));
                    throw ex2;
                }
            }
            log.warn("yt-dlp output did not contain JSON. Snippet: {}", safeSnippet(trimmed));
            throw ex;
        }
    }

    private String safeSnippet(String output) {
        if (output == null) {
            return "";
        }
        String normalized = output.replaceAll("[\\r\\n\\t]+", " ").trim();
        int limit = 800;
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    private String classifyError(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        String lower = message.toLowerCase();
        if (lower.contains("instagram") && lower.contains("403")) {
            return "INSTAGRAM_BLOCKED";
        }
        if (lower.contains("unsupported_url") || lower.contains("unsupported")) {
            return "UNSUPPORTED_URL";
        }
        if (lower.contains("duration_limit")) {
            return "DURATION_LIMIT";
        }
        if (lower.contains("upload_too_large")) {
            return "UPLOAD_TOO_LARGE";
        }
        return "PROCESSING_FAILED";
    }

    private void deleteDirectory(Path dir) {
        try {
            if (dir == null || !Files.exists(dir)) {
                return;
            }
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        log.debug("Failed to delete {}", path, ex);
                    }
                });
        } catch (IOException ex) {
            log.debug("Failed to cleanup {}", dir, ex);
        }
    }

    private record Variant(String label, int height, int bitrateKbps) {}

    private record MediaProbe(int durationSeconds, int width, int height, boolean hasAudio) {}

    private record YtDlpInfo(int durationSeconds) {}

    private record ProcessResult(int exitCode, String output) {}

    private long elapsedSeconds(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000_000L);
    }
}
