package com.samsamhajo.deepground.feed.feed.service;

import com.samsamhajo.deepground.feed.feed.entity.Feed;
import com.samsamhajo.deepground.feed.feed.entity.FeedMedia;
import com.samsamhajo.deepground.feed.feed.model.FeedMediaResponse;
import com.samsamhajo.deepground.feed.feed.model.FeedUpdateRequest;
import com.samsamhajo.deepground.feed.feed.repository.FeedMediaRepository;
import com.samsamhajo.deepground.global.upload.S3Uploader;
import com.samsamhajo.deepground.media.MediaUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static com.samsamhajo.deepground.media.MediaUtils.getExtension;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedMediaService {

    private final FeedMediaRepository feedMediaRepository;
    private final S3Uploader s3Uploader;

    @Transactional
    public List<String> createFeedMedia(Feed feed, List<MultipartFile> images) {
        if (CollectionUtils.isEmpty(images)) return List.of();

        List<FeedMedia> mediaEntities = images.stream()
                .map(image -> {
                    String url = s3Uploader.upload(image, "feed-media");
                    String extension = getExtension(image.getOriginalFilename());
                    return FeedMedia.of(url, extension, feed);
                })
                .toList();

        List<FeedMedia> savedMedia = feedMediaRepository.saveAll(mediaEntities);

        return savedMedia.stream()
                .map(FeedMedia::getMediaUrl)
                .toList();
    }

    public List<FeedMedia> findAllByFeed(Feed feed) {
        return feedMediaRepository.findAllByFeedId(feed.getId());
    }
    
    @Transactional
    public void deleteAllByFeedId(Long feedId) {
        feedMediaRepository.softDeleteAllByFeedId(feedId);
    }

    public void updateFeedMedia(Feed feed, FeedUpdateRequest request) {
        // 피드에 연결된 모든 미디어 삭제
        deleteAllByFeedId(feed.getId());

        // 새 미디어 추가
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            createFeedMedia(feed, request.getImages());
        }
    }

    public List<String> findAllMediaUrlsByFeedId(Long feedId) {
        return feedMediaRepository.findAllByFeedId(feedId)
                .stream()
                .map(FeedMedia::getMediaUrl)
                .toList();
    }

    public List<Long> findAllMediaIdsByFeedId(Long feedId) {
        return feedMediaRepository.findAllByFeedId(feedId)
                .stream()
                .map(FeedMedia::getId)
                .toList();
    }
}