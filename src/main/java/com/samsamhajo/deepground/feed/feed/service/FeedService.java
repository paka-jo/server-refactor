package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedMedia;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.model.*;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedsResponse;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedMediaRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.feed.feedcomment.service.FeedCommentService;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.entity.MemberProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedMediaService feedMediaService;
    private final FeedCommentService feedCommentService;
    private final FeedLikeService feedLikeService;
    private final FeedMediaRepository feedMediaRepository;
    private final FeedLikeRepository feedLikeRepository;

    @Transactional
    public Feed createFeed(FeedCreateRequest request, Member member) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new FeedException(FeedErrorCode.INVALID_FEED_CONTENT);
        }

        Feed feed = Feed.of(request.getContent(), member);

        feedRepository.save(feed);

        saveFeedMedia(request, feed);

        return feed;
    }

    @Transactional
    public Feed updateFeed(Long feedId, FeedUpdateRequest request, Long memberId) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new FeedException(FeedErrorCode.INVALID_FEED_CONTENT);
        }

        Feed feed = feedRepository.getById(feedId);

        // 피드 내용 업데이트
        feed.updateContent(request.getContent());

        // 미디어 업데이트
        feedMediaService.updateFeedMedia(feed, request);

        return feed;
    }

    public FetchFeedResponse getFeed(Long feedId, Long memberId) {

        FetchFeedResponse feed = feedRepository.getByIdWithMemberAndProfile(feedId)
                .orElseThrow(() -> new FeedException(FeedErrorCode.FEED_NOT_FOUND));

        boolean isUserAuthenticated = (memberId != null);

        boolean isLikedByCurrentUser = false;
        if (isUserAuthenticated) {
            isLikedByCurrentUser = feedLikeService.isLiked(feed.getFeedId(), memberId);
        }

        List<String> mediaUrls = feedMediaRepository.findAllByFeedId(feedId)
                .stream()
                .map(FeedMedia::getMediaUrl)
                .collect(Collectors.toList());

        return FetchFeedResponse.of(
                feed.getPublicId(),
                feed.getProfilePublicId(),
                feedId,
                feed.getMemberName(),
                feed.getContent(),
                feed.getLikeCount(),
                feed.getCommentCount(),
                feed.getShareCount(),
                isLikedByCurrentUser,
                feed.getProfileImageUrl(),
                feed.getCreatedAt(),
                mediaUrls);
    }

    public FetchFeedsResponse getFeeds(Pageable pageable, Long memberId) {

        Slice<FetchFeedResponse> feedSlice = feedRepository.findFeeds(pageable);
        List<FetchFeedResponse> feeds = feedSlice.getContent();

        List<Long> feedIds = feeds.stream().map(FetchFeedResponse::getFeedId).toList();

        Map<Long,List<String>> mediaMap = feedMediaRepository.findByFeedIdIn(feedIds)
                .stream()
                .collect(Collectors.groupingBy(
                        fm -> fm.getFeed().getId(),
                        Collectors.mapping(FeedMedia::getMediaUrl, Collectors.toList())
                ));

        Set<Long> likedFeedIdSet = new HashSet<>();
        if (memberId != null) {
            List<Long> likes = feedLikeRepository.findLikedFeedIds(memberId, feedIds);
            likedFeedIdSet.addAll(likes);
        }

        feeds.forEach(f -> {
            f.setMediaUrls(mediaMap.getOrDefault(f.getFeedId(), List.of()));
            f.setLiked(likedFeedIdSet.contains(f.getFeedId()));
        });

        return FetchFeedsResponse.of(feedSlice);
    }

    public FetchFeedSummariesResponse getFeedSummariesByMemberId(Pageable pageable, Long memberId) {

        Page<Feed> feeds = feedRepository.findAllByMemberId(pageable, memberId);

        return FetchFeedSummariesResponse.of(
                feeds.getContent().stream()
                        .map(feed -> FetchFeedSummaryResponse.builder()
                                .feedId(feed.getId())
                                .content(feed.getContent())
                                .createdAt(feed.getCreatedAt().toLocalDate())
                                .build())
                        .toList(),
                feeds.getTotalElements(),
                feeds.getNumber(),        // 현재 페이지 번호 (0부터 시작)
                feeds.getSize(),          // 요청된 페이지 크기
                feeds.getTotalPages()
        );
    }


    private void saveFeedMedia(FeedCreateRequest request, Feed feed) {
        feedMediaService.createFeedMedia(feed, request.getImages());
    }

    @Transactional
    public void deleteFeed(Long feedId) {

        Feed feed = feedRepository.getById(feedId);

        deleteRelatedEntities(feedId);

        feed.softDelete();

    }

    private void deleteRelatedEntities(Long feedId) {
        feedCommentService.deleteFeedCommentByFeed(feedId);
        feedLikeService.deleteAllByFeedId(feedId);
        feedMediaService.deleteAllByFeedId(feedId);
    }

}
