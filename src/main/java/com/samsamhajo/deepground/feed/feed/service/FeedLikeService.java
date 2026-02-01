package com.samsamhajo.deepground.feed.feed.service;


import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedLike;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.repository.FeedLikeRepository;
import com.samsamhajo.deepground.feed.feed.repository.FeedRepository;
import com.samsamhajo.deepground.member.entity.Member;
import com.samsamhajo.deepground.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedLikeService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;

    @Transactional
    public void feedLikeIncrease(Long feedId, Member member) {

        Feed feed = feedRepository.findByIdWithLock(feedId)
                .orElseThrow(()-> new FeedException(FeedErrorCode.FEED_NOT_FOUND));

        increaseValidate(feedId, member.getId());

        FeedLike feedLike = FeedLike.of(feed, member);

        feedLikeRepository.save(feedLike);

        updateCountFeedLikeByFeedId(feedId);
    }

    @Transactional
    public void feedLikeDecrease(Long feedId, Long memberId) {
        // 이미 0 이거나 음수인 경우 취소하려 할 때, 예외 발생 
        decreaseValidate(feedId);

        FeedLike feedLike = feedLikeRepository.getByFeedIdAndMemberId(feedId, memberId);

        feedLikeRepository.delete(feedLike);

        updateCountFeedLikeByFeedId(feedId);
    }

    public int countFeedLikeByFeedId(Long feedId) {
        return feedLikeRepository.countByFeedId(feedId);
    }

    private void updateCountFeedLikeByFeedId(Long feedId) {
        feedRepository.updateCountFeedLikeByFeedId(feedId);
    }

    public void deleteAllByFeedId(Long feedId) {
        feedLikeRepository.deleteAllByFeedId(feedId);
    }

    public boolean isLiked(Long feedId, Long memberId) {
        return feedLikeRepository.existsByFeedIdAndMemberId(feedId, memberId);
    }

    private void decreaseValidate(Long feedId) {
        if(countFeedLikeByFeedId(feedId) <= 0){
            throw new FeedException(FeedErrorCode.FEED_LIKE_MINUS_NOT_ALLOWED);
        }
    }

    private void increaseValidate(Long feedId, Long memberId) {
        if (feedLikeRepository.existsByFeedIdAndMemberId(feedId, memberId)) {
            throw new FeedException(FeedErrorCode.FEED_LIKE_ALREADY_EXISTS);
        }
    }
}
