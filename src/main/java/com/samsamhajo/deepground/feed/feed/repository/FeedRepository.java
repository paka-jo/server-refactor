package com.samsamhajo.deepground.feed.feed.repository;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.exception.FeedErrorCode;
import com.samsamhajo.deepground.feed.feed.exception.FeedException;
import com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface FeedRepository extends JpaRepository<Feed, Long>, FeedRepositoryCustom {


    default Feed getById(Long id) {
        return findById(id).orElseThrow(() -> new FeedException(FeedErrorCode.FEED_NOT_FOUND));
    }

    Page<Feed> findAllByMemberId(Pageable pageable, Long memberId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Feed f SET f.likeCount = :count WHERE f.id = :feedId")
    void updateLikeCount(@Param("feedId") Long feedId, @Param("count") Long count);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Feed f " +
            "SET f.commentCount = (" +
            "   SELECT COUNT(fc) " +
            "   FROM FeedComment fc " +
            "   WHERE fc.feed = f) " +
            "WHERE f.id = :feedId")
    void updateCountFeedCommentByFeedId(@Param("feedId") Long feedId);

    @Modifying
    @Query("UPDATE Feed f " +
            "SET f.sharedCount = (" +
            "   SELECT COUNT(sf)" +
            "   FROM SharedFeed sf" +
            "   WHERE sf.feed = f) " +
            "WHERE f.id= :feedId")
    void updateCountFeedSharedById(Long feedId);

    @Query("select new com.samsamhajo.deepground.feed.feed.model.FetchFeedResponse " +
            "(m.publicId , mp.profilePublicId , f.id, m.nickname, f.content, f.likeCount, f.commentCount," +
            "f.sharedCount, mp.profileImage, f.createdAt)" +
            "from Feed f " +
            "join f.member m " +
            "left join m.memberProfile mp " +
            "WHERE f.id = :feedId")
    Optional<FetchFeedResponse> getByIdWithMemberAndProfile(@Param("feedId") Long feedId);
}
