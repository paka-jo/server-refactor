package com.samsamhajo.deepground.feed.refactor;

import com.samsamhajo.deepground.IntegrationTestSupport;
import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.model.FeedCreateRequest;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.feed.feed.service.FeedLikeService;
import com.samsamhajo.deepground.feed.feed.service.FeedService;
import com.samsamhajo.deepground.global.config.S3Config;
import com.samsamhajo.deepground.global.upload.S3Uploader;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.entity.Role;
import com.samsamhajo.deepground.member.exception.MemberErrorCode;
import com.samsamhajo.deepground.member.exception.MemberException;
import com.samsamhajo.deepground.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class feddLikeServcierRefactorTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FeedLikeService feedLikeService;

    @MockBean
    protected S3Config s3Config;
    @MockBean protected S3Uploader s3Uploader;


    @Test
    @DisplayName("피드 좋아요 동시성 테스트")
    void updateFeedLikeBy100() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        FeedCreateRequest request = new FeedCreateRequest("TEST_CONTENT", List.of());

        Long targetFeedId = 1L;

        List<Member> members = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String email = "test" + i + "@example.com";
            String nickname = "tester" + i;

            Member member = Member.createLocalMember(email, "password123", nickname);

            member.verify();
            member.updateRole(Role.ROLE_USER);

            members.add(member);
        }

        memberRepository.saveAll(members);

        feedService.createFeed(request,members.get(1));

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
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

        long duration = System.currentTimeMillis() - startTime;

        System.out.println("비관적락 피드 좋아요 100명 소요 시간: " + duration + "ms");

        //then
        Feed feed = feedRepository.findById(targetFeedId).orElseThrow();
        assertThat(feed.getLikeCount()).isEqualTo(100);

        // redis 사용시 테스트 코드
        // String count = redisTemplate.opsForValue().get("like:" + targetFeedId);
        // assertThat(Integer.parseInt(count)).isEqualTo(100);

        System.out.println("테스트 완료! 예상 값: 100");
    }
}
