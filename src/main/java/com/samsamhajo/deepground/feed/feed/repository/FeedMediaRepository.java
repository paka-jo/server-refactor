package com.samsamhajo.deepground.feed.feed.repository;

import com.samsamhajo.deepground.feed.feed.entity.FeedMedia;
import com.samsamhajo.deepground.media.MediaErrorCode;
import com.samsamhajo.deepground.media.MediaException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface FeedMediaRepository extends JpaRepository<FeedMedia, Long> {

    List<FeedMedia> findByFeedIdIn(List<Long> feedIds);

    List<FeedMedia> findAllByFeedId(Long feedId);
    
    void deleteAllByFeedId(Long feedId);
    
    default FeedMedia getById(Long feedMediaId){
        return findById(feedMediaId)
                .orElseThrow(() -> new MediaException(MediaErrorCode.MEDIA_NOT_FOUND,feedMediaId));
    }

    @Modifying(clearAutomatically = true)
    @Query("UPDATE FeedMedia fm SET fm.deleted = true WHERE fm.feed.id = :feedId")
    void softDeleteAllByFeedId(@Param("feedId") Long feedId);
}

