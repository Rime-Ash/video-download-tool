package com.nzsk.videodownloader.engine;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DouyinPostIdsTest {
    @Test
    void readsIdFromVideoAndNotePaths() {
        assertEquals("7300000000000000000", DouyinPostIds.awemeId(
                URI.create("https://www.douyin.com/video/7300000000000000000")).orElseThrow());
        assertEquals("7300000000000000000", DouyinPostIds.awemeId(
                URI.create("https://www.douyin.com/note/7300000000000000000?previous_page=web_code_link"))
                .orElseThrow());
        assertEquals("7300000000000000000", DouyinPostIds.awemeId(
                URI.create("https://www.iesdouyin.com/share/video/7300000000000000000/")).orElseThrow());
    }

    @Test
    void readsIdFromProfileModalParameters() {
        assertEquals("7300000000000000000", DouyinPostIds.awemeId(URI.create(
                "https://www.douyin.com/user/MS4wLjABAAAAsample?modal_id=7300000000000000000"))
                .orElseThrow());
        assertEquals("7300000000000000000", DouyinPostIds.awemeId(
                URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample?vid=7300000000000000000"))
                .orElseThrow());
    }

    @Test
    void ignoresNonNumericIds() {
        assertTrue(DouyinPostIds.awemeId(
                URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample?modal_id=../evil")).isEmpty());
        assertTrue(DouyinPostIds.awemeId(URI.create("https://www.douyin.com/user/MS4wLjABAAAAsample"))
                .isEmpty());
    }

    @Test
    void detectsImagePostPaths() {
        assertTrue(DouyinPostIds.isImagePostPath(
                URI.create("https://www.douyin.com/note/7300000000000000000")));
        assertFalse(DouyinPostIds.isImagePostPath(
                URI.create("https://www.douyin.com/video/7300000000000000000")));
    }
}
