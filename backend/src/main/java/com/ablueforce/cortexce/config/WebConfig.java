package com.ablueforce.cortexce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.*;

import java.time.Duration;

/**
 * Web MVC configuration.
 * <p>
 * Configures static resource serving (for Viewer UI), CORS, and SPA fallback.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${claudemem.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static resources from classpath:/static/
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/", "classpath:/public/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)));

        // Assets (fonts, icons, etc.)
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)));
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // SPA fallback: unmatched paths -> index.html
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // P0: Fixed CORS configuration - use allowed origins from config or deny
        // Never combine allowCredentials(true) with wildcard origins
        String[] origins = allowedOrigins != null && !allowedOrigins.isBlank()
            ? allowedOrigins.split(",")
            : new String[]{};

        // Determine if we should allow credentials (only if not using wildcard)
        boolean allowCredentials = origins.length > 0 && !origins[0].equals("*");

        // API endpoints CORS
        registry.addMapping("/api/**")
            .allowedOrigins(origins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(allowCredentials)
            .maxAge(3600);

        // SSE stream endpoint CORS
        registry.addMapping("/stream")
            .allowedOrigins(origins)
            .allowedMethods("GET")
            .allowedHeaders("*")
            .allowCredentials(allowCredentials)
            .maxAge(3600);
    }
}
