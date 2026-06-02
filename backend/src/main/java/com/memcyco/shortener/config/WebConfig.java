package com.memcyco.shortener.config;

import com.memcyco.shortener.common.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // Rate-limit the redirect path only. The interceptor short-circuits when
        // the feature flag is off, so it's cheap to leave wired up.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/{shortCode:[a-zA-Z0-9_-]{1,32}}");
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        // Open CORS for the SPA — dev convenience; harden in production.
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
