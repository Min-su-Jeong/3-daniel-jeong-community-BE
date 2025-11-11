package com.kakaotechbootcamp.community.controller;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.dto.user.UserLoginRequestDto;
import com.kakaotechbootcamp.community.dto.user.UserLoginResponseDto;
import com.kakaotechbootcamp.community.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인증(Auth) API 컨트롤러
 * - 로그인, 로그아웃, 현재 인증 정보 조회
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionService sessionService;

    /**
     * 로그인
     * - 의도: 이메일/비밀번호 검증 후 세션 생성
     * - 보안: 기존 세션 무효화 후 새 세션 생성 (세션 탈취 방지)
     * - 반환: 200 OK (로그인 성공), 400 Bad Request (비밀번호 불일치), 404 Not Found (사용자 없음)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserLoginResponseDto>> login(
            @Valid @RequestBody UserLoginRequestDto request,
            HttpServletRequest httpRequest
    ) {
        ApiResponse<UserLoginResponseDto> apiResponse = sessionService.login(request, httpRequest);
        return ResponseEntity.status(apiResponse.getStatus()).body(apiResponse);
    }

    /**
     * 로그아웃
     * - 의도: 현재 세션 무효화
     * - 반환: 200 OK
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> logout(HttpSession session) {
        sessionService.logout(session);
        return ResponseEntity.ok(ApiResponse.modified(null));
    }

    /**
     * 현재 로그인한 사용자 조회
     * - 의도: 현재 세션의 사용자 정보 조회
     * - 반환: 200 OK (인증됨), 401 Unauthorized (인증 필요)
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserLoginResponseDto>> getCurrentUser(HttpSession session) {
        // 필터에서 이미 인증 검증이 완료된 상태이므로 바로 사용자 정보 반환
        var user = sessionService.getCurrentUser(session);
        UserLoginResponseDto responseDto = UserLoginResponseDto.from(user);
        return ResponseEntity.ok(ApiResponse.modified(responseDto));
    }
}
