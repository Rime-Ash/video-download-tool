package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinImagePostParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesImagePostAndPrefersNonWebpUrl() throws Exception {
        Optional<ImagePostInfo> parsed = DouyinImagePostParser.parse("""
                {
                  "status_code": 0,
                  "aweme_detail": {
                    "aweme_id": "7300000000000000000",
                    "desc": "测试图集标题",
                    "aweme_type": 68,
                    "author": { "nickname": "测试作者" },
                    "images": [
                      { "width": 1920, "height": 1080, "url_list": [
                          "https://p3-pc-sign.douyinpic.com/a~tplv-dy-aweme-images:q75.webp?x=1",
                          "https://p3-pc-sign.douyinpic.com/a~tplv-dy-aweme-images:q75.jpeg?x=1" ],
                        "video": {
                          "duration": 3434, "width": 1280, "height": 720,
                          "play_addr": { "url_list": [ "https://v26-web.douyinvod.com/a.mp4" ] },
                          "play_addr_h264": { "url_list": [ "https://v26-web.douyinvod.com/a_h264.mp4" ] } } },
                      { "width": 1080, "height": 1920, "url_list": [
                          "https://p9-pc-sign.douyinpic.com/b.jpeg" ] }
                    ]
                  }
                }
                """, objectMapper);

        assertTrue(parsed.isPresent());
        ImagePostInfo info = parsed.orElseThrow();
        assertEquals("测试图集标题", info.title());
        assertEquals("测试作者", info.author());
        assertEquals(2, info.imageCount());
        ImageInfo first = info.images().get(0);
        assertTrue(first.url().contains(".jpeg"), "应优先选择非 webp 地址");
        assertEquals("jpg", first.extension());
        assertEquals("1920x1080", first.resolution());
        assertTrue(first.hasLivePhoto(), "第一张应识别为动图");
        assertEquals("https://v26-web.douyinvod.com/a_h264.mp4", first.livePhoto().videoUrl());
        assertEquals(3434, first.livePhoto().durationMillis());
        assertEquals("3.4 秒", first.livePhoto().durationText());
        assertFalse(info.images().get(1).hasLivePhoto(), "第二张是静态图");
    }

    @Test
    void ignoresMotionVideoThatIsNotOnTheMediaCdn() throws Exception {
        Optional<ImagePostInfo> parsed = DouyinImagePostParser.parse("""
                {
                  "aweme_detail": {
                    "aweme_id": "7300000000000000000",
                    "aweme_type": 68,
                    "images": [
                      { "url_list": [ "https://p3.douyinpic.com/a.jpeg" ],
                        "video": { "duration": 2000,
                          "play_addr": { "url_list": [ "https://evil.example.com/a.mp4" ] } } }
                    ]
                  }
                }
                """, objectMapper);

        ImageInfo image = parsed.orElseThrow().images().get(0);
        assertTrue(image.url().contains("douyinpic"));
        assertFalse(image.hasLivePhoto());
    }

    @Test
    void dropsImagesThatAreNotOnTheMediaCdn() throws Exception {
        Optional<ImagePostInfo> parsed = DouyinImagePostParser.parse("""
                {
                  "aweme_detail": {
                    "aweme_id": "7300000000000000000",
                    "aweme_type": 68,
                    "images": [
                      { "url_list": [ "https://evil.example.com/x.jpeg" ] },
                      { "url_list": [ "https://p3.douyinpic.com/ok.jpeg" ] }
                    ]
                  }
                }
                """, objectMapper);

        assertEquals(1, parsed.orElseThrow().imageCount());
        assertEquals("https://p3.douyinpic.com/ok.jpeg",
                parsed.orElseThrow().images().get(0).url());
    }

    @Test
    void reportsVideoPostsAsNotAnImagePost() throws Exception {
        assertTrue(DouyinImagePostParser.parse("""
                { "aweme_detail": { "aweme_id": "1", "aweme_type": 0, "images": [] } }
                """, objectMapper).isEmpty());
        assertTrue(DouyinImagePostParser.parse("""
                { "status_code": 0, "aweme_detail": null }
                """, objectMapper).isEmpty());
    }

    @Test
    void rejectsImagePostWithoutUsableImageUrls() {
        assertThrows(ImagePostException.class, () -> DouyinImagePostParser.parse("""
                { "aweme_detail": { "aweme_id": "1", "aweme_type": 68,
                  "images": [ { "url_list": [ "https://evil.example.com/x.jpeg" ] } ] } }
                """, objectMapper));
    }
}
