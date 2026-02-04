package com.samsamhajo.deepground.feed.feed.repository;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
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

    boolean existsByFeedIdAndMemberId(Long feedId, Long memberId);

    void deleteAllByFeedId(Long feedId);

    void deleteByFeedIdAndMemberId(Long feedId, Long memberId);

    List<Long> feed(Feed feed);
}
