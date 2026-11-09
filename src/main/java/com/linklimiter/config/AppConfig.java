package com.linklimiter.config;

import java.time.Duration;

public record AppConfig(
    String shortLinkPrefix,
    Duration linkLifetime,
    Duration cleanupInterval,
    int shortCodeLength,
    int minClickLimit,
    int maxClickLimit) {
  public static AppConfig defaultConfig() {
    return new ConfigLoader().load();
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
    if (minClickLimit <= 0) {
      throw new IllegalArgumentException("Минимальный лимит переходов должен быть положительным");
    }
    if (maxClickLimit < minClickLimit) {
      throw new IllegalArgumentException(
          "Максимальный лимит переходов не может быть меньше минимального");
    }
  }

  public void validateClickLimit(int clickLimit) {
    if (clickLimit < minClickLimit || clickLimit > maxClickLimit) {
      throw new IllegalArgumentException(
          "Лимит переходов должен быть в диапазоне " + minClickLimit + ".." + maxClickLimit);
    }
  }
}
