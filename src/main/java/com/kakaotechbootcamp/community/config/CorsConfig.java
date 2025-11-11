package com.kakaotechbootcamp.community.config;

import com.kakaotechbootcamp.community.common.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS 설정
 * - 프론트엔드와 백엔드 간 통신을 위한 CORS 설정
 * - Cookie 기반 인증을 사용하므로 credentials 허용
 * - 허용할 Origin은 application-secret.yml의 cors.allowed-origins에서 설정
 * - Spring 5.3+ 버전의 allowedOriginPatterns 사용으로 와일드카드 패턴 지원
 *   예) "https://*.app.com" 형태로 여러 서브도메인을 한 번에 관리 가능
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods(Constants.Cors.ALLOWED_METHODS)
                .allowedHeaders(Constants.Cors.ALLOWED_HEADERS)
                .exposedHeaders(Constants.Cors.EXPOSED_HEADER_SET_COOKIE)
                .allowCredentials(true)
                .maxAge(Constants.Cors.MAX_AGE);
    }
}
