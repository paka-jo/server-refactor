package com.samsamhajo.deepground.feed.feed.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private Set<Object> getAndClearDirtySet(String key) {
        String script =
                "local members = redis.call('SMEMBERS', KEYS[1]) " +
                        "if #members > 0 then " +
                        "    redis.call('DEL', KEYS[1]) " +
                        "end " +
                        "return members";

        List<Object> result = redisTemplate.execute(
                new DefaultRedisScript<>(script, List.class),
                Collections.singletonList(key)
        );

        if (result == null || result.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(result);
    }

    @Transactional
    public void syncFeedLikesToDatabase() {
        String key = "feed:likes:dirty";

        Set<Object> dirtyFeedIds = getAndClearDirtySet(key);
        if (dirtyFeedIds.isEmpty()) return;

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
