package com.linklimiter.service;

import com.linklimiter.exception.LinkLimiterException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UrlValidator {
    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new LinkLimiterException("URL не может быть пустым");
        }

        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            boolean supportedScheme = scheme != null
                    && ("http".equals(scheme.toLowerCase(Locale.ROOT))
                    || "https".equals(scheme.toLowerCase(Locale.ROOT)));
            if (!supportedScheme || uri.getHost() == null) {
                throw new LinkLimiterException(
                        "Введите абсолютный URL с протоколом http или https"
                );
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new LinkLimiterException("Некорректный URL", exception);
        }
    }
}
