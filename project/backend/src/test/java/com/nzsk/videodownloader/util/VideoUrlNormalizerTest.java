package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.UrlValidationException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VideoUrlNormalizerTest {
    @Test
    void rewritesDouyinProfileLinkThatCarriesAModalVideoId() throws Exception {
        URI input = URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample?from_tab_name=main"
                + "&modal_id=7300000000000000000&vid=7300000000000000000");

        URI normalized = VideoUrlNormalizer.normalize(input);

        assertEquals("https://www.douyin.com/video/7300000000000000000", normalized.toString());
    }

    @Test
    void rewritesDouyinProfileLinkThatOnlyCarriesVid() throws Exception {
        URI input = URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample?vid=7300000000000000000");

        assertEquals("https://www.douyin.com/video/7300000000000000000",
                VideoUrlNormalizer.normalize(input).toString());
    }

    @Test
    void rejectsDouyinProfileLinkWithoutVideoId() {
        URI input = URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample");

        assertThrows(UrlValidationException.class, () -> VideoUrlNormalizer.normalize(input));
    }

    @Test
    void rejectsNonNumericVideoIdInsteadOfRewriting() {
        URI input = URI.create("https://www.douyin.com/user/MS4wLjABAAAA?modal_id=../evil");

        assertThrows(UrlValidationException.class, () -> VideoUrlNormalizer.normalize(input));
    }

    @Test
    void rejectsBilibiliSpaceAndCollectionLinks() {
        assertThrows(UrlValidationException.class, () -> VideoUrlNormalizer.normalize(
                URI.create("https://space.bilibili.com/12345/video")));
        assertThrows(UrlValidationException.class, () -> VideoUrlNormalizer.normalize(
                URI.create("https://www.bilibili.com/medialist/play/ml12345")));
    }

    @Test
    void keepsSingleVideoLinksUnchanged() throws Exception {
        URI douyin = URI.create("https://www.douyin.com/video/7300000000000000000");
        URI bilibili = URI.create("https://www.bilibili.com/video/BV1GJ411x7h7");

        assertEquals(douyin, VideoUrlNormalizer.normalize(douyin));
        assertEquals(bilibili, VideoUrlNormalizer.normalize(bilibili));
    }
}
