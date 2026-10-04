package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.exception.UrlValidationException;

import java.net.URI;

public interface RedirectResolver {
    URI resolveFinalUri(URI initialUri) throws UrlValidationException;
}
