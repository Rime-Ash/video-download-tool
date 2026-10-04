package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class YtDlpJsonParserTest {
    @Test
    void parsesVideoMetadataAndFormats() throws Exception {
        String json = """
                {
                  "title": "Sample video",
                  "uploader": "Sample author",
                  "duration": 95,
                  "thumbnail": "https://example.test/thumb.jpg",
                  "formats": [
                    {
                      "format_id": "137",
                      "ext": "mp4",
                      "vcodec": "avc1",
                      "acodec": "none",
                      "width": 1920,
                      "height": 1080,
                      "filesize": 123456,
                      "format_note": "1080p"
                    }
                  ]
                }
                """;

        var result = new YtDlpJsonParser(new ObjectMapper()).parse(json);

        assertEquals("Sample video", result.title());
        assertEquals("Sample author", result.uploader());
        assertEquals(95L, result.durationSeconds());
        assertNotNull(result.formats());
        assertEquals(1, result.formats().size());
        assertEquals(1080, result.formats().get(0).height());
    }
}
