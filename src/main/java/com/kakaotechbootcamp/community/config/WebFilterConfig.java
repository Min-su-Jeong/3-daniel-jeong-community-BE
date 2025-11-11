package com.kakaotechbootcamp.community.config;

import com.kakaotechbootcamp.community.filter.SessionAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 웹 필터 및 리스너 설정
 * - 세션 인증 필터 등록 및 순서 설정
 * - 세션 유지보수 리스너 등록
 */
@Configuration
@RequiredArgsConstructor
public class WebFilterConfig {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final SessionMaintenanceConfig sessionMaintenanceConfig;

    /**
     * 세션 인증 필터 등록
     * - 모든 요청에 대해 세션 인증 검증
     */
    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionFilterRegistration() {
        FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(sessionAuthenticationFilter);
        registration.addUrlPatterns("/*");
        registration.setOrder(1); // 필터 실행 순서 (낮을수록 먼저 실행)
        registration.setName("sessionAuthenticationFilter");
        return registration;
    }

    /**
     * 세션 유지보수 리스너 등록
     * - 세션 생성/소멸 추적 및 만료 세션 정리
     */
    @Bean
    public ServletListenerRegistrationBean<SessionMaintenanceConfig> sessionListenerRegistration() {
        ServletListenerRegistrationBean<SessionMaintenanceConfig> registration = 
                new ServletListenerRegistrationBean<>(sessionMaintenanceConfig);
        return registration;
    }
}

