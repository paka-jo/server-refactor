package com.samsamhajo.deepground.feed.feed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsamhajo.deepground.feed.feed.model.FeedCreateRequest;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedsResponse;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.global.config.S3Config;
import com.samsamhajo.deepground.global.upload.S3Uploader;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.entity.MemberProfile;
import com.samsamhajo.deepground.member.entity.Role;
import com.samsamhajo.deepground.member.repository.MemberRepository;
import com.samsamhajo.deepground.member.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
public class FeedCacheTest {

    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FeedService feedService;
    @Autowired private FeedRepository feedRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProfileRepository profileRepository;

    @MockBean protected S3Config s3Config;
    @MockBean protected S3Uploader s3Uploader;

    private static final String TEST_KEY = "feed:cache:test";
    private static final String CACHE_KEY = "feed:recent:20";

    private Member member;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(TEST_KEY);
        redisTemplate.delete(CACHE_KEY);
        feedRepository.deleteAll();
        memberRepository.deleteAll();

        member = Member.createLocalMember("cache@test.com", "password123", "캐시테스터");
        member.verify();
        member.updateRole(Role.ROLE_USER);
        memberRepository.save(member);

        MemberProfile profile = MemberProfile.create(
                "image.png", member, "intro", "job", "company", "city", "edu",
                new ArrayList<>(), "git", "link", "web", "twit"
        );
        profileRepository.save(profile);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(TEST_KEY);
        redisTemplate.delete(CACHE_KEY);
        feedRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("FetchFeedResponse - Redis 저장 후 objectMapper.convertValue()로 역직렬화 성공")
    void redisCacheSerializationTest() {
        // given
        FetchFeedResponse response = new FetchFeedResponse();
        response.setFeedId(1L);
        response.setContent("테스트 피드");
        response.setMemberName("테스터");
        response.setPublicId(UUID.randomUUID());
        response.setCreatedAt(LocalDateTime.now());
        response.setMediaUrls(List.of("http://example.com/image.jpg"));

        // when - Redis에 쓰기
        redisTemplate.opsForList().leftPush(TEST_KEY, response);

        // then - Redis에서 읽어 convertValue()로 변환 (FeedService.fetchFromRedis() 동일 방식)
        List<Object> cached = redisTemplate.opsForList().range(TEST_KEY, 0, -1);
        assertThat(cached).isNotNull().hasSize(1);

        Object rawValue = cached.get(0);
        assertThatCode(() -> {
            FetchFeedResponse result = objectMapper.convertValue(rawValue, FetchFeedResponse.class);
            assertThat(result.getFeedId()).isEqualTo(1L);
            assertThat(result.getContent()).isEqualTo("테스트 피드");
            assertThat(result.getMemberName()).isEqualTo("테스터");
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("피드 생성 시 feed:recent:20 Redis 키에 캐시 적재")
    void feedCreateCachesInRedis() {
        // when
        feedService.createFeed(new FeedCreateRequest("Redis 캐시 테스트 피드", List.of()), member);

        // then - 트랜잭션 커밋 후 @TransactionalEventListener 실행 대기
        Long size = redisTemplate.opsForList().size(CACHE_KEY);
        assertThat(size).isGreaterThan(0);

        List<Object> cached = redisTemplate.opsForList().range(CACHE_KEY, 0, -1);
        FetchFeedResponse cachedFeed = objectMapper.convertValue(cached.get(0), FetchFeedResponse.class);
        assertThat(cachedFeed.getContent()).isEqualTo("Redis 캐시 테스트 피드");
    }

    @Test
    @DisplayName("피드 목록 첫 페이지 조회 시 Redis 캐시에서 응답")
    void getFeedsReadsFromRedis() {
        // given - 피드 생성으로 Redis 캐시 적재
        feedService.createFeed(new FeedCreateRequest("캐시 조회 테스트 피드", List.of()), member);
        assertThat(redisTemplate.opsForList().size(CACHE_KEY)).isGreaterThan(0);

        // DB에서 피드 삭제 - Redis에만 데이터가 남아있는 상태 강제
        feedRepository.deleteAll();

        // when - 첫 페이지 조회
        FetchFeedsResponse response = feedService.getFeeds(PageRequest.of(0, 20), null);

        // then - DB가 아닌 Redis 캐시 데이터로 응답
        assertThat(response.getFeeds()).isNotEmpty();
        assertThat(response.getFeeds().get(0).getContent()).isEqualTo("캐시 조회 테스트 피드");
    }
}
