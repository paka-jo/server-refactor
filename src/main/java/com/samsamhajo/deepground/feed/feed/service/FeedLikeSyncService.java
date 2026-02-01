package com.samsamhajo.deepground.feed.feed.service;

import java.util.Set;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedLikeSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FeedRepository feedRepository;

    @Transactional
    public void syncFeedLikesToDatabase() {
        Set<Object> dirtyFeedIds = redisTemplate.opsForSet().members("feed:likes:dirty");
        if (dirtyFeedIds == null || dirtyFeedIds.isEmpty()) return;

        for (Object idObj : dirtyFeedIds) {
            Long feedId = Long.valueOf((String) idObj);
            Long redisCount = redisTemplate.execute((RedisCallback<Long>) conn ->
                    conn.bitCount(("feed:" + feedId + ":likes:members").getBytes())
            );

            if (redisCount != null) {
                feedRepository.updateLikeCount(feedId, redisCount);
            }
        }
        redisTemplate.opsForSet().remove("feed:likes:dirty", dirtyFeedIds.toArray());
    }
}
