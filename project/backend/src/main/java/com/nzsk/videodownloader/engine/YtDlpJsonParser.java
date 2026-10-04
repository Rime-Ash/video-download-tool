package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.model.FormatInfo;
import com.nzsk.videodownloader.model.VideoInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class YtDlpJsonParser {
    private final ObjectMapper objectMapper;

    public YtDlpJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public VideoInfo parse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<FormatInfo> formats = new ArrayList<>();
        JsonNode formatNodes = root.path("formats");
        if (formatNodes.isArray()) {
            for (JsonNode format : formatNodes) {
                formats.add(new FormatInfo(
                        text(format, "format_id"),
                        text(format, "ext"),
                        text(format, "vcodec"),
                        text(format, "acodec"),
                        integer(format, "width"),
                        integer(format, "height"),
                        longValue(format, "filesize"),
                        text(format, "format_note")
                ));
            }
        }
        return new VideoInfo(
                text(root, "title"),
                firstText(root, "uploader", "channel", "creator"),
                longValue(root, "duration"),
                text(root, "thumbnail"),
                formats
        );
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node, name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || !value.canConvertToInt() ? null : value.intValue();
    }

    private static Long longValue(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || !value.isNumber() ? null : value.longValue();
    }
}
