package com.samsamhajo.deepground.feed.feed.service;

import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedLikeSyncService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FeedRepository feedRepository;

    @Transactional
    public void syncFeedLikesToDatabase() {
        List<Object> dirtyFeedIds = redisTemplate.opsForSet().pop("feed:likes:dirty", 1000);
        if (dirtyFeedIds == null || dirtyFeedIds.isEmpty()) return;

        for (Object idObj : dirtyFeedIds) {
            try{
                Long feedId = Long.valueOf(String.valueOf(idObj));

                Long redisCount = redisTemplate.execute((RedisCallback<Long>) conn ->
                        conn.bitCount(("feed:" + feedId + ":likes:members").getBytes()));
                if (redisCount != null) {
                    feedRepository.updateLikeCount(feedId, redisCount);
                }
            } catch (Exception e){
                log.error("Failed to sync like count for feedId: {}",idObj,e);
            }

        }
    }
}
