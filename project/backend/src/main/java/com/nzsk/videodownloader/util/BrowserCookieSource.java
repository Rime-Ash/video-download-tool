package com.nzsk.videodownloader.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Checks whether the cookies of a local browser profile can be read by yt-dlp.
 *
 * <p>Chromium 127 and later protect the cookie database with app-bound encryption; such profiles cannot be
 * decrypted even when the browser is closed, so the user has to export a cookie file instead. Firefox
 * profiles and older Chromium browsers do not have that limitation.</p>
 */
public final class BrowserCookieSource {
    public record Support(boolean readable, String message) {
    }

    private static final String APP_BOUND_KEY = "app_bound_encrypted_key";

    private BrowserCookieSource() {
    }

    public static Support check(String browserName) {
        return check(browserName,
                AppPaths.localAppDataDirectory(), AppPaths.roamingAppDataDirectory());
    }

    /** Testable variant with explicit data directories. */
    static Support check(String browserName, Path localAppData, Path roamingAppData) {
        if (browserName == null || browserName.isBlank()) {
            return new Support(true, "");
        }
        String browser = browserName.trim().toLowerCase(Locale.ROOT);
        int separator = browser.indexOf(':');
        if (separator > 0) {
            browser = browser.substring(0, separator);
        }
        Optional<Path> dataDirectory = dataDirectory(browser, localAppData, roamingAppData);
        if (dataDirectory.isEmpty()) {
            return new Support(false, "未检测到该浏览器的用户数据目录，可能未安装或使用了自定义路径。");
        }
        if ("firefox".equals(browser)) {
            return new Support(true, "Firefox 的 Cookie 可以直接读取，读取前请完全退出浏览器。");
        }
        Path localState = dataDirectory.get().resolve("Local State");
        if (Files.isRegularFile(localState) && containsAppBoundKey(localState)) {
            return new Support(false, "该浏览器使用应用绑定加密（Chromium 127 及以上），yt-dlp 无法解密其 "
                    + "Cookie：关掉浏览器也一样读不到。请改用浏览器扩展导出 Cookie 文件。");
        }
        return new Support(true, "可以读取，但必须先完全退出浏览器（含后台进程），否则会提示 Cookie 数据库被占用。");
    }

    private static Optional<Path> dataDirectory(String browser, Path localAppData,
                                                Path roamingAppData) {
        return switch (browser) {
            case "edge" -> existing(localAppData.resolve("Microsoft").resolve("Edge")
                    .resolve("User Data"));
            case "chrome" -> existing(localAppData.resolve("Google").resolve("Chrome")
                    .resolve("User Data"));
            case "brave" -> existing(localAppData.resolve("BraveSoftware").resolve("Brave-Browser")
                    .resolve("User Data"));
            case "chromium" -> existing(localAppData.resolve("Chromium").resolve("User Data"));
            case "vivaldi" -> existing(localAppData.resolve("Vivaldi").resolve("User Data"));
            case "opera" -> existing(roamingAppData.resolve("Opera Software").resolve("Opera Stable"));
            case "firefox" -> existing(roamingAppData.resolve("Mozilla").resolve("Firefox"));
            default -> Optional.empty();
        };
    }

    private static Optional<Path> existing(Path path) {
        return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    }

    private static boolean containsAppBoundKey(Path localState) {
        try {
            return Files.readString(localState).contains(APP_BOUND_KEY);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
