package com.samsamhajo.deepground.feed.feed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public Feed createFeed(FeedCreateRequest request, Member member) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new FeedException(FeedErrorCode.INVALID_FEED_CONTENT);
        }

        Feed feed = Feed.of(request.getContent(), member);

        feedRepository.save(feed);

        List<String> savedUrls = saveFeedMedia(request, feed);
        FetchFeedResponse cacheDto = FetchFeedResponse.forCache(feed, member, savedUrls);

        eventPublisher.publishEvent(new FeedCreateEvent(cacheDto));

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

        List<FetchFeedResponse> content;
        boolean hasNext;

        if (pageable.getPageNumber() == 0) {
            List<FetchFeedResponse> cachedData = fetchFromRedis();

            if (!cachedData.isEmpty()) {
                int pageSize = pageable.getPageSize();

                if (cachedData.size() > pageSize) {
                    content = new ArrayList<>(cachedData.subList(0, pageSize));
                    hasNext = true;
                }
                else {
                    content = cachedData;
                    hasNext = (cachedData.size() >= pageSize);
                }
            } else {
                Slice<FetchFeedResponse> slice = feedRepository.findFeeds(pageable);
                content = slice.getContent();
                hasNext = slice.hasNext();
            }
        } else {
            Slice<FetchFeedResponse> slice = feedRepository.findFeeds(pageable);
            content = slice.getContent();
            hasNext = slice.hasNext();
        }
        enrichFeeds(content, memberId);

        return FetchFeedsResponse.of(new SliceImpl<>(content, pageable, hasNext));
    }



    private List<FetchFeedResponse> fetchFromRedis(){
        try {
            List<Object> cachedData = redisTemplate.opsForList().range("feed:recent:20", 0, -1);

            if (cachedData == null || cachedData.isEmpty()) {
                return List.of();
            }

            return cachedData.stream()
                    .map(obj -> objectMapper.convertValue(obj, FetchFeedResponse.class))
                    .toList();
        } catch (Exception e) {
            log.error("Redis로부터 피드 캐시를 가져오는 중 에러 발생", e);
            return List.of();
        }
    }

    private void enrichFeeds(List<FetchFeedResponse> feeds, Long memberId) {
        if(feeds.isEmpty() || memberId == null){
            return;
        }
        List<Long> feedIds = feeds.stream()
                .map(FetchFeedResponse::getFeedId)
                .toList();

        List<Long> likedFeedIds = feedLikeRepository.findLikedFeedIds(memberId,feedIds);
        Set<Long> likedFeedIdSet = new HashSet<>(likedFeedIds);

        feeds.forEach(feed -> {
            feed.setLiked(likedFeedIdSet.contains(feed.getFeedId()));
        });
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


    private List<String> saveFeedMedia(FeedCreateRequest request, Feed feed) {
        return feedMediaService.createFeedMedia(feed, request.getImages());
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