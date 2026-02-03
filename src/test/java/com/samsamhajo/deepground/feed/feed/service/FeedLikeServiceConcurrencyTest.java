package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.model.FeedCreateRequest;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.global.config.S3Config;
import com.samsamhajo.deepground.global.upload.S3Uploader;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.entity.Role;
import com.samsamhajo.deepground.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class FeedLikeServiceConcurrencyTest {

    @Autowired private FeedService feedService;
    @Autowired private FeedRepository feedRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FeedLikeService feedLikeService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private FeedLikeSyncService feedLikeSyncService;
    @Autowired private FeedLikeScheduler feedLikeScheduler;
    @Autowired private FeedLikeRepository feedLikeRepository;

    @MockBean protected S3Config s3Config;
    @MockBean protected S3Uploader s3Uploader;

    private Long targetFeedId;
    private String redisKey;
    private static final String DIRTY_KEY = "feed:likes:dirty";
    private static final int THREAD_COUNT = 1000;

    private List<Member> members;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {

        feedLikeRepository.deleteAll();
        feedRepository.deleteAll();
        memberRepository.deleteAll();

        redisTemplate.delete(DIRTY_KEY);

        executorService = Executors.newFixedThreadPool(32);

        members = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            Member member = Member.createLocalMember("test" + i + "@example.com", "password123", "tester" + i);
            member.verify();
            member.updateRole(Role.ROLE_USER);
            members.add(member);
        }
        memberRepository.saveAll(members);

        FeedCreateRequest request = new FeedCreateRequest("TEST_CONTENT", List.of());
        feedService.createFeed(request, members.get(0));

        Feed feed = feedRepository.findAll().get(0);
        targetFeedId = feed.getId();

        redisKey = "feed:" + targetFeedId + ":likes:members";

        redisTemplate.delete(redisKey);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
    if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("피드 좋아요 동시성 테스트 (증가)")
    void updateFeedLikeBy100() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Member member = members.get(i);
            executorService.submit(() -> {
                try {
                    feedLikeService.feedLikeIncrease(targetFeedId, member);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Redis 피드 좋아요 1000명 소요 시간: " + duration + "ms");

        Long bitCount = redisTemplate.execute((RedisCallback<Long>) conn ->
                conn.bitCount(redisKey.getBytes())
        );
        assertThat(bitCount).isEqualTo(1000L);

        Feed feedBeforeSync = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(feedBeforeSync.getLikeCount()).isEqualTo(0);

        feedLikeSyncService.syncFeedLikesToDatabase();

        Feed feedAfterSync = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(feedAfterSync.getLikeCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("피드 좋아요 취소 동시성 테스트 (감소)")
    void decreaseFeedLikeBy100() throws InterruptedException {

        for (Member member : members) {
            feedLikeService.feedLikeIncrease(targetFeedId, member);
        }
        feedLikeScheduler.syncFeedLikeCount();

        Feed initialFeed = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(initialFeed.getLikeCount()).isEqualTo(1000);

        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Member member = members.get(i);
            executorService.submit(() -> {
                try {
                    feedLikeService.feedLikeDecrease(targetFeedId, member.getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Redis 좋아요 취소 1000명 소요 시간: " + duration + "ms");

        Long bitCount = redisTemplate.execute((RedisCallback<Long>) conn ->
                conn.bitCount(redisKey.getBytes())
        );
        assertThat(bitCount).isEqualTo(0L);

        long feedLikeCount = feedLikeRepository.count();
        assertThat(feedLikeCount).isEqualTo(0);

        Feed feedBeforeSync = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(feedBeforeSync.getLikeCount()).isEqualTo(1000);

        feedLikeScheduler.syncFeedLikeCount();

        Feed feedAfterSync = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(feedAfterSync.getLikeCount()).isEqualTo(0);
    }
}