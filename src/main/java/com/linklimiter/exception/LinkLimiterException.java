package com.linklimiter.exception;

public class LinkLimiterException extends RuntimeException {
  public LinkLimiterException(String message) {
    super(message);
  }

  public LinkLimiterException(String message, Throwable cause) {
    super(message, cause);
  }
}
