package com.kakaotechbootcamp.community.service;

import com.kakaotechbootcamp.community.common.Constants;
import com.kakaotechbootcamp.community.entity.Post;
import com.kakaotechbootcamp.community.entity.PostLike;
import com.kakaotechbootcamp.community.entity.PostStat;
import com.kakaotechbootcamp.community.exception.NotFoundException;
import com.kakaotechbootcamp.community.repository.PostLikeRepository;
import com.kakaotechbootcamp.community.repository.PostRepository;
import com.kakaotechbootcamp.community.repository.PostStatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 좋아요 도메인 서비스
 * - 의도: 게시글에 대한 사용자 좋아요 생성/삭제 처리 및 통계 비동기 반영
 * - 사용처: PostLikeController (POST/DELETE /posts/{postId}/likes)
 */
@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostStatRepository postStatRepository;
    private final PostStatAsyncService postStatAsyncService;

    /**
     * 좋아요 생성
     * - 의도: 사용자가 게시글에 좋아요 등록, 이미 존재하면 no-op
     * - 파라미터: userId(사용자 ID), postId(게시글 ID)
     * - 반환: 현재 게시글의 likeCount(생성 시 +1, 이미 존재 시 +0)
     * - 동작: PostLike 엔티티 생성 → PostStat 비동기 증가 호출 → 즉시 PostStat에서 likeCount 반환
     */
    @Transactional
    public int saveLike(Integer userId, Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));

        boolean exists = postLikeRepository.existsByIdPostIdAndIdUserId(postId, userId);
        PostStat stat = postStatRepository.findById(postId).orElseGet(() -> new PostStat(post));
        if (exists) {
            return stat.getLikeCount();
        }
        postLikeRepository.save(new PostLike(userId, postId));
        postStatAsyncService.incrementLikeCount(postId);
        return stat.getLikeCount() + 1;
    }

    /**
     * 좋아요 취소
     * - 의도: 사용자가 게시글에 좋아요 취소, 아직 없으면 no-op
     * - 파라미터: userId(사용자 ID), postId(게시글 ID)
     * - 반환: 현재 게시글의 likeCount(삭제 시 -1, 미존재 시 +0)
     * - 동작: PostLike 엔티티 삭제 → PostStat 비동기 감소 호출 → 즉시 PostStat에서 likeCount 반환(0 미만 방지)
     */
    @Transactional
    public int removeLike(Integer userId, Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorMessage.POST_NOT_FOUND));

        boolean exists = postLikeRepository.existsByIdPostIdAndIdUserId(postId, userId);
        PostStat stat = postStatRepository.findById(postId).orElseGet(() -> new PostStat(post));
        if (!exists) {
            return stat.getLikeCount();
        }
        postLikeRepository.deleteByIdPostIdAndIdUserId(postId, userId);
        postStatAsyncService.decrementLikeCount(postId);
        return Math.max(0, stat.getLikeCount() - 1);
    }
}
