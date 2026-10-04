package com.nzsk.videodownloader.util;

import java.util.Locale;
import java.util.Set;

public final class AllowedHosts {
    private static final Set<String> HOSTS = Set.of(
            "bilibili.com",
            "b23.tv",
            "douyin.com",
            "v.douyin.com",
            "iesdouyin.com"
    );

    private AllowedHosts() {
    }

    public static boolean isAllowed(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        return HOSTS.stream().anyMatch(
                allowed -> normalized.equals(allowed) || normalized.endsWith("." + allowed)
        );
    }
}
