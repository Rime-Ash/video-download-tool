package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.exception.UrlValidationException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;

public final class HttpRedirectResolver implements RedirectResolver {
    private final HttpClient httpClient;
    private final int maxRedirects;

    public HttpRedirectResolver(HttpClient httpClient, int maxRedirects) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects must not be negative");
        }
        this.maxRedirects = maxRedirects;
    }

    @Override
    public URI resolveFinalUri(URI initialUri) throws UrlValidationException {
        URI current = initialUri;
        for (int redirect = 0; redirect <= maxRedirects; redirect++) {
            validateHttpUri(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(10))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            try {
                HttpResponse<Void> response = httpClient.send(request, BodyHandlers.discarding());
                if (response.statusCode() == 405 || response.statusCode() == 501) {
                    response = sendGetWithoutBody(current);
                }
                if (response.statusCode() < 300 || response.statusCode() >= 400) {
                    return current;
                }
                String location = response.headers().firstValue("location").orElseThrow(
                        () -> new UrlValidationException("链接重定向缺少目标地址。"));
                current = current.resolve(location);
            } catch (IOException exception) {
                throw new UrlValidationException("无法解析链接的最终地址，请检查网络连接。", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new UrlValidationException("链接解析被中断。", exception);
            }
        }
        throw new UrlValidationException("链接重定向次数过多，已停止解析。");
    }

    private HttpResponse<Void> sendGetWithoutBody(URI uri)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, BodyHandlers.discarding());
    }

    private void validateHttpUri(URI uri) throws UrlValidationException {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new UrlValidationException("重定向目标不是安全的 HTTP 或 HTTPS 地址。");
        }
    }
}
