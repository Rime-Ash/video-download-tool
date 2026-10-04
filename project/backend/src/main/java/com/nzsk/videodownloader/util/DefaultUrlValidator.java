package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.service.RedirectResolver;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

public final class DefaultUrlValidator implements UrlValidator {
    private final RedirectResolver redirectResolver;

    public DefaultUrlValidator(RedirectResolver redirectResolver) {
        this.redirectResolver = Objects.requireNonNull(redirectResolver, "redirectResolver");
    }

    @Override
    public ValidatedUrl validate(String rawUrl) throws UrlValidationException {
        URI initialUri = parseAndValidateScheme(rawUrl);
        URI finalUri = VideoUrlNormalizer.normalize(redirectResolver.resolveFinalUri(initialUri));
        validateScheme(finalUri);

        String host = finalUri.getHost();
        if (!AllowedHosts.isAllowed(host)) {
            throw new UrlValidationException("该链接的最终域名不在允许的站点范围内。");
        }
        return new ValidatedUrl(finalUri, host.toLowerCase(Locale.ROOT));
    }

    private URI parseAndValidateScheme(String rawUrl) throws UrlValidationException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new UrlValidationException("视频链接不能为空。");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            validateScheme(uri);
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                throw new UrlValidationException("链接格式无效或包含不支持的用户信息。");
            }
            if (!AllowedHosts.isAllowed(uri.getHost())) {
                throw new UrlValidationException("该链接的域名不在允许的站点范围内。");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new UrlValidationException("视频链接格式无效。", exception);
        }
    }

    private void validateScheme(URI uri) throws UrlValidationException {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new UrlValidationException("仅支持 HTTP 或 HTTPS 视频链接。");
        }
    }
}
