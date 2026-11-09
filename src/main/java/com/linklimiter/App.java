package com.linklimiter;

import com.linklimiter.config.AppConfig;
import com.linklimiter.repository.InMemoryNotificationRepository;
import com.linklimiter.repository.InMemoryShortLinkRepository;
import com.linklimiter.repository.InMemoryUserRepository;
import com.linklimiter.service.ExpiredLinkCleaner;
import com.linklimiter.service.LinkService;
import com.linklimiter.service.ShortCodeGenerator;
import com.linklimiter.service.ShortLinkParser;
import com.linklimiter.service.UrlValidator;
import com.linklimiter.ui.ConsoleApplication;
import com.linklimiter.ui.DesktopBrowserLauncher;
import java.time.Clock;
import java.util.Scanner;

public final class App {
  private App() {}

  public static void main(String[] args) {
    AppConfig config = AppConfig.defaultConfig();
    System.out.printf(
        "Конфигурация загружена: TTL=%s, очистка каждые %s, код=%d символов%n",
        config.linkLifetime(), config.cleanupInterval(), config.shortCodeLength());
    LinkService linkService =
        new LinkService(
            new InMemoryUserRepository(),
            new InMemoryShortLinkRepository(),
            new InMemoryNotificationRepository(),
            new ShortCodeGenerator(),
            new UrlValidator(),
            new ShortLinkParser(config.shortLinkPrefix()),
            config,
            Clock.systemUTC());
    try (ExpiredLinkCleaner cleaner =
        new ExpiredLinkCleaner(linkService, config.cleanupInterval())) {
      cleaner.start();
      ConsoleApplication application =
          new ConsoleApplication(
              linkService, new DesktopBrowserLauncher(), config, new Scanner(System.in));
      application.run();
    }
  }
}
