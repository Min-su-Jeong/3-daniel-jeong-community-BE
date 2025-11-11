package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.common.ImageType;
import com.kakaotechbootcamp.community.dto.image.ImageUploadResponseDto;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.common.ImageProperties;
import com.kakaotechbootcamp.community.exception.BadRequestException;
import com.kakaotechbootcamp.community.repository.PostRepository;
import com.kakaotechbootcamp.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 이미지 업로드 공통 서비스
 * - 의도: 프로필/게시글 이미지 타입별 처리 및 검증 (경로 생성/검증)
 * - 기능:
 *   1. 이미지 업로드: 경로 생성/검증 및 응답 반환
 *   2. objectKey 검증: 외부 서비스에서 받은 objectKey 검증
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ImageProperties imageProperties;

    /**
     * 이미지 업로드 검증 및 경로 생성
     * - 의도: 타입별 리소스 존재 확인 후 objectKey 생성/검증
     * - 파라미터: imageType, objectKey(전체 경로 또는 null), filename(objectKey 없을 때), resourceId
     * - 반환: ImageUploadResponseDto(objectKey, url)
     * - 동작:
     *   1. 리소스 존재 검증 (userId/postId)
     *   2. objectKey 제공 시: 경로 규칙 검증
     *   3. filename 제공 시: 경로 자동 생성 (user/{userId}/profile/{filename} 또는 post/{postId}/images/{filename})
     *   4. 응답 반환 (objectKey, url)
     */
    @Transactional(readOnly = true)
    public ImageUploadResponseDto uploadImage(ImageType imageType, String objectKey, String filename, Integer resourceId) {
        // 리소스 존재 검증
        validateResourceExists(imageType, resourceId);

        // objectKey 생성 또는 검증
        String finalObjectKey;
        if (objectKey != null && !objectKey.isBlank()) {
            // objectKey가 제공된 경우: 경로/확장자 규칙 검증
            validateObjectKeyPath(imageType, objectKey, resourceId);
            validateExtension(objectKey);
            finalObjectKey = objectKey;
        } else {
            // objectKey가 없는 경우: filename으로 경로 자동 생성
            if (filename == null || filename.isBlank()) {
                throw new BadRequestException(Constants.ErrorMessage.OBJECT_KEY_OR_FILENAME_REQUIRED);
            }
            // 확장자 검증
            validateExtension(filename);
            finalObjectKey = generateObjectKey(imageType, resourceId, filename);
        }

        // TODO(s3): S3 도입 시 objectKey로부터 public/presigned URL 생성 로직 교체
        // 응답 생성 (현재는 로컬 정적 매핑 URL)
        String url = generateImageUrl(finalObjectKey);
        
        return ImageUploadResponseDto.of(finalObjectKey, url);
    }

    /**
     * 타입별 objectKey 경로 생성
     * - 의도: filename과 resourceId로 규칙에 맞는 경로 자동 생성
     * - PROFILE: imageProperties.getProfilePathFormat()
     * - POST: imageProperties.getPostPathFormat()
     */
    private String generateObjectKey(ImageType imageType, Integer resourceId, String filename) {
        String safeFilename = sanitizeFilename(filename);
        if (imageType == ImageType.PROFILE) {
            String format = imageProperties.getProfilePathFormat();
            if (format == null || format.isBlank()) {
                format = "user/%d/profile/%s"; // fallback
            }
            return String.format(format, resourceId, safeFilename);
        } else if (imageType == ImageType.POST) {
            String format = imageProperties.getPostPathFormat();
            if (format == null || format.isBlank()) {
                format = "post/%d/images/%s"; // fallback
            }
            return String.format(format, resourceId, safeFilename);
        }
        throw new BadRequestException(Constants.ErrorMessage.IMAGE_TYPE_NOT_SUPPORTED);
    }

    /**
     * objectKey 경로 규칙 검증
     * - 의도: 제공된 objectKey가 타입별 경로 규칙을 따르는지 검증
     * - 규칙:
     *   - PROFILE: user/{userId}/profile/ 로 시작해야 함
     *   - POST: post/{postId}/images/ 로 시작해야 함
     * - 예외: 규칙 위반 시 IllegalArgumentException
     */
    private void validateObjectKeyPath(ImageType imageType, String objectKey, Integer resourceId) {
        String expectedPrefix;
        if (imageType == ImageType.PROFILE) {
            expectedPrefix = String.format("user/%d/profile/", resourceId);
            if (!objectKey.startsWith(expectedPrefix)) {
                throw new BadRequestException(
                        String.format(Constants.ErrorMessage.PROFILE_IMAGE_PREFIX_MISMATCH, expectedPrefix)
                );
            }
        } else if (imageType == ImageType.POST) {
            expectedPrefix = String.format("post/%d/images/", resourceId);
            if (!objectKey.startsWith(expectedPrefix)) {
                throw new BadRequestException(
                        String.format(Constants.ErrorMessage.POST_IMAGE_PREFIX_MISMATCH, expectedPrefix)
                );
            }
        } else {
            throw new BadRequestException(Constants.ErrorMessage.IMAGE_TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * filename 정리 (경로 분리자, 특수문자 제거)
     * - 의도: 보안을 위해 경로 분리자 및 위험한 문자 제거
     * - 처리: /, \, 선행 점(.) 제거 후 trim
     */
    private String sanitizeFilename(String filename) {
        // 경로 분리자 제거 및 기본 정리
        return filename.replaceAll("[/\\\\]", "")
                .replaceAll("^\\.+", "")  // 선행 점 제거
                .trim();
    }

    /**
     * objectKey 경로 규칙 검증 (외부 서비스용)
     * - 의도: PostService, UserService에서 받은 objectKey 검증
     * - 파라미터: imageType, objectKey, resourceId
     * - 동작:
     *   1. objectKey가 null/빈 값이면 통과 (이미지 없음 허용)
     *   2. 리소스 존재 검증 (userId/postId)
     *   3. 경로 규칙 검증 (타입별 prefix 확인)
     */
    @Transactional(readOnly = true)
    public void validateObjectKey(ImageType imageType, String objectKey, Integer resourceId) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        
        // 리소스 존재 검증
        validateResourceExists(imageType, resourceId);
        
        // 경로 규칙 검증
        validateObjectKeyPath(imageType, objectKey, resourceId);
    }

    /**
     * 확장자 검증
     * - 의도: 허용된 이미지 확장자만 통과
     * - 검사 대상: 파일명 혹은 objectKey의 마지막 세그먼트
     */
    private void validateExtension(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            throw new BadRequestException(Constants.ErrorMessage.FILENAME_REQUIRED);
        }
        // 마지막 세그먼트 추출
        String filename = nameOrPath;
        int slash = filename.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < filename.length()) {
            filename = filename.substring(slash + 1);
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= filename.length()) {
            throw new BadRequestException(Constants.ErrorMessage.IMAGE_EXTENSION_REQUIRED);
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        if (!imageProperties.getAllowedExtensionSet().contains(ext)) {
            throw new BadRequestException(Constants.ErrorMessage.IMAGE_EXTENSION_NOT_SUPPORTED + ext);
        }
    }

    /**
     * 리소스 존재 검증 (공통)
     * - 의도: 타입별 리소스(userId/postId) 존재 확인
     * - 동작:
     *   - PROFILE: userId 존재 확인
     *   - POST: postId 존재 확인
     * - 예외: 리소스 미존재 시 NotFoundException
     */
    private void validateResourceExists(ImageType imageType, Integer resourceId) {
        if (imageType == ImageType.PROFILE) {
            if (resourceId == null) {
                throw new BadRequestException(Constants.ErrorMessage.PROFILE_IMAGE_USER_ID_REQUIRED);
            }
            if (!userRepository.existsById(resourceId)) {
                throw new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND);
            }
        } else if (imageType == ImageType.POST) {
            if (resourceId == null) {
                throw new BadRequestException(Constants.ErrorMessage.POST_IMAGE_POST_ID_REQUIRED);
            }
            if (!postRepository.existsById(resourceId)) {
                throw new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND);
            }
        }
    }

    /**
     * 이미지 URL 생성
     * - 의도: objectKey를 기반으로 접근 가능한 URL 생성
     * - 현재: 임시 경로 반환 (/images/{objectKey})
     * - 추후: S3 설정에 따라 presigned URL 또는 public URL 반환
     */
    private String generateImageUrl(String objectKey) {
        // 로컬 정적 매핑: StaticResourceConfig에서 /files/** → uploads/** 매핑
        // TODO(s3): S3로 전환 시 presigned URL 생성기로 교체
        return Constants.ApiPath.FILES_PREFIX + objectKey;
    }

    /**
     * Multipart 업로드 처리
     * - 의도: 서버가 파일을 직접 받아 정책 검증 후 objectKey 생성
     * - 제약: 파일 크기 ≤ 10MB, 확장자 화이트리스트
     */
    public ImageUploadResponseDto uploadMultipart(ImageType imageType, Integer resourceId, MultipartFile file) {
        // 리소스 존재 검증
        validateResourceExists(imageType, resourceId);

        // 컨트롤러에서 파일/크기/확장자 1차 검증 수행

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BadRequestException(Constants.ErrorMessage.FILENAME_REQUIRED);
        }
        // 확장자 검증
        validateExtension(originalName);

        // 고유 파일명 생성(충돌 방지): {basename}_{uuid}.{ext}
        String uniqueName = buildUniqueFilename(originalName);

        // objectKey 생성
        String objectKey = generateObjectKey(imageType, resourceId, uniqueName);

        // 로컬 저장
        // TODO(s3): S3로 전환 시 storeFileLocal → S3Uploader.upload 로 교체
        storeFileLocal(objectKey, file);

        String url = generateImageUrl(objectKey); // 현재 로컬 URL
        return ImageUploadResponseDto.of(objectKey, url);
    }

    /**
     * 고유 파일명 생성: basename_uuid.ext
     */
    private String buildUniqueFilename(String originalName) {
        String name = originalName;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        String ext = (dot > 0 && dot + 1 < name.length()) ? name.substring(dot + 1) : "";
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return ext.isEmpty() ? (base + "_" + uuid) : (base + "_" + uuid + "." + ext);
    }

    /**
     * 로컬 저장: 프로젝트 루트의 uploads 디렉토리 하위에 objectKey 경로로 저장
     */
    private void storeFileLocal(String objectKey, MultipartFile file) {
        try {
            String configured = imageProperties.getBaseDir();
            Path baseDir = Paths.get((configured == null || configured.isBlank()) ? "uploads" : configured).toAbsolutePath().normalize();
            Path target = baseDir.resolve(objectKey).toAbsolutePath().normalize();
            if (!target.startsWith(baseDir)) {
                throw new BadRequestException(Constants.ErrorMessage.INVALID_FILE_PATH);
            }
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BadRequestException(Constants.ErrorMessage.IMAGE_SAVE_ERROR);
        }
    }
}


