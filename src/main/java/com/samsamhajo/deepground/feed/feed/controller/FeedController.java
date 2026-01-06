package com.samsamhajo.deepground.feed.feed.controller;

import com.samsamhajo.deepground.auth.security.CustomUserDetails;
import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.exception.FeedSuccessCode;
import com.samsamhajo.deepground.feed.feed.model.*;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedsResponse;
import com.samsamhajo.deepground.feed.feed.service.FeedService;
import com.samsamhajo.deepground.global.success.SuccessResponse;
import com.samsamhajo.deepground.global.utils.GlobalLogger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Feed>> createFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute FeedCreateRequest request) {
        GlobalLogger.info("피드 생성 요청 - memberId: {}", userDetails.getMember().getId());
        feedService.createFeed(request, userDetails.getMember());

        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_CREATED));
    }

    @GetMapping("/list")
    public ResponseEntity<SuccessResponse<FetchFeedsResponse>> getFeeds(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){

        Long memberId = null;
        if (userDetails != null) {
            memberId = userDetails.getMember().getId();
        }
        FetchFeedsResponse response = feedService.getFeeds(pageable ,memberId);

        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_LIST_FETCHED, response));
    }

    @GetMapping("/summaries")
    public ResponseEntity<SuccessResponse<FetchFeedSummariesResponse>> getFeedsSummariesByMemberId(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Long memberId = userDetails.getMember().getId();
        FetchFeedSummariesResponse response = feedService.getFeedSummariesByMemberId(pageable, memberId);

        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_LIST_FETCHED, response));
    }

    @GetMapping(value = "/{feedId}")
    public ResponseEntity<SuccessResponse<FetchFeedResponse>> getFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("feedId") Long feedId) {

        Long memberId = null;
        if (userDetails != null) {
            memberId = userDetails.getMember().getId();
        }

        FetchFeedResponse response = feedService.getFeed(feedId, memberId);

        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_FETCHED, response));
    }

    @PutMapping(value = "/{feedId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Feed>> updateFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("feedId") Long feedId,
            @ModelAttribute FeedUpdateRequest request) {

        Long memberId = userDetails.getMember().getId();
        feedService.updateFeed(feedId, request, memberId);
        
        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_UPDATED));
    }

    @DeleteMapping("/{feedId}")
    public ResponseEntity<SuccessResponse<Void>> deleteFeed(
            @PathVariable("feedId") Long feedId) {
        feedService.deleteFeed(feedId);

        return ResponseEntity
                .ok(SuccessResponse.of(FeedSuccessCode.FEED_DELETED));
    }
}
