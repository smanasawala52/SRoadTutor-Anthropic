package com.sroadtutor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * CORS: which origins are allowed to call our API from the browser / webview.
 * Values come from {@link AppProperties} so dev/qa/prod can differ.
 */
@Configuration
public class CorsConfig {

    private final AppProperties appProperties;

    public CorsConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Use setAllowedOriginPatterns (not setAllowedOrigins) because we need
        // to support wildcards like http://localhost:* AND allowCredentials.
        config.setAllowedOriginPatterns(appProperties.cors().allowedOrigins());
        config.setAllowedMethods(Arrays.asList(appProperties.cors().allowedMethods().split(",")));
        config.setAllowedHeaders(Arrays.asList(appProperties.cors().allowedHeaders().split(",")));
        config.setAllowCredentials(appProperties.cors().allowCredentials());
        config.setMaxAge(appProperties.cors().maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
