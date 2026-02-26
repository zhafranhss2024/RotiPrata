package com.rotiprata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "media")
public class MediaProcessingProperties {
    private int maxUploadMb = 200;
    private int maxDurationSeconds = 180;
    private String tempDir;
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    private String ytdlpPath = "yt-dlp";
    private boolean autoUpdateYtdlp = true;
    private boolean checkToolingOnStartup = true;
    private boolean ytdlpVerbose = false;

    public int getMaxUploadMb() {
        return maxUploadMb;
    }

    public void setMaxUploadMb(int maxUploadMb) {
        this.maxUploadMb = maxUploadMb;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public void setMaxDurationSeconds(int maxDurationSeconds) {
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getFfprobePath() {
        return ffprobePath;
    }

    public void setFfprobePath(String ffprobePath) {
        this.ffprobePath = ffprobePath;
    }

    public String getYtdlpPath() {
        return ytdlpPath;
    }

    public void setYtdlpPath(String ytdlpPath) {
        this.ytdlpPath = ytdlpPath;
    }

    public boolean isAutoUpdateYtdlp() {
        return autoUpdateYtdlp;
    }

    public void setAutoUpdateYtdlp(boolean autoUpdateYtdlp) {
        this.autoUpdateYtdlp = autoUpdateYtdlp;
    }

    public boolean isCheckToolingOnStartup() {
        return checkToolingOnStartup;
    }

    public void setCheckToolingOnStartup(boolean checkToolingOnStartup) {
        this.checkToolingOnStartup = checkToolingOnStartup;
    }

    public boolean isYtdlpVerbose() {
        return ytdlpVerbose;
    }

    public void setYtdlpVerbose(boolean ytdlpVerbose) {
        this.ytdlpVerbose = ytdlpVerbose;
    }
}
