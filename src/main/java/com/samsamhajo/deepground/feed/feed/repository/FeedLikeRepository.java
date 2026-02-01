package com.samsamhajo.deepground.feed.feed.repository;

import com.samsamhajo.deepground.feed.feed.entity.FeedLike;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface FeedLikeRepository extends JpaRepository<FeedLike, Long> {

    @Query("select fl.feed.id from FeedLike fl where fl.member.id = :memberId and fl.feed.id in :feedIds and fl.deleted = false")
    List<Long> findLikedFeedIds(@Param("memberId") Long memberId, @Param("feedIds") List<Long> feedIds);

    int countByFeedId(Long feedId);

    boolean existsByFeedIdAndMemberId(Long feedId, Long memberId);

    void deleteAllByFeedId(Long feedId);

    Optional<FeedLike> findByFeedIdAndMemberId(Long feed, Long memberId);

    default FeedLike getByFeedIdAndMemberId(Long feedId, Long memberId){
        return findByFeedIdAndMemberId(feedId, memberId)
                .orElseThrow(()->new FeedException(FeedErrorCode.FEED_LIKE_NOT_FOUND));
    }

    void deleteByFeedIdAndMemberId(Long feedId, Long memberId);
}
