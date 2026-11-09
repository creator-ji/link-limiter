package com.linklimiter.service;

import java.security.SecureRandom;

public final class ShortCodeGenerator {
  private static final char[] ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  private final SecureRandom random;

  public ShortCodeGenerator() {
    this(new SecureRandom());
  }

  ShortCodeGenerator(SecureRandom random) {
    this.random = random;
  }

  public String generate(int length) {
    StringBuilder result = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      result.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
    return result.toString();
  }
}
