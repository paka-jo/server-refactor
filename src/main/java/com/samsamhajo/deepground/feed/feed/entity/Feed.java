package com.samsamhajo.deepground.feed.feed.entity;


import com.samsamhajo.deepground.global.BaseEntity;
import com.samsamhajo.deepground.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Feed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_id")
    private Long id;

    @Column(length = 4096, nullable = false)
    private String content;

    @Column(name = "feed_comment_count")
    private int commentCount;

    @Column(name = "feed_like_count")
    private int likeCount;

    @Column(name = "feed_shared_count")
    private int sharedCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private Feed(String content, Member member) {
        this.content = content;
        this.member = member;
    }

    public static Feed of(String content, Member member) {
        return new Feed(content, member);
    }
    
    public void updateContent(String content) {
        this.content = content;
    }
}
