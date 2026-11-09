package com.linklimiter.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linklimiter.config.AppConfig;
import com.linklimiter.repository.InMemoryNotificationRepository;
import com.linklimiter.repository.InMemoryShortLinkRepository;
import com.linklimiter.repository.InMemoryUserRepository;
import com.linklimiter.service.LinkService;
import com.linklimiter.service.ShortCodeGenerator;
import com.linklimiter.service.ShortLinkParser;
import com.linklimiter.service.UrlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConsoleApplicationIntegrationTest {
  @Test
  void supportsCreateListAndRedirectFlow() {
    AppConfig config =
        new AppConfig("clck.ru/", Duration.ofHours(24), Duration.ofDays(1), 6, 1, 1_000_000);
    LinkService service =
        new LinkService(
            new InMemoryUserRepository(),
            new InMemoryShortLinkRepository(),
            new InMemoryNotificationRepository(),
            new ShortCodeGenerator(),
            new UrlValidator(),
            new ShortLinkParser(config.shortLinkPrefix()),
            config,
            Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

    AtomicReference<URI> openedUrl = new AtomicReference<>();
    BrowserLauncher browserLauncher =
        uri -> {
          openedUrl.set(uri);
          return true;
        };

    String createOutput =
        captureOutput(
            () ->
                runConsole(
                    service,
                    browserLauncher,
                    config,
                    """
                        1

                        https://example.com/page
                        2
                        0
                        """));
    String userId = extract(createOutput, "UUID пользователя: (.+)");
    String shortUrl = extract(createOutput, "Короткая ссылка: (.+)");

    String flowOutput =
        captureOutput(
            () ->
                runConsole(
                    service,
                    browserLauncher,
                    config,
                    """
                        3
                        %s
                        2
                        %s
                        0
                        """
                        .formatted(userId, shortUrl)));

    assertTrue(flowOutput.contains("Ссылки пользователя:"));
    assertTrue(flowOutput.contains("https://example.com/page"));
    assertTrue(flowOutput.contains("Осталось переходов: 1"));
    assertEquals(URI.create("https://example.com/page"), openedUrl.get());
  }

  private void runConsole(
      LinkService service, BrowserLauncher browserLauncher, AppConfig config, String input) {
    ConsoleApplication application =
        new ConsoleApplication(
            service,
            browserLauncher,
            config,
            new Scanner(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8))));
    application.run();
  }

  private String captureOutput(Runnable action) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    try {
      action.run();
      return output.toString(StandardCharsets.UTF_8);
    } finally {
      System.setOut(originalOut);
    }
  }

  private String extract(String text, String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(text);
    assertTrue(matcher.find(), "Не найдено значение по шаблону: " + regex);
    return matcher.group(1).trim();
  }
}
