package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.ApiResponse;
import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.dto.post.PostStatResponseDto;
import com.kakaotechbootcamp.community.entity.Post;
import com.kakaotechbootcamp.community.entity.PostStat;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.repository.CommentRepository;
import com.kakaotechbootcamp.community.repository.PostLikeRepository;
import com.kakaotechbootcamp.community.repository.PostRepository;
import com.kakaotechbootcamp.community.repository.PostStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 통계 서비스
 * - 의도: 통계 조회/동기화(좋아요/댓글 수) 제공
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostStatService {

    private final PostRepository postRepository;
    private final PostStatRepository postStatRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    /**
     * 통계 조회
     * - 에러: 미존재 시 404
     */
    public ApiResponse<PostStatResponseDto> getByPostId(Integer postId) {
        PostStat stat = postStatRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("통계를 찾을 수 없습니다"));
        return ApiResponse.modified(PostStatResponseDto.from(stat));
    }

    /**
     * 통계 동기화
     * - 의도: 테이블(PostLike, Comment)로부터 like/comment 수 집계하여 PostStat 반영
     */
    @Transactional
    public ApiResponse<PostStatResponseDto> syncAll(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));

        PostStat stat = postStatRepository.findById(postId).orElseGet(() -> new PostStat(post));

        int likeCount = postLikeRepository.countByIdPostId(postId);
        int commentCount = commentRepository.countByPostId(postId);

        stat.syncLikeCount(likeCount);
        stat.syncCommentCount(commentCount);

        PostStat saved = postStatRepository.save(stat);
        return ApiResponse.modified(PostStatResponseDto.from(saved));
    }
}
