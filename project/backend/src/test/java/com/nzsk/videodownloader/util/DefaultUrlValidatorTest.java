package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.service.RedirectResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultUrlValidatorTest {
    @Test
    void validatesFinalRedirectHost() throws Exception {
        RedirectResolver resolver = mock(RedirectResolver.class);
        URI finalUri = URI.create("https://www.bilibili.com/video/BV1");
        when(resolver.resolveFinalUri(URI.create("https://b23.tv/abc"))).thenReturn(finalUri);

        ValidatedUrl result = new DefaultUrlValidator(resolver).validate("https://b23.tv/abc");

        assertEquals(finalUri, result.uri());
        assertEquals("www.bilibili.com", result.host());
    }

    @Test
    void rejectsNonHttpSchemes() {
        RedirectResolver resolver = mock(RedirectResolver.class);

        assertThrows(UrlValidationException.class,
                () -> new DefaultUrlValidator(resolver).validate("file:///secret/video.mp4"));
    }

    @Test
    void rejectsLookalikeFinalHost() throws Exception {
        RedirectResolver resolver = mock(RedirectResolver.class);
        when(resolver.resolveFinalUri(URI.create("https://b23.tv/abc")))
                .thenReturn(URI.create("https://evil-bilibili.com/video"));

        assertThrows(UrlValidationException.class,
                () -> new DefaultUrlValidator(resolver).validate("https://b23.tv/abc"));
    }

    @Test
    void rejectsUnapprovedInitialHostBeforeResolving() {
        RedirectResolver resolver = mock(RedirectResolver.class);

        assertThrows(UrlValidationException.class,
                () -> new DefaultUrlValidator(resolver).validate("https://example.com/video"));
    }
}
