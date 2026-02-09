package com.samsamhajo.deepground.feed.feed.handler;

import com.samsamhajo.deepground.feed.feed.model.FeedCreateEvent;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedCacheHandler {

    private final RedisTemplate<String, Object> redisTemplate;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void handleFeedCreatedEvent(FeedCreateEvent event) {
        try {
            String key = "feed:recent:20";
            redisTemplate.opsForList().leftPush(key, event.fetchFeedResponse());
            redisTemplate.opsForList().trim(key, 0, 19);

            log.info("Redis 캐시 적재 성공");
        } catch (Exception e) {
            log.error("Redis 저장 실패 원인:", e);
        }
    }
}

