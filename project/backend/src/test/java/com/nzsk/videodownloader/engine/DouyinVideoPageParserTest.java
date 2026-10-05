package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.model.DouyinVideoInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinVideoPageParserTest {
    private static final String POST_ID = "7300000000000000000";

    @Test
    void readsTitleAuthorCoverAndPlayableAddresses() {
        Optional<DouyinVideoInfo> parsed = DouyinVideoPageParser.parse(renderedPage(), POST_ID);

        DouyinVideoInfo info = parsed.orElseThrow();
        assertEquals(POST_ID, info.postId());
        assertEquals("你这个无可救药的…… #琉璃川椿", info.title());
        assertEquals("小熊裤衩子", info.author());
        assertEquals("https://p3-pc-sign.douyinpic.com/cover~tplv.jpeg?biz_tag=pcweb_cover&x=1",
                info.thumbnailUrl());

        assertEquals(2, info.formats().size(), "封面图片不应被当成可下载视频");
        assertEquals("源 1", info.formats().get(0).id());
        assertTrue(info.formats().get(0).url().startsWith("https://v26-web.douyinvod.com/"));
        assertTrue(info.formats().get(0).url().contains("&br=534"), "HTML 实体需要还原为 &");
        assertEquals("mp4", info.formats().get(0).extension());
        assertEquals("浏览器解析（CDN 直链）", info.formats().get(0).note());

        assertTrue(info.formats().get(1).url().startsWith("https://www.douyin.com/aweme/v1/play/"));
        assertEquals("浏览器解析", info.formats().get(1).note());
    }

    @Test
    void returnsEmptyWhenThePageHasNoPlayableAddress() {
        String coverOnly = """
                <html><head><title>只有封面 - 抖音</title></head><body>
                <source src="https://p3-pc-sign.douyinpic.com/cover.jpeg">
                </body></html>
                """;

        assertTrue(DouyinVideoPageParser.parse(coverOnly, POST_ID).isEmpty());
        assertTrue(DouyinVideoPageParser.parse("", POST_ID).isEmpty());
        assertTrue(DouyinVideoPageParser.parse(renderedPage(), " ").isEmpty());
    }

    @Test
    void rejectsAddressesOutsideTheMediaHostPolicy() {
        assertTrue(DouyinVideoPageParser.parse(renderedPage(), POST_ID, uri -> false).isEmpty());
    }

    @Test
    void fallsBackToTheDocumentTitleWhenTheMetaTagIsMissing() {
        String page = """
                <html><head><title>备用标题 - 抖音</title></head><body>
                <source src="https://v3-web.douyinvod.com/x/video/tos/cn/y/">
                </body></html>
                """;

        DouyinVideoInfo info = DouyinVideoPageParser.parse(page, POST_ID).orElseThrow();
        assertEquals("备用标题", info.title());
        assertEquals(null, info.author());
    }

    /** Mirrors the structure of a page dumped by a headless Chromium browser. */
    private static String renderedPage() {
        return """
                <!DOCTYPE html><html><head>
                <meta name="description" content="你这个无可救药的…… #琉璃川椿 - 小熊裤衩子于20260717发布在抖音，已经收获了1875.8万个喜欢。" data-rh="true">
                <meta name="lark:url:video_title" content="你这个无可救药的…… #琉璃川椿 - 抖音" data-rh="true">
                <meta name="lark:url:video_cover_image_url" content="https://p3-pc-sign.douyinpic.com/cover~tplv.jpeg?biz_tag=pcweb_cover&amp;x=1" data-rh="true">
                <title>你这个无可救药的…… #琉璃川椿 - 抖音</title>
                </head><body>
                <video class="" autoplay=""><source class="" src="https://v26-web.douyinvod.com/a474/video/tos/cn/tos-cn-ve-15/o01/?a=6383&amp;br=534&amp;btag=80000"></source></video>
                <source src='https://www.douyin.com/aweme/v1/play/?aid=6383&amp;video_id=v0d00fg10000'>
                <source src="https://p3-pc-sign.douyinpic.com/cover.jpeg">
                <script src="https://lf-douyin-pc-web.douyinstatic.com/app.js"></script>
                </body></html>
                """;
    }
}
