package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.model.*;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
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
    private FeedLikeRepository feedLikeRepository;
    @Mock
    private FeedCommentService feedCommentService;
    @Mock
    private FeedLikeService feedLikeService;
    @Mock
    private RedisTemplate<String,Object> redisTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        MemberProfile testProfile = MemberProfile.create(
                "image.png", testMember, "intro", "job", "company", "city", "edu",
                new ArrayList<>(), "git", "link", "web", "twit"
        );
        ReflectionTestUtils.setField(testMember, "memberProfile", testProfile);

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
    @DisplayName("피드 생성 성공 - 이벤트 발행 확인")
    void createFeedWithEventSuccess() {
        // given
        Member testMember = Member.createLocalMember(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        MemberProfile testProfile = MemberProfile.create(
                "image.png", testMember, "intro", "job", "company", "city", "edu",
                new ArrayList<>(), "git", "link", "web", "twit"
        );
        ReflectionTestUtils.setField(testMember, "memberProfile", testProfile);
        FeedCreateRequest request = new FeedCreateRequest(TEST_CONTENT, List.of());
        Feed expectedFeed = Feed.of(TEST_CONTENT, testMember);
        when(feedRepository.save(any(Feed.class))).thenReturn(expectedFeed);

        // when
        feedService.createFeed(request, testMember);

        // then
        // 이벤트가 정확히 1번 발행되었는지 확인
        verify(eventPublisher, times(1)).publishEvent(any(FeedCreateEvent.class));
    }

    @Test
    @DisplayName("피드 목록 조회 성공 - Redis 캐시가 있는 경우 DB를 조회하지 않음")
    void getFeedsFromRedisSuccess() {
        // given
        FetchFeedResponse cachedDto = new FetchFeedResponse();
        cachedDto.setFeedId(100L);
        cachedDto.setContent("캐시된 피드");

        // redisTemplate.opsForList().range(...)가 데이터를 반환하도록 설정
        ListOperations listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(anyString(), anyLong(), anyLong())).thenReturn(List.of(cachedDto));

        // when
        FetchFeedsResponse result = feedService.getFeeds(PageRequest.of(0, 10), 1L);

        // then
        assertThat(result.getFeeds()).hasSize(1);
        assertThat(result.getFeeds().get(0).getContent()).isEqualTo("캐시된 피드");

        // 핵심: DB 레포지토리는 호출되지 않아야 함!
        verify(feedRepository, never()).findFeeds(any());
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
        Feed existingFeed = spy(Feed.of(TEST_CONTENT, testMember));
        Long feedId = 1L;
        ReflectionTestUtils.setField(existingFeed, "id", feedId);

        when(feedRepository.getById(feedId)).thenReturn(existingFeed);

        // when
        feedService.deleteFeed(existingFeed.getId());

        // then
        verify(existingFeed).softDelete();
        verify(feedCommentService).deleteFeedCommentByFeed(existingFeed.getId());
        verify(feedLikeService).deleteAllByFeedId(existingFeed.getId());
        verify(feedMediaService).deleteAllByFeedId(existingFeed.getId());

    }
}
