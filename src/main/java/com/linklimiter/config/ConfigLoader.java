package com.linklimiter.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

public final class ConfigLoader {
  private static final String CLASSPATH_CONFIG = "application.properties";

  public AppConfig load() {
    Properties properties = loadClasspathProperties();
    return fromProperties(properties);
  }

  public AppConfig loadFromFile(Path path) {
    Objects.requireNonNull(path, "Путь к конфигурации не может быть null");
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(path)) {
      properties.load(reader);
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось прочитать конфигурацию: " + path, exception);
    }
    return fromProperties(properties);
  }

  public AppConfig fromProperties(Properties properties) {
    Objects.requireNonNull(properties, "Свойства конфигурации не могут быть null");
    return new AppConfig(
        required(properties, "short.link.prefix"),
        Duration.ofHours(positiveLong(properties, "link.lifetime.hours")),
        Duration.ofMinutes(positiveLong(properties, "cleanup.interval.minutes")),
        positiveInt(properties, "short.code.length"),
        positiveInt(properties, "click.limit.min"),
        positiveInt(properties, "click.limit.max"));
  }

  private Properties loadClasspathProperties() {
    try (InputStream inputStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(CLASSPATH_CONFIG)) {
      if (inputStream == null) {
        throw new IllegalStateException(
            "Файл конфигурации " + CLASSPATH_CONFIG + " не найден в classpath");
      }
      Properties properties = new Properties();
      properties.load(inputStream);
      return properties;
    } catch (IOException exception) {
      throw new IllegalStateException("Не удалось загрузить " + CLASSPATH_CONFIG, exception);
    }
  }

  private String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Отсутствует параметр конфигурации: " + key);
    }
    return value.trim();
  }

  private int positiveInt(Properties properties, String key) {
    try {
      int value = Integer.parseInt(required(properties, key));
      if (value <= 0) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Параметр " + key + " должен быть положительным целым числом");
    }
  }

  private long positiveLong(Properties properties, String key) {
    try {
      long value = Long.parseLong(required(properties, key));
      if (value <= 0) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Параметр " + key + " должен быть положительным целым числом");
    }
  }
}
