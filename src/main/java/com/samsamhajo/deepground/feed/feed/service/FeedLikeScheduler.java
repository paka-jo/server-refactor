package com.samsamhajo.deepground.feed.feed.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FeedLikeScheduler {

    private final FeedLikeSyncService feedLikeSyncService;

    @Scheduled(fixedDelay = 60000)
    public void syncFeedLikeCount() {
        feedLikeSyncService.syncFeedLikesToDatabase();
    }
}
