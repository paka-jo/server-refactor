package com.samsamhajo.deepground.feed.feed.repository;

import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;


public interface FeedRepositoryCustom {

    Slice<FetchFeedResponse> findFeeds(Pageable pageable);
}