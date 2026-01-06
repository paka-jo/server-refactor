package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.model.FeedCreateRequest;
import com.samsamhajo.deepground.feed.feed.model.FeedUpdateRequest;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedsResponse;
import com.samsamhajo.deepground.feed.feed.repository.FeedMediaRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.feed.feedcomment.service.FeedCommentService;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.entity.MemberProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;
    @Mock
    private FeedMediaService feedMediaService;
    @Mock
    private FeedMediaRepository feedMediaRepository;
    @Mock
    private FeedCommentService feedCommentService;
    @Mock
    private FeedLikeService feedLikeService;

    @InjectMocks
    private FeedService feedService;


    private static final String TEST_CONTENT = "테스트 피드 내용입니다.";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NICKNAME = "테스트유저";

    @Test
    @DisplayName("피드 생성 성공")
    void createFeedSuccess() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        FeedCreateRequest request = new FeedCreateRequest(TEST_CONTENT, List.of());
        Feed expectedFeed = Feed.of(TEST_CONTENT, testMember);

        when(feedRepository.save(any(Feed.class))).thenReturn(expectedFeed);

        // when
        Feed createdFeed = feedService.createFeed(request, testMember);

        // then
        assertThat(createdFeed).isNotNull();
        assertThat(createdFeed.getContent()).isEqualTo(TEST_CONTENT);
        assertThat(createdFeed.getMember().getId()).isEqualTo(testMember.getId());

        verify(feedMediaService).createFeedMedia(any(Feed.class), anyList());
    }

    @Test
    @DisplayName("피드 생성 실패 - 내용이 비어있는 경우")
    void createFeedFailWithEmptyContent() {
        // given
        FeedCreateRequest request = new FeedCreateRequest("", List.of());
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);

        // when & then
        assertThatThrownBy(() -> feedService.createFeed(request, testMember))
                .isInstanceOf(FeedException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_FEED_CONTENT);
    }

    @Test
    @DisplayName("피드 수정 성공")
    void updateFeedSuccess() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Feed existingFeed = Feed.of(TEST_CONTENT, testMember);
        String updatedContent = "수정된 피드 내용입니다.";
        FeedUpdateRequest updateRequest = new FeedUpdateRequest(updatedContent, List.of());
        when(feedRepository.getById(existingFeed.getId())).thenReturn(existingFeed);

        // when
        Feed updatedFeed = feedService.updateFeed(existingFeed.getId(), updateRequest, testMember.getId());

        // then
        assertThat(updatedFeed.getContent()).isEqualTo(updatedContent);
        verify(feedMediaService).updateFeedMedia(any(Feed.class), any(FeedUpdateRequest.class));
    }

    @Test
    @DisplayName("피드 수정 실패 - 내용이 비어있는 경우")
    void updateFeedFailWithEmptyContent() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Feed existingFeed = Feed.of(TEST_CONTENT, testMember);
        FeedUpdateRequest updateRequest = new FeedUpdateRequest("", List.of());

        // when & then
        assertThatThrownBy(() -> feedService.updateFeed(existingFeed.getId(), updateRequest, testMember.getId()))
                .isInstanceOf(FeedException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_FEED_CONTENT);
    }

    @Test
    @DisplayName("피드 목록 조회 성공")
    void getFeedsSuccess() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);

        //  프로필 생성 & 멤버에 연결 (liveIn은 not-null)
        MemberProfile profile = MemberProfile.create(
                null,              // profileImage
                testMember,
                "소개",            // introduction
                "직업",            // job
                "회사",            // company
                "서울",            // liveIn (NOT NULL)
                "학력",            // education
                new ArrayList<>(), // tech stacks
                null, null, null, null // urls
        );
        // 필요 시 profileId 세팅
        ReflectionTestUtils.setField(profile, "profileId", 10L);

        FetchFeedResponse dto1 = new FetchFeedResponse();
        dto1.setFeedId(100L);
        dto1.setContent("피드1");
        dto1.setMemberName(TEST_NICKNAME); // "테스트유저" 직접 주입!
        dto1.setShareCount(0);

        FetchFeedResponse dto2 = new FetchFeedResponse();
        dto2.setFeedId(101L);
        dto2.setContent("피드2");
        dto2.setMemberName(TEST_NICKNAME); // "테스트유저" 직접 주입!
        dto2.setShareCount(0);

        Slice<FetchFeedResponse> feedSlice = new SliceImpl<>(List.of(dto2, dto1));

        when(feedRepository.findFeeds(any(Pageable.class))).thenReturn(feedSlice);

        // when
        FetchFeedsResponse result = feedService.getFeeds(PageRequest.of(0, 10), testMember.getId());

        // then
        assertThat(result.getFeeds()).hasSize(2);
        assertThat(result.getFeeds().get(0).getContent()).isEqualTo("피드2");
        assertThat(result.getFeeds().get(1).getContent()).isEqualTo("피드1");
        assertThat(result.getFeeds().get(0).getMemberName()).isEqualTo(TEST_NICKNAME);
        assertThat(result.getFeeds().get(1).getMemberName()).isEqualTo(TEST_NICKNAME);
    }

    @Test
    @DisplayName("피드 삭제 성공")
    void deleteFeedSuccess() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Feed existingFeed = Feed.of(TEST_CONTENT, testMember);

        // when
        feedService.deleteFeed(existingFeed.getId());

        // then
        verify(feedCommentService).deleteFeedCommentByFeed(existingFeed.getId());
        verify(feedLikeService).deleteAllByFeedId(existingFeed.getId());
        verify(feedMediaService).deleteAllByFeedId(existingFeed.getId());
        verify(feedRepository).deleteById(existingFeed.getId());
    }
}
