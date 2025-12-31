package com.samsamhajo.deepground.feed.feed.model.v2;

import lombok.Getter;
import org.springframework.data.domain.Slice;

import java.util.List;

@Getter
public class FetchFeedsResponse {

    private List<FetchFeedResponse> feeds;
    private int page;
    private boolean hasNext;

    private FetchFeedsResponse(List<FetchFeedResponse> feeds, int page, boolean hasNext) {
        this.feeds = feeds;
        this.page = page;
        this.hasNext = hasNext;
    }

    public static FetchFeedsResponse of(Slice<FetchFeedResponse> slice) {
        return new FetchFeedsResponse(
                slice.getContent(),
                slice.getNumber(),
                slice.hasNext()
        );
    }
}