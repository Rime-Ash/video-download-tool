package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaCdnHostsTest {
    @Test
    void allowsKnownMediaCdns() {
        assertTrue(MediaCdnHosts.isAllowed(
                URI.create("https://p3-pc-sign.douyinpic.com/tos-cn-i-abc.jpeg")));
        assertTrue(MediaCdnHosts.isAllowed(
                URI.create("https://v26-web.douyinvod.com/abc/video/tos/cn/x/")));
        assertTrue(MediaCdnHosts.isAllowed(
                URI.create("https://www.douyin.com/aweme/v1/play/?video_id=x")));
    }

    @Test
    void rejectsLookalikesOtherHostsAndUnsafeSchemes() {
        assertFalse(MediaCdnHosts.isAllowed(URI.create("https://evil-douyinvod.com/x.mp4")));
        assertFalse(MediaCdnHosts.isAllowed(URI.create("https://example.com/x.mp4")));
        assertFalse(MediaCdnHosts.isAllowed(URI.create("file:///C:/temp/x.mp4")));
        assertFalse(MediaCdnHosts.isAllowed(URI.create("https://user@v26-web.douyinvod.com/x.mp4")));
    }
}
