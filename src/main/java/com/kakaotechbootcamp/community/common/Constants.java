package com.kakaotechbootcamp.community.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {

    public static final class Cors {
        public static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"};
        public static final String[] ALLOWED_HEADERS = {"Content-Type", "Authorization", "X-Requested-With", "Accept", "Origin"};
        public static final String EXPOSED_HEADER_SET_COOKIE = "Set-Cookie";
        public static final long MAX_AGE = 3600L;
        private Cors() {}
    }

    public static final class HttpMethod {
        public static final String GET = "GET";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
        public static final String PATCH = "PATCH";
        public static final String OPTIONS = "OPTIONS";
        private HttpMethod() {}
    }

    public static final class ContentType {
        public static final String APPLICATION_JSON_UTF8 = "application/json;charset=UTF-8";
        private ContentType() {}
    }

    public static final class ApiPath {
        public static final String USERS = "/users";
        public static final String USERS_CHECK_EMAIL = "/users/check-email";
        public static final String USERS_CHECK_NICKNAME = "/users/check-nickname";
        public static final String AUTH = "/auth";
        public static final String FILES = "/files";
        public static final String FILES_PREFIX = "/files/";
        public static final String POSTS = "/posts";
        public static final String COMMENTS = "/comments";
        private ApiPath() {}
    }

    public static final class SessionKey {
        public static final String USER_ID = "userId";
        public static final String USER_EMAIL = "userEmail";
        private SessionKey() {}
    }

    public static final class ErrorMessage {
        public static final String LOGIN_REQUIRED = "로그인이 필요합니다";
        public static final String INVALID_EMAIL_OR_PASSWORD = "이메일 또는 비밀번호가 올바르지 않습니다";

        public static final String USER_NOT_FOUND = "사용자를 찾을 수 없습니다";
        public static final String AUTHOR_NOT_FOUND = "작성자를 찾을 수 없습니다";
        public static final String EMAIL_ALREADY_IN_USE = "이미 사용 중인 이메일입니다";
        public static final String NICKNAME_ALREADY_IN_USE = "이미 사용 중인 닉네임입니다";
        public static final String PASSWORD_ALL_REQUIRED = "비밀번호를 모두 입력해주세요";
        public static final String PASSWORD_SAME_AS_PREVIOUS = "이전 비밀번호와 새 비밀번호가 동일합니다";
        public static final String PASSWORD_MISMATCH = "현재 비밀번호가 일치하지 않습니다";

        public static final String POST_NOT_FOUND = "게시글을 찾을 수 없습니다";
        public static final String VALID_POST_ID_REQUIRED = "유효한 게시글 ID가 필요합니다";

        public static final String COMMENT_NOT_FOUND = "댓글을 찾을 수 없습니다";
        public static final String PARENT_COMMENT_NOT_FOUND = "부모 댓글을 찾을 수 없습니다";
        public static final String VALID_COMMENT_ID_REQUIRED = "유효한 댓글 ID가 필요합니다";
        public static final String COMMENT_CONTENT_REQUIRED = "댓글 내용을 입력해주세요";
        public static final String PARENT_COMMENT_NOT_IN_POST = "부모 댓글이 해당 게시글에 속하지 않습니다";
        public static final String DELETED_COMMENT_NO_REPLY = "삭제된 댓글에는 답글을 달 수 없습니다";
        public static final String MAX_DEPTH_EXCEEDED = "대댓글의 하위에는 더 이상 답글을 달 수 없습니다";
        public static final String DELETED_COMMENT = "삭제된 댓글입니다";

        public static final String IMAGE_TYPE_NOT_SUPPORTED = "지원하지 않는 이미지 타입입니다";
        public static final String FILENAME_REQUIRED = "파일명이 필요합니다";
        public static final String IMAGE_EXTENSION_REQUIRED = "이미지 확장자가 필요합니다 (.jpeg/.jpg/.png/.gif/.webp)";
        public static final String IMAGE_EXTENSION_NOT_SUPPORTED = "지원하지 않는 이미지 확장자입니다: ";
        public static final String PROFILE_IMAGE_USER_ID_REQUIRED = "프로필 이미지 검증 시 userId는 필수입니다";
        public static final String POST_IMAGE_POST_ID_REQUIRED = "게시글 이미지 검증 시 postId는 필수입니다";
        public static final String OBJECT_KEY_OR_FILENAME_REQUIRED = "objectKey 또는 filename 중 하나는 필수입니다";
        public static final String INVALID_FILE_PATH = "잘못된 파일 경로입니다";
        public static final String IMAGE_SAVE_ERROR = "이미지 저장 중 오류가 발생했습니다";
        public static final String PROFILE_IMAGE_PREFIX_MISMATCH = "프로필 이미지 objectKey는 '%s'로 시작해야 합니다";
        public static final String POST_IMAGE_PREFIX_MISMATCH = "게시글 이미지 objectKey는 '%s'로 시작해야 합니다";

        public static final String VALID_ID_REQUIRED = "유효한 ID가 필요합니다";
        private ErrorMessage() {}
    }

    public static final class Pagination {
        public static final int DEFAULT_PAGE = 0;
        public static final int DEFAULT_SIZE = 10;
        public static final int MAX_SIZE = 20;
        private Pagination() {}
    }

    public static final class Comment {
        public static final int MAX_DEPTH = 1;
        private Comment() {}
    }
}

