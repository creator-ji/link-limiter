package com.linklimiter.config;

import java.time.Duration;

public record AppConfig(
        String shortLinkPrefix,
        Duration linkLifetime,
        Duration cleanupInterval,
        int shortCodeLength
) {
    public static AppConfig defaultConfig() {
        return new AppConfig(
                "clck.ru/",
                Duration.ofHours(24),
                Duration.ofMinutes(1),
                6
        );
    }

    public AppConfig {
        if (shortLinkPrefix == null || shortLinkPrefix.isBlank()) {
            throw new IllegalArgumentException("Префикс короткой ссылки не может быть пустым");
        }
        if (linkLifetime == null || linkLifetime.isNegative() || linkLifetime.isZero()) {
            throw new IllegalArgumentException("Время жизни ссылки должно быть положительным");
        }
        if (cleanupInterval == null || cleanupInterval.toMillis() <= 0) {
            throw new IllegalArgumentException("Интервал очистки должен быть не меньше 1 мс");
        }
        if (shortCodeLength < 4) {
            throw new IllegalArgumentException("Длина короткого кода должна быть не меньше 4");
        }
    }
}
