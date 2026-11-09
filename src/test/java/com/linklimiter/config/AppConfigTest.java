package com.linklimiter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppConfigTest {
  @Test
  void rejectsInvalidDurationsAndShortCodeLength() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("clck.ru/", Duration.ZERO, Duration.ofMinutes(1), 6, 1, 100));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("clck.ru/", Duration.ofHours(1), Duration.ofNanos(1), 6, 1, 100));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AppConfig("clck.ru/", Duration.ofHours(1), Duration.ofMinutes(1), 3, 1, 100));
  }

  @Test
  void loadsConfigurationFromClasspathProperties() {
    AppConfig config = new ConfigLoader().load();

    assertEquals("clck.ru/", config.shortLinkPrefix());
    assertEquals(Duration.ofHours(24), config.linkLifetime());
    assertEquals(Duration.ofMinutes(1), config.cleanupInterval());
    assertEquals(6, config.shortCodeLength());
    assertEquals(1, config.minClickLimit());
    assertEquals(1_000_000, config.maxClickLimit());
  }

  @Test
  void loadsConfigurationFromExternalFile() throws Exception {
    Path tempFile = Files.createTempFile("link-limiter-", ".properties");
    Files.writeString(
        tempFile,
        """
                        short.link.prefix=short.local/
                        link.lifetime.hours=2
                        cleanup.interval.minutes=5
                        short.code.length=8
                        click.limit.min=2
                        click.limit.max=10
                        """);

    try {
      AppConfig config = new ConfigLoader().loadFromFile(tempFile);
      assertEquals("short.local/", config.shortLinkPrefix());
      assertEquals(Duration.ofHours(2), config.linkLifetime());
      assertEquals(Duration.ofMinutes(5), config.cleanupInterval());
      assertEquals(8, config.shortCodeLength());
      assertEquals(2, config.minClickLimit());
      assertEquals(10, config.maxClickLimit());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void rejectsMissingConfigurationKeys() {
    Properties properties = new Properties();
    properties.setProperty("short.link.prefix", "clck.ru/");
    assertThrows(
        IllegalArgumentException.class, () -> new ConfigLoader().fromProperties(properties));
  }
}
