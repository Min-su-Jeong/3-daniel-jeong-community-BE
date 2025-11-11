package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.dto.user.UserLoginRequestDto;
import com.kakaotechbootcamp.community.dto.user.UserLoginResponseDto;
import com.kakaotechbootcamp.community.entity.User;
import com.kakaotechbootcamp.community.exception.BadRequestException;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 기반 인증 서비스
 * - 로그인, 로그아웃, 세션 검증 로직
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SessionService {

    // 세션에 저장할 사용자 정보 키 이름
    private static final String SESSION_USER_ID = Constants.SessionKey.USER_ID;
    private static final String SESSION_USER_EMAIL = Constants.SessionKey.USER_EMAIL;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 로그인
     * - 의도: 이메일/비밀번호 검증 후 세션 생성
     * - 에러: 사용자 없음 404, 비밀번호 불일치 400
     */
    @Transactional
    public ApiResponse<UserLoginResponseDto> login(UserLoginRequestDto request, HttpSession session) {
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.INVALID_EMAIL_OR_PASSWORD));

        // 비밀번호 BCrypt 검증
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException(Constants.ErrorMessage.INVALID_EMAIL_OR_PASSWORD);
        }

        // 세션에 사용자 정보 저장
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setAttribute(SESSION_USER_EMAIL, user.getEmail());

        return ApiResponse.modified(UserLoginResponseDto.from(user));
    }

    /**
     * 로그아웃
     * - 의도: 세션 무효화
     */
    public void logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * 현재 로그인한 사용자 ID 조회
     * - 의도: 세션에서 사용자 ID 추출
     * - 반환: 로그인하지 않았으면 null
     */
    public Integer getCurrentUserId(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (Integer) session.getAttribute(SESSION_USER_ID);
    }

    /**
     * 현재 로그인한 사용자 이메일 조회
     * - 의도: 세션에서 사용자 이메일 추출 (DB 조회 없음)
     * - 반환: 로그인하지 않았으면 null
     */
    public String getCurrentUserEmail(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (String) session.getAttribute(SESSION_USER_EMAIL);
    }

    /**
     * 현재 로그인한 사용자 조회
     * - 의도: 세션에서 사용자 정보 조회
     * - 에러: 세션 없음 또는 사용자 없음 404
     */
    @Transactional(readOnly = true)
    public User getCurrentUser(HttpSession session) {
        Integer userId = getCurrentUserId(session);
        if (userId == null) {
            throw new NotFoundException(Constants.ErrorMessage.LOGIN_REQUIRED);
        }
        // @SQLRestriction으로 인해 삭제된 사용자는 조회되지 않음
        User user = userRepository.findById(userId)
                .orElse(null);
        
        // 사용자가 없거나 탈퇴한 경우 세션 무효화 후 예외 발생
        if (user == null) {
            if (session != null) {
                session.invalidate();
            }
            throw new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND);
        }
        
        return user;
    }

    /**
     * 세션 검증
     * - 의도: 세션이 유효한지 확인
     * - 반환: 유효한 세션이면 true
     */
    public boolean isAuthenticated(HttpSession session) {
        if (session == null) {
            return false;
        }
        Integer userId = (Integer) session.getAttribute(SESSION_USER_ID);
        return userId != null;
    }
}
