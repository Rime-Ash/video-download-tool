package com.nzsk.videodownloader.model;

import java.net.URI;
import java.util.Objects;

public record ValidatedUrl(URI uri, String host) {
    public ValidatedUrl {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(host, "host");
    }
}
