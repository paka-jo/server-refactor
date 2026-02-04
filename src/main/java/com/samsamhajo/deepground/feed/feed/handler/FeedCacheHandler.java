package com.samsamhajo.deepground.feed.feed.handler;

import com.samsamhajo.deepground.feed.feed.model.FeedCreateEvent;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FeedCacheHandler {

    private final RedisTemplate<String, Object> redisTemplate;

    @TransactionalEventListener(phase =  TransactionPhase.AFTER_COMMIT)
    public void handleFeedCreatedEvent(FeedCreateEvent event){
        FetchFeedResponse cacheDto = FetchFeedResponse.forCache(event.feed(), event.member(), event.mediaUrls());

        String key = "feed:recent:20";
        redisTemplate.opsForList().leftPush(key, cacheDto);
        redisTemplate.opsForList().trim(key,0,19);
    }
}
