package com.linklimiter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linklimiter.exception.LinkLimiterException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class InputValidationTest {
  private final UrlValidator urlValidator = new UrlValidator();
  private final ShortLinkParser shortLinkParser = new ShortLinkParser("clck.ru/");

  @Test
  void validatesHttpAndHttpsUrls() {
    assertEquals(
        URI.create("https://example.com/page"),
        urlValidator.validate(" https://example.com/page "));
    assertEquals(URI.create("http://example.com"), urlValidator.validate("http://example.com"));
    assertThrows(LinkLimiterException.class, () -> urlValidator.validate("example.com"));
    assertThrows(LinkLimiterException.class, () -> urlValidator.validate("ftp://example.com"));
  }

  @Test
  void acceptsCodeOrConfiguredShortLinkOnly() {
    assertEquals("Ab12Cd", shortLinkParser.parse("Ab12Cd"));
    assertEquals("Ab12Cd", shortLinkParser.parse("clck.ru/Ab12Cd"));
    assertThrows(
        LinkLimiterException.class, () -> shortLinkParser.parse("https://other.example/Ab12Cd"));
    assertThrows(LinkLimiterException.class, () -> shortLinkParser.parse("bad-code"));
  }
}
