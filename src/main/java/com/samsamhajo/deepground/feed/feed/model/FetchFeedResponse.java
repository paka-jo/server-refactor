package com.samsamhajo.deepground.feed.feed.model.v2;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class FetchFeedResponse {

    private UUID publicId;
    private UUID profilePublicId;
    private Long feedId;
    private String memberName;
    private String content;
    private int likeCount;

    private boolean isLiked;

    private int commentCount;
    private int shareCount;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private List<String> mediaUrls;

    private FetchFeedResponse(UUID publicId, UUID profilePublicId, Long feedId, String memberName,
                              String content, int likeCount, int commentCount, int shareCount,
                              String profileImageUrl, LocalDateTime createdAt){
        this.publicId = publicId;
        this.profilePublicId = profilePublicId;
        this.feedId = feedId;
        this.memberName = memberName;
        this.content = content;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.shareCount = shareCount;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
    }

    private FetchFeedResponse(UUID publicId, UUID profilePublicId, Long feedId, String memberName,
                              String content, int likeCount, int commentCount, int shareCount,
                              String profileImageUrl, LocalDateTime createdAt, List<String> mediaUrls){
        this.publicId = publicId;
        this.profilePublicId = profilePublicId;
        this.feedId = feedId;
        this.memberName = memberName;
        this.content = content;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.shareCount = shareCount;
        this.profileImageUrl = profileImageUrl;
        this.createdAt = createdAt;
        this.mediaUrls = mediaUrls;
    }
}
