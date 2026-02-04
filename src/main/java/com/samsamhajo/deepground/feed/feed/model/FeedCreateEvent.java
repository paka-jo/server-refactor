package com.samsamhajo.deepground.feed.feed.model;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.member.entity.Member;

import java.util.List;

public record FeedCreateEvent(Feed feed,
                              Member member,
                              List<String> mediaUrls) {
}
