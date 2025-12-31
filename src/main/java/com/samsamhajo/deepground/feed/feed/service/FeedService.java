package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedMedia;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.model.*;
import com.samsamhajo.deepground.feed.feed.model.v2.FetchFeedResponse;
import com.samsamhajo.deepground.feed.feed.model.v2.FetchFeedsResponse;
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

    public com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse getFeed(Long feedId, Long memberId) {
        Feed feed = feedRepository.getById(feedId);

        boolean isUserAuthenticated = (memberId != null);

        Member member = feed.getMember();

        UUID publicProfileId = Optional.ofNullable(member.getMemberProfile())
                .map(MemberProfile::getProfilePublicId)
                .orElse(null);

        boolean isLikedByCurrentUser = false;
        if (isUserAuthenticated) {
            isLikedByCurrentUser = feedLikeService.isLiked(feed.getId(), memberId);
        }

        return com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse.builder()
                .feedId(feed.getId())
                .content(feed.getContent())
                .createdAt(feed.getCreatedAt().toLocalDate())
                .publicId(feed.getMember().getPublicId())
                .profilePublicId(publicProfileId)
                .memberName(feed.getMember().getNickname())
                .mediaUrls(feedMediaService.findAllMediaUrlsByFeedId(feed.getId()))
                .shareCount(feed.getCommentCount())
                .commentCount(feed.getCommentCount())
                .likeCount(feed.getLikeCount())
                .isLiked(isLikedByCurrentUser)
                .profileImageUrl(member.getMemberProfile().getProfileImage())
                .build();
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
