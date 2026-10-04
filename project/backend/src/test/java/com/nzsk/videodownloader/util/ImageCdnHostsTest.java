package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCdnHostsTest {
    @Test
    void allowsKnownMediaCdns() {
        assertTrue(ImageCdnHosts.isAllowed(
                URI.create("https://p3-pc-sign.douyinpic.com/tos-cn-i-abc.jpeg")));
        assertTrue(ImageCdnHosts.isAllowed(URI.create("https://api.amemv.com/x.jpeg")));
        assertTrue(ImageCdnHosts.isAllowed(URI.create("https://v6-default.365yg.com/x.jpeg")));
        assertTrue(ImageCdnHosts.isAllowed(URI.create("https://v26-web.douyinvod.com/x.mp4")));
    }

    @Test
    void rejectsLookalikesOtherHostsAndUnsafeSchemes() {
        assertFalse(ImageCdnHosts.isAllowed(URI.create("https://evil-douyinpic.com/x.jpeg")));
        assertFalse(ImageCdnHosts.isAllowed(URI.create("https://example.com/x.jpeg")));
        assertFalse(ImageCdnHosts.isAllowed(URI.create("file:///C:/temp/x.jpeg")));
        assertFalse(ImageCdnHosts.isAllowed(URI.create("https://user@p3.douyinpic.com/x.jpeg")));
    }
}
