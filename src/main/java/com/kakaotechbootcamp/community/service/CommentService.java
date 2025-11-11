package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.dto.comment.*;
import com.kakaotechbootcamp.community.entity.Comment;
import com.kakaotechbootcamp.community.entity.Post;
import com.kakaotechbootcamp.community.entity.User;
import com.kakaotechbootcamp.community.exception.BadRequestException;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.repository.CommentRepository;
import com.kakaotechbootcamp.community.repository.PostRepository;
import com.kakaotechbootcamp.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;


/**
 * 댓글(Comment) 도메인 서비스
 * - 역할: 댓글 목록/생성/수정/삭제 비즈니스 로직 수행
 * - 검증: 리소스 존재, 부모-자식 정합성, 내용 유효성
 * - 통계: 생성/삭제 시 댓글수 비동기 증감 처리
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostStatAsyncService postStatAsyncService;

    /**
     * 댓글 목록 페이징 조회 (게시글 기준)
     * - 의도: 생성일 오름차순 Page 반환
     * - 파라미터: page(기본 0), size(기본 10, 최대 20)
     * - 에러: 게시글 미존재 시 404
     */
    public ApiResponse<Page<CommentResponseDto>> listByPost(Integer postId, Integer page, Integer size) {
        if (postId == null || postId <= 0) {
            throw new BadRequestException(Constants.ErrorMessage.VALID_POST_ID_REQUIRED);
        }
        if (!postRepository.existsById(postId)) {
            throw new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND);
        }
        int p = (page == null) ? Constants.Pagination.DEFAULT_PAGE : Math.max(0, page);
        int requested = (size == null || size <= 0) ? Constants.Pagination.DEFAULT_SIZE : size;
        int pageSize = Math.min(requested, Constants.Pagination.MAX_SIZE);

        var sorts = new java.util.ArrayList<Sort.Order>();
        sorts.add(Sort.Order.asc("createdAt"));
        var pageable = PageRequest.of(p, pageSize, Sort.by(sorts));

        Page<CommentResponseDto> pageResult = commentRepository.findAllByPostId(postId, pageable)
                .map(CommentResponseDto::from);

        return ApiResponse.modified(pageResult);
    }

    /**
     * 댓글 생성
     * - 의도: 게시글/사용자 존재 확인 후 댓글 저장, parentId 전달 시 대댓글로 처리
     * - 정책: 내용 공백 불가, 부모는 동일 게시글에 속해야 함, 삭제된 부모 금지, 최대 깊이=2(루트=0, 대댓글=1)
     */
    @Transactional
    public ApiResponse<CommentResponseDto> create(Integer postId, Integer userId, CommentRequestDto request) {
        if (postId == null || postId <= 0 || userId == null || userId <= 0) {
            throw new BadRequestException(Constants.ErrorMessage.VALID_ID_REQUIRED);
        }
        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.USER_NOT_FOUND));

        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new BadRequestException(Constants.ErrorMessage.COMMENT_CONTENT_REQUIRED);
        }

        Integer parentId = null;
        int depth = 0;
        if (request.getParentId() != null) {
            Comment parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.PARENT_COMMENT_NOT_FOUND));
            if (!parent.getPost().getId().equals(postId)) {
                throw new BadRequestException(Constants.ErrorMessage.PARENT_COMMENT_NOT_IN_POST);
            }
            if (parent.getDeletedAt() != null) {
                throw new BadRequestException(Constants.ErrorMessage.DELETED_COMMENT_NO_REPLY);
            }
            if (parent.getDepth() >= Constants.Comment.MAX_DEPTH) { // 최대 깊이: 2
                throw new BadRequestException(Constants.ErrorMessage.MAX_DEPTH_EXCEEDED);
            }
            parentId = parent.getId();
            depth = parent.getDepth() + 1;
        }

        Comment saved = commentRepository.save(new Comment(post, user, parentId, request.getContent().trim(), depth));

        // 비동기 댓글수 +1
        postStatAsyncService.incrementCommentCount(postId);

        return ApiResponse.created(CommentResponseDto.from(saved));
    }

    /**
     * 댓글 수정
     * - 의도: 내용만 수정
     * - 에러: 댓글 미존재/내용 공백 시 400/404
     */
    @Transactional
    public ApiResponse<CommentResponseDto> update(Integer commentId, String content) {
        if (commentId == null || commentId <= 0) {
            throw new BadRequestException(Constants.ErrorMessage.VALID_COMMENT_ID_REQUIRED);
        }
        if (content == null || content.isBlank()) {
            throw new BadRequestException(Constants.ErrorMessage.COMMENT_CONTENT_REQUIRED);
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.COMMENT_NOT_FOUND));
        comment.updateContent(content.trim());
        Comment saved = commentRepository.save(comment);
        return ApiResponse.modified(CommentResponseDto.from(saved));
    }

    /**
     * 댓글 삭제
     * - 의도: deletedAt 설정, 데이터 보관, 내용 마스킹("삭제된 댓글입니다")
     */
    @Transactional
    public ApiResponse<Void> delete(Integer commentId) {
        if (commentId == null || commentId <= 0) {
            throw new BadRequestException(Constants.ErrorMessage.VALID_COMMENT_ID_REQUIRED);
        }
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.COMMENT_NOT_FOUND));
        Integer postId = comment.getPost().getId();
        comment.updateContent(Constants.ErrorMessage.DELETED_COMMENT);
        comment.softDelete();
        // 비동기 댓글수 -1
        postStatAsyncService.decrementCommentCount(postId);
        return ApiResponse.deleted(null);
    }
}
