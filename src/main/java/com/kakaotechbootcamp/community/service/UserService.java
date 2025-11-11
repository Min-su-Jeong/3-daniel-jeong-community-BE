package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.common.ImageType;
import com.kakaotechbootcamp.community.dto.user.UserCreateRequestDto;
import com.kakaotechbootcamp.community.dto.user.UserResponseDto;
import com.kakaotechbootcamp.community.dto.user.UserUpdateRequestDto;
import com.kakaotechbootcamp.community.entity.User;
import com.kakaotechbootcamp.community.exception.*;
import com.kakaotechbootcamp.community.repository.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * 사용자(User) 도메인 서비스
 * - 회원가입, 조회, 수정, 탈퇴 비즈니스 로직
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ImageUploadService imageUploadService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입
     * - 의도: 이메일/닉네임 중복 검사 후 사용자 생성
     * - 에러: 중복 시 409(Conflict)
     */
    @Transactional
    public ApiResponse<UserResponseDto> create(UserCreateRequestDto request) {
        String email = request.getEmail().trim().toLowerCase();
        String nickname = request.getNickname().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(Constants.ErrorMessage.EMAIL_ALREADY_IN_USE);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new ConflictException(Constants.ErrorMessage.NICKNAME_ALREADY_IN_USE);
        }
        // 비밀번호 BCrypt 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = new User(email, encodedPassword, nickname);
        User saved = userRepository.save(user);
        return ApiResponse.created(UserResponseDto.from(saved));
    }

    /**
     * 회원 정보 조회
     * - 의도: id로 활성 사용자 조회
     * - 에러: 없으면 404(NotFound)
     */
    @Transactional(readOnly = true)
    public ApiResponse<UserResponseDto> getById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND));
        return ApiResponse.modified(UserResponseDto.from(user));
    }

    /**
     * 회원 정보 수정
     * - 의도: 닉네임/프로필 부분 수정(선택 입력)
     * - 로직: 닉네임 동일 시 중복검사 생략, 프로필 빈문자→null
     * - 에러: 닉네임 중복 시 409(Conflict)
     */
    @Transactional
    public ApiResponse<UserResponseDto> update(Integer id, UserUpdateRequestDto request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND));

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            String newNickname = request.getNickname().trim();
            if (!newNickname.equals(user.getNickname()) && userRepository.existsByNicknameAndIdNot(newNickname, id)) {
                throw new ConflictException(Constants.ErrorMessage.NICKNAME_ALREADY_IN_USE);
            }
            user.updateNickname(newNickname);
        }
        if (request.getProfileImageKey() != null) {
            String newProfileKey = request.getProfileImageKey().trim();
            String finalProfileKey = newProfileKey.isEmpty() ? null : newProfileKey;
            
            // 프로필 이미지 objectKey 검증
            if (finalProfileKey != null) {
                imageUploadService.validateObjectKey(ImageType.PROFILE, finalProfileKey, id);
            }
            
            user.updateProfileImageKey(finalProfileKey);
        }
        return ApiResponse.modified(UserResponseDto.from(user));
    }

    /**
     * 회원 탈퇴(소프트 삭제)
     * - 의도: deletedAt 설정로 비활성화
     */
    @Transactional
    public ApiResponse<Void> delete(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND));
        user.softDelete();
        return ApiResponse.deleted(null);
    }

    /**
     * 이메일 사용 가능 여부
     * - 반환: true=사용 가능, false=중복
     */
    @Transactional(readOnly = true)
    public ApiResponse<Boolean> isEmailAvailable(String email) {
        boolean exists = userRepository.existsByEmail(email.trim().toLowerCase());
        return ApiResponse.modified(!exists);
    }

    /**
     * 닉네임 사용 가능 여부
     * - 반환: true=사용 가능, false=중복
     */
    @Transactional(readOnly = true)
    public ApiResponse<Boolean> isNicknameAvailable(String nickname) {
        boolean exists = userRepository.existsByNickname(nickname.trim());
        return ApiResponse.modified(!exists);
    }

    /**
     * 비밀번호 변경
     * - 의도: 현재/신규 비밀번호 검증 후 업데이트
     * - 에러: 공백/동일/현재 불일치 시 400(BadRequest)
     */
    @Transactional
    public ApiResponse<Void> updatePassword(Integer id, String currentPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND));

        String cur = currentPassword == null ? "" : currentPassword.trim();
        String next = newPassword == null ? "" : newPassword.trim();
        if (cur.isEmpty() || next.isEmpty()) {
            throw new BadRequestException(Constants.ErrorMessage.PASSWORD_ALL_REQUIRED);
        }
        if (cur.equals(next)) {
            throw new BadRequestException(Constants.ErrorMessage.PASSWORD_SAME_AS_PREVIOUS);
        }
        // 현재 비밀번호 BCrypt 비교
        if (!passwordEncoder.matches(cur, user.getPassword())) {
            throw new BadRequestException(Constants.ErrorMessage.PASSWORD_MISMATCH);
        }
        // 새 비밀번호 BCrypt 암호화
        String encodedNewPassword = passwordEncoder.encode(next);
        user.updatePassword(encodedNewPassword);
        return ApiResponse.modified(null);
    }
}
