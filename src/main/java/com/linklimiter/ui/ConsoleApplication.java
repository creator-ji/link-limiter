package com.linklimiter.ui;

import com.linklimiter.config.AppConfig;
import com.linklimiter.exception.LinkLimiterException;
import com.linklimiter.model.ShortLink;
import com.linklimiter.service.CreatedLink;
import com.linklimiter.service.LinkAccess;
import com.linklimiter.service.LinkService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

public final class ConsoleApplication {
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

  private final LinkService linkService;
  private final BrowserLauncher browserLauncher;
  private final AppConfig config;
  private final Scanner scanner;

  public ConsoleApplication(
      LinkService linkService, BrowserLauncher browserLauncher, AppConfig config, Scanner scanner) {
    this.linkService = linkService;
    this.browserLauncher = browserLauncher;
    this.config = config;
    this.scanner = scanner;
  }

  public void run() {
    System.out.println("Сервис сокращения ссылок Link Limiter");
    printHelp();
    boolean running = true;
    while (running) {
      printMenu();
      if (!scanner.hasNextLine()) {
        break;
      }
      String command = scanner.nextLine().trim();
      try {
        switch (command) {
          case "1" -> createLink();
          case "2" -> accessLink();
          case "3" -> showUserLinks();
          case "4" -> deleteLink();
          case "5" -> updateClickLimit();
          case "h", "help", "?" -> printHelp();
          case "0" -> running = false;
          default -> System.out.println("Неизвестная команда. Введите h для справки.");
        }
      } catch (LinkLimiterException | IllegalArgumentException exception) {
        System.out.println("Ошибка: " + exception.getMessage());
      } catch (NoSuchElementException | IllegalStateException exception) {
        System.out.println("Ввод завершён.");
        running = false;
      }
      System.out.println();
    }
    System.out.println("Работа приложения завершена.");
  }

  private void printMenu() {
    System.out.println(
        """
                1. Создать короткую ссылку
                2. Перейти по короткой ссылке
                3. Показать мои ссылки
                4. Удалить ссылку
                5. Изменить лимит переходов
                h. Справка
                0. Выйти
                Выберите действие:""");
  }

  private void printHelp() {
    System.out.println(
        """
                Справка:
                - URL: https://example.com/page
                - Лимит переходов: число от %d до %d
                - Срок жизни ссылки задаётся конфигурацией: %s
                - Для управления своими ссылками сохраните и вводите UUID
                - Переход: введите clck.ru/код или только код
                - Уведомления показываются при любом действии с UUID
                """
            .formatted(
                config.minClickLimit(),
                config.maxClickLimit(),
                formatDuration(config.linkLifetime())));
  }

  private void createLink() {
    System.out.println("Введите UUID или оставьте строку пустой для нового пользователя:");
    String rawUserId = scanner.nextLine().trim();
    UUID userId = rawUserId.isEmpty() ? null : parseUuid(rawUserId);
    if (userId != null) {
      showPendingNotifications(userId);
    }

    System.out.println("Введите исходный URL (пример: https://example.com):");
    String originalUrl = scanner.nextLine();
    System.out.printf(
        "Введите лимит переходов (%d..%d):%n", config.minClickLimit(), config.maxClickLimit());
    int clickLimit = parsePositiveInteger(scanner.nextLine());

    CreatedLink created = linkService.createLink(userId, originalUrl, clickLimit);
    System.out.println("UUID пользователя: " + created.userId());
    System.out.println("Сохраните UUID для дальнейшего управления ссылками.");
    System.out.println("Короткая ссылка: " + created.shortUrl());
    System.out.println("Ссылка действует до: " + DATE_FORMATTER.format(created.expiresAt()));
  }

  private void accessLink() {
    System.out.println("Введите короткую ссылку или её код (пример: clck.ru/Ab12Cd):");
    LinkAccess access = linkService.accessLink(scanner.nextLine());

    if (browserLauncher.open(access.originalUrl())) {
      System.out.println("Исходная страница открыта в браузере.");
    } else {
      System.out.println("Не удалось открыть браузер. Перейдите по адресу:");
      System.out.println(access.originalUrl());
    }
    System.out.println("Осталось переходов: " + access.remainingClicks());
  }

  private void showUserLinks() {
    UUID userId = readUserId();
    List<ShortLink> links = linkService.getUserLinks(userId);
    if (links.isEmpty()) {
      System.out.println("У пользователя нет ссылок.");
      return;
    }

    System.out.println("Ссылки пользователя:");
    for (ShortLink link : links) {
      System.out.printf(
          "%s%s -> %s | переходы: %d/%d | действует до: %s | статус: %s%n",
          config.shortLinkPrefix(),
          link.getShortCode(),
          link.getOriginalUrl(),
          link.getClickCount(),
          link.getClickLimit(),
          DATE_FORMATTER.format(link.getExpiresAt()),
          link.getStatus());
    }
  }

  private void deleteLink() {
    UUID userId = readUserId();
    System.out.println("Введите короткую ссылку или её код:");
    linkService.deleteLink(userId, scanner.nextLine());
    System.out.println("Ссылка удалена.");
  }

  private void updateClickLimit() {
    UUID userId = readUserId();
    System.out.println("Введите короткую ссылку или её код:");
    String shortUrlOrCode = scanner.nextLine();
    System.out.printf(
        "Введите новый лимит переходов (%d..%d):%n",
        config.minClickLimit(), config.maxClickLimit());
    int newClickLimit = parsePositiveInteger(scanner.nextLine());
    linkService.updateClickLimit(userId, shortUrlOrCode, newClickLimit);
    System.out.println("Лимит переходов изменён.");
  }

  private UUID readUserId() {
    System.out.println("Введите UUID пользователя:");
    UUID userId = parseUuid(scanner.nextLine().trim());
    showPendingNotifications(userId);
    return userId;
  }

  private void showPendingNotifications(UUID userId) {
    List<String> notifications = linkService.takeNotifications(userId);
    if (!notifications.isEmpty()) {
      System.out.println("Уведомления:");
      notifications.forEach(message -> System.out.println("- " + message));
    }
  }

  private UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new LinkLimiterException("UUID имеет неверный формат");
    }
  }

  private int parsePositiveInteger(String value) {
    try {
      int result = Integer.parseInt(value.trim());
      if (result <= 0) {
        throw new NumberFormatException();
      }
      return result;
    } catch (NumberFormatException exception) {
      throw new LinkLimiterException("Введите положительное целое число");
    }
  }

  private String formatDuration(java.time.Duration duration) {
    long hours = duration.toHours();
    if (hours > 0 && duration.toMinutes() % 60 == 0) {
      return hours + " ч";
    }
    return duration.toString();
  }
}
