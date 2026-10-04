package com.nzsk.videodownloader.util;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads a Netscape format cookie file and builds a Cookie header for one target host.
 *
 * <p>Cookie values are never logged, printed or copied anywhere; they are only written into the HTTP request
 * header that goes to the platform the user is already logged in to.</p>
 */
public final class NetscapeCookieStore {
    private NetscapeCookieStore() {
    }

    public static Optional<String> cookieHeader(Path cookieFile, URI target) {
        if (cookieFile == null || target == null || target.getHost() == null) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(cookieFile)) {
            return Optional.empty();
        }
        List<String> pairs = new ArrayList<>();
        try {
            for (String rawLine : Files.readAllLines(cookieFile, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty()) {
                    continue;
                }
                boolean httpOnly = line.startsWith("#HttpOnly_");
                if (line.startsWith("#") && !httpOnly) {
                    continue;   // comment or the Netscape header
                }
                if (httpOnly) {
                    line = line.substring("#HttpOnly_".length());
                }
                String[] fields = line.split("\t");
                if (fields.length < 7) {
                    continue;
                }
                String domain = fields[0];
                if (!matches(domain, target.getHost())) {
                    continue;
                }
                if (isExpired(fields[4])) {
                    continue;
                }
                String name = fields[5];
                String value = fields[6];
                if (name.isBlank()) {
                    continue;
                }
                pairs.add(name + "=" + value);
            }
        } catch (IOException exception) {
            return Optional.empty();
        }
        return pairs.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", pairs));
    }

    private static boolean matches(String cookieDomain, String host) {
        String domain = cookieDomain.toLowerCase(Locale.ROOT);
        String requestHost = host.toLowerCase(Locale.ROOT);
        if (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        return requestHost.equals(domain) || requestHost.endsWith("." + domain);
    }

    private static boolean isExpired(String rawExpiry) {
        try {
            long expiry = Long.parseLong(rawExpiry.strip());
            return expiry > 0 && expiry < Instant.now().getEpochSecond();
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
