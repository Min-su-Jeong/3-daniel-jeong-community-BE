package com.kakaotechbootcamp.community.config;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세션 유지보수 설정
 * - 세션 생성/소멸 추적
 * - 주기적으로 만료된 세션 정리하여 메모리 누수 방지
 */
@Component
public class SessionMaintenanceConfig implements HttpSessionListener {

    private static final Logger log = LoggerFactory.getLogger(SessionMaintenanceConfig.class);
    private static final long MILLISECONDS_PER_SECOND = 1000L;
    private static final int NO_TIMEOUT = 0;

    private final Set<HttpSession> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        activeSessions.add(event.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        activeSessions.remove(event.getSession());
    }

    /**
     * 만료된 세션 정리 스케줄러
     * - 주기: application.yml의 session.cleanup.fixed-delay-ms로 설정 (기본 5분)
     * - 동작: maxInactiveInterval을 초과한 세션을 무효화
     */
    @Scheduled(fixedDelayString = "${session.cleanup.fixed-delay-ms:300000}")
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;

        for (HttpSession session : activeSessions) {
            if (isExpired(session, currentTime)) {
                invalidateSession(session);
                cleanedCount++;
            }
        }

        logCleanupResult(cleanedCount);
    }

    private boolean isExpired(HttpSession session, long currentTime) {
        try {
            int maxInactiveSeconds = session.getMaxInactiveInterval();
            if (maxInactiveSeconds <= NO_TIMEOUT) {
                return false;
            }

            long lastAccessedTime = session.getLastAccessedTime();
            long idleTimeMillis = currentTime - lastAccessedTime;
            long maxInactiveMillis = maxInactiveSeconds * MILLISECONDS_PER_SECOND;

            return idleTimeMillis > maxInactiveMillis;
        } catch (IllegalStateException e) {
            // 세션이 이미 무효화된 경우
            activeSessions.remove(session);
            return false;
        }
    }

    private void invalidateSession(HttpSession session) {
        try {
            session.invalidate();
            activeSessions.remove(session);
        } catch (Exception e) {
            log.debug("Failed to invalidate session: {}", e.getMessage());
            activeSessions.remove(session);
        }
    }

    private void logCleanupResult(int cleanedCount) {
        if (cleanedCount > 0) {
            log.debug("Cleaned up {} expired sessions", cleanedCount);
        }
    }
}

