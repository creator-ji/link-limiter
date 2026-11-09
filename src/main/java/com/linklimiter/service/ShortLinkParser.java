package com.linklimiter.service;

import com.linklimiter.exception.LinkLimiterException;
import java.util.regex.Pattern;

public final class ShortLinkParser {
  private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9]+");

  private final String shortLinkPrefix;

  public ShortLinkParser(String shortLinkPrefix) {
    if (shortLinkPrefix == null || shortLinkPrefix.isBlank()) {
      throw new IllegalArgumentException("Префикс короткой ссылки не может быть пустым");
    }
    this.shortLinkPrefix = shortLinkPrefix;
  }

  public String parse(String shortUrlOrCode) {
    if (shortUrlOrCode == null || shortUrlOrCode.isBlank()) {
      throw new LinkLimiterException("Короткая ссылка не может быть пустой");
    }

    String value = shortUrlOrCode.trim();
    if (value.startsWith(shortLinkPrefix)) {
      value = value.substring(shortLinkPrefix.length());
    } else if (value.contains("/")) {
      throw new LinkLimiterException("Короткая ссылка имеет неверный формат");
    }

    if (value.isBlank() || !CODE_PATTERN.matcher(value).matches()) {
      throw new LinkLimiterException("Короткая ссылка имеет неверный формат");
    }
    return value;
  }
}
