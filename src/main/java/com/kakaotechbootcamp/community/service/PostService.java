package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.common.ImageType;
import com.kakaotechbootcamp.community.common.ImageProperties;
import com.kakaotechbootcamp.community.dto.post.*;
import com.kakaotechbootcamp.community.entity.*;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.exception.BadRequestException;
import com.kakaotechbootcamp.community.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 게시글(Post) 도메인 서비스
 * - 목록/상세 조회, 생성, 수정 비즈니스 로직
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostStatRepository postStatRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostStatAsyncService postStatAsyncService;
    private final ImageUploadService imageUploadService;
    private final ImageProperties imageProperties;

    /**
     * 게시글 목록 조회(커서 기반)
     * - 의도: id 내림차순 커서 페이지네이션
     * - 반환: items, nextCursor, hasNext
     */
    @Transactional(readOnly = true)
    public ApiResponse<PostResponseDto> list(Integer cursor, Integer size) {
        int requested = (size == null) ? 10 : size;
        int pageSize = requested <= 0 ? 10 : Math.min(requested, 20);
        Pageable pageable = PageRequest.of(0, pageSize);

        List<Post> posts;
        if (cursor == null || cursor <= 0) {
            posts = postRepository.findFirstPageWithUser(pageable);
        } else {
            posts = postRepository.findPageByCursorWithUser(cursor, pageable);
        }

        List<Integer> postIds = posts.stream().map(Post::getId).toList();
        Map<Integer, PostStat> postIdToStat = new HashMap<>();
        if (!postIds.isEmpty()) {
            postStatRepository.findAllById(postIds).forEach(stat -> postIdToStat.put(stat.getId(), stat));
        }

        List<PostListItemDto> items = PostListItemDto.from(posts, postIdToStat);
        Integer nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).postId();
        boolean hasNext = items.size() == pageSize;

        return ApiResponse.modified(new PostResponseDto(items, nextCursor, hasNext));
    }

    /**
     * 게시글 상세 조회
     * - 의도: 조회수 +1, 이미지/댓글/통계 포함해 반환
     * - 에러: 게시글 미존재 시 404
     */
    @Transactional
    public ApiResponse<PostDetailDto> getDetail(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));

        List<PostImage> images = postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId);
        PostStat stat = postStatRepository.findById(postId).orElseGet(() -> new PostStat(post));

        // 조회수 증가: 비동기 처리
        postStatAsyncService.incrementViewCount(postId);

        // 댓글 + 작성자
        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAscWithUser(postId);

        // 응답에는 증가된 값으로 표현
        PostStat responseStat = new PostStat(post, stat.getLikeCount(), stat.getCommentCount());
        // viewCount는 +1 보정
        responseStat.incrementViewCount();
        return ApiResponse.modified(PostDetailDto.from(post, images, responseStat, comments));
    }

    /**
     * 게시글 생성
     * - 의도: 작성자 검증 후 저장, 이미지 순서대로 저장, 통계 초기화
     * - 에러: 작성자 미존재 시 404
     */
    @Transactional
    public ApiResponse<PostDetailDto> create(PostCreateRequestDto request) {
        User author = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.AUTHOR_NOT_FOUND));

        Post post = new Post(author, request.getTitle().trim(), request.getContent().trim());
        Post saved = postRepository.save(post);

        // 이미지 저장 (displayOrder = index)
        if (request.getImageObjectKeys() != null && !request.getImageObjectKeys().isEmpty()) {
            if (request.getImageObjectKeys().size() > imageProperties.getMaxPerPost()) {
                throw new BadRequestException("이미지 최대 업로드 개수는 " + imageProperties.getMaxPerPost() + "개 입니다");
            }
            // 이미지 objectKey 검증
            List<String> keys = request.getImageObjectKeys();
            for (String objectKey : keys) {
                imageUploadService.validateObjectKey(ImageType.POST, objectKey, saved.getId());
            }
            
            List<PostImage> images = new java.util.ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                images.add(new PostImage(saved, keys.get(i), i));
            }
            postImageRepository.saveAll(images);
        }

        // 통계 생성
        PostStat stat = postStatRepository.save(new PostStat(saved));

        return ApiResponse.created(PostDetailDto.from(saved,
                postImageRepository.findByPostIdOrderByDisplayOrderAsc(saved.getId()),
                stat,
                List.of()));
    }

    /**
     * 게시글 수정
     * - 의도: 제목/내용 선택 수정, 이미지 배열 전달 시 전체 교체
     * - 정책: null=미변경, 빈 배열=전부 제거
     * - 에러: 게시글 미존재 시 404
     */
    @Transactional
    public ApiResponse<PostDetailDto> update(Integer postId, PostUpdateRequestDto request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            post.updateTitle(request.getTitle().trim());
        }
        if (request.getContent() != null && !request.getContent().trim().isEmpty()) {
            post.updateContent(request.getContent().trim());
        }

        // 이미지 전체 교체 정책
        if (request.getImageObjectKeys() != null) {
            List<PostImage> existing = postImageRepository.findByPostOrderByDisplayOrderAsc(post);
            postImageRepository.deleteAll(existing);

            List<String> keys = request.getImageObjectKeys();
            if (!keys.isEmpty()) {
                if (keys.size() > imageProperties.getMaxPerPost()) {
                    throw new BadRequestException("이미지 최대 업로드 개수는 " + imageProperties.getMaxPerPost() + "개 입니다");
                }
                // 이미지 objectKey 검증
                for (String objectKey : keys) {
                    imageUploadService.validateObjectKey(ImageType.POST, objectKey, postId);
                }
                
                List<PostImage> newImages = new java.util.ArrayList<>(keys.size());
                for (int i = 0; i < keys.size(); i++) {
                    newImages.add(new PostImage(post, keys.get(i), i));
                }
                postImageRepository.saveAll(newImages);
            }
        }

        PostStat stat = postStatRepository.findById(postId).orElseGet(() -> postStatRepository.save(new PostStat(post)));

        return ApiResponse.modified(PostDetailDto.from(post,
                postImageRepository.findByPostIdOrderByDisplayOrderAsc(postId),
                stat,
                commentRepository.findByPostIdOrderByCreatedAtAsc(postId)));
    }

    /**
     * 게시글 삭제(소프트 삭제)
     * - 의도: deletedAt 설정으로 비활성화, 연관 데이터는 유지
     * - 에러: 게시글 미존재 시 404
     */
    @Transactional
    public ApiResponse<Void> delete(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));
        post.softDelete();
        return ApiResponse.deleted(null);
    }
}
