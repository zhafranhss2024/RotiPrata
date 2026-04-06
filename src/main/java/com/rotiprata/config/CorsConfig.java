package com.rotiprata.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = System.getenv("ALLOWED_ORIGINS");
        if (origins != null && !origins.isEmpty()) {
            String[] allowedOrigins = origins.split(",");
            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }
    }
}