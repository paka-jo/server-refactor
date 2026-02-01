package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedLike;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedLikeServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedLikeRepository feedLikeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, Object> setOperations;

    @InjectMocks
    private FeedLikeService feedLikeService;

    @BeforeEach
    void setUp() {

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("피드 좋아요 증가 성공 (Redis)")
    void feedLikeIncreaseSuccess() {
        // given
        Long feedId = 1L;
        Long memberId = 1L;
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);

        when(valueOperations.setBit(anyString(), eq(memberId), eq(true))).thenReturn(false);

        // when
        feedLikeService.feedLikeIncrease(feedId, member);

        // then
        verify(setOperations).add(eq("feed:likes:dirty"), anyString()); // Dirty Checking 확인

    }

    @Test
    @DisplayName("피드 좋아요 실패 - 이미 좋아요 누름 (Redis)")
    void feedLikeIncreaseFail_AlreadyLiked() {
        // given
        Long feedId = 1L;
        Long memberId = 1L;
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);

        when(valueOperations.setBit(anyString(), eq(memberId), eq(true))).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> feedLikeService.feedLikeIncrease(feedId, member))
                .isInstanceOf(FeedException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.FEED_LIKE_ALREADY_EXISTS);

        verify(feedLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("피드 좋아요 취소 성공 (Redis)")
    void feedLikeDecreaseSuccess() {
        // given
        Long feedId = 1L;
        Long memberId = 1L;


        when(valueOperations.getBit(anyString(), eq(memberId))).thenReturn(true);

        feedLikeService.feedLikeDecrease(feedId, memberId);

        verify(valueOperations).setBit(anyString(), eq(memberId), eq(false));

        verify(setOperations).add("feed:likes:dirty", String.valueOf(feedId));

        verify(feedLikeRepository).deleteByFeedIdAndMemberId(feedId, memberId);
    }

    @Test
    @DisplayName("피드 좋아요 취소 실패 - 좋아요 안 누른 상태 (Redis)")
    void feedLikeDecreaseFail_NotLiked() {
        // given
        Long feedId = 1L;
        Long memberId = 1L;

        when(valueOperations.getBit(anyString(), eq(memberId))).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> feedLikeService.feedLikeDecrease(feedId, memberId))
                .isInstanceOf(FeedException.class)
                .hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.FEED_LIKE_NOT_FOUND);

        verify(feedLikeRepository, never()).deleteByFeedIdAndMemberId(anyLong(), anyLong());
    }
}