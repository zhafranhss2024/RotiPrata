package com.rotiprata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {
    private String url;
    private String anonKey;
    private String restUrl;
    private String serviceRoleKey;
    private Storage storage = new Storage();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAnonKey() {
        return anonKey;
    }

    public void setAnonKey(String anonKey) {
        this.anonKey = anonKey;
    }

    public String getRestUrl() {
        if (restUrl != null && !restUrl.isBlank()) {
            return restUrl;
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return base + "/rest/v1";
    }

    public void setRestUrl(String restUrl) {
        this.restUrl = restUrl;
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = serviceRoleKey;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public static class Storage {
        private String contentMedia;
        private String avatars;
        private String lessonMedia;
        private String badges;

        public String getContentMedia() {
            return contentMedia;
        }

        public void setContentMedia(String contentMedia) {
            this.contentMedia = contentMedia;
        }

        public String getAvatars() {
            return avatars;
        }

        public void setAvatars(String avatars) {
            this.avatars = avatars;
        }

        public String getLessonMedia() {
            return lessonMedia;
        }

        public void setLessonMedia(String lessonMedia) {
            this.lessonMedia = lessonMedia;
        }

        public String getBadges() {
            return badges;
        }

        public void setBadges(String badges) {
            this.badges = badges;
        }
    }
}
