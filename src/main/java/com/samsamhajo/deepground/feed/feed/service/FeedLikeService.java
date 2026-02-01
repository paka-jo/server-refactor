package com.samsamhajo.deepground.feed.feed.service;


import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedLike;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedLikeService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final RedisTemplate<String,Object> redisTemplate;

    @Transactional
    public void feedLikeIncrease(Long feedId, Member member) {

        Long memberId = member.getId();

        String key = getRedisKey(feedId);

        Boolean isFeedLike  = redisTemplate.opsForValue().setBit(key,memberId,true);

        if (isFeedLike != null && isFeedLike) {
            throw new FeedException(FeedErrorCode.FEED_LIKE_ALREADY_EXISTS);
        }

        Feed feed = feedRepository.getReferenceById(feedId);

        FeedLike feedLike = FeedLike.of(feed, member);

        feedLikeRepository.save(feedLike);

        redisTemplate.opsForSet().add(getDirtyKey(), String.valueOf(feedId));
    }

    @Transactional
    public void feedLikeDecrease(Long feedId, Long memberId) {
        String key = getRedisKey(feedId);

        Boolean isLiked = redisTemplate.opsForValue().getBit(key, memberId);

        if (isLiked != null && !isLiked) {
            throw new FeedException(FeedErrorCode.FEED_LIKE_NOT_FOUND);
        }

        redisTemplate.opsForValue().setBit(key, memberId, false);

        redisTemplate.opsForSet().add(getDirtyKey(), String.valueOf(feedId));

        feedLikeRepository.deleteByFeedIdAndMemberId(feedId, memberId);
    }

    public void deleteAllByFeedId(Long feedId) {

        feedLikeRepository.deleteAllByFeedId(feedId);
        redisTemplate.delete(getRedisKey(feedId));
    }

    public boolean isLiked(Long feedId, Long memberId) {
        return feedLikeRepository.existsByFeedIdAndMemberId(feedId, memberId);
    }

    private String getRedisKey(Long feedId) {
        return "feed:" + feedId + ":likes:members";
    }

    private String getDirtyKey() {
        return "feed:likes:dirty";
    }

}
