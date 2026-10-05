package com.nzsk.videodownloader.util;

import java.net.URI;

/**
 * Image URLs returned by the platform API are only fetched when they point at a known media CDN, so a
 * crafted response can never make the application download from an arbitrary host.
 *
 * <p>The allowlist is shared with video media through {@link MediaCdnHosts}.</p>
 */
public final class ImageCdnHosts {
    private ImageCdnHosts() {
    }

    public static boolean isAllowed(URI uri) {
        return MediaCdnHosts.isAllowed(uri);
    }
}
