package com.ncp03.coursechange.common.config;

import com.ncp03.coursechange.common.auth.AuthProperties;
import com.ncp03.coursechange.common.auth.TokenService;
import com.ncp03.coursechange.common.web.CurrentStudentIdArgumentResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 프론트 주소. application.yml의 app.cors.allowed-origins 또는 환경변수 CORS_ALLOWED_ORIGINS로 바꾼다. */
    private final List<String> allowedOrigins;

    private final TokenService tokenService;
    private final AuthProperties authProperties;

    public WebConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins,
                     TokenService tokenService, AuthProperties authProperties) {
        this.allowedOrigins = allowedOrigins;
        this.tokenService = tokenService;
        this.authProperties = authProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentStudentIdArgumentResolver(tokenService, authProperties));
    }
}
