package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.ValidatedUrl;

public interface UrlValidator {
    ValidatedUrl validate(String rawUrl) throws UrlValidationException;
}
