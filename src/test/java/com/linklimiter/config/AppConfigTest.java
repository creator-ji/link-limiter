package com.linklimiter.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
    @Test
    void rejectsInvalidDurationsAndShortCodeLength() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig("clck.ru/", Duration.ZERO, Duration.ofMinutes(1), 6)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig(
                        "clck.ru/",
                        Duration.ofHours(1),
                        Duration.ofNanos(1),
                        6
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig(
                        "clck.ru/",
                        Duration.ofHours(1),
                        Duration.ofMinutes(1),
                        3
                )
        );
    }
}
