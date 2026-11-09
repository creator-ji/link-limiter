package com.linklimiter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linklimiter.config.AppConfig;
import com.linklimiter.exception.LinkLimiterException;
import com.linklimiter.repository.InMemoryNotificationRepository;
import com.linklimiter.repository.InMemoryShortLinkRepository;
import com.linklimiter.repository.InMemoryUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LinkServiceTest {
  private MutableClock clock;
  private LinkService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
    service = createService(Duration.ofHours(24));
  }

  @Test
  void differentUsersReceiveDifferentLinksForSameUrl() {
    CreatedLink first = service.createLink(null, "https://example.com/page", 5);
    CreatedLink second = service.createLink(null, "https://example.com/page", 5);

    assertNotEquals(first.userId(), second.userId());
    assertNotEquals(first.shortUrl(), second.shortUrl());
  }

  @Test
  void reusesExistingUserAndRejectsUnknownUser() {
    CreatedLink first = service.createLink(null, "https://example.com/first", 5);
    CreatedLink second = service.createLink(first.userId(), "https://example.com/second", 5);

    assertEquals(first.userId(), second.userId());
    assertThrows(
        LinkLimiterException.class,
        () -> service.createLink(UUID.randomUUID(), "https://example.com", 5));
  }

  @Test
  void blocksLinkAfterClickLimitIsReached() {
    CreatedLink created = service.createLink(null, "https://example.com", 2);

    assertEquals(1, service.accessLink(created.shortUrl()).remainingClicks());
    assertEquals(0, service.accessLink(created.shortUrl()).remainingClicks());

    LinkLimiterException exception =
        assertThrows(LinkLimiterException.class, () -> service.accessLink(created.shortUrl()));
    assertTrue(exception.getMessage().contains("Лимит переходов исчерпан"));
    var notifications = service.takeNotifications(created.userId());
    assertEquals(1, notifications.size());
    assertTrue(notifications.get(0).contains("Лимит переходов"));
    assertThrows(LinkLimiterException.class, () -> service.accessLink(created.shortUrl()));
    assertTrue(service.takeNotifications(created.userId()).isEmpty());
  }

  @ParameterizedTest(name = "удаляет ссылку после срока жизни {0}")
  @MethodSource("linkLifetimes")
  void removesLinkAtConfiguredLifetimeAndNotifiesOwner(Duration lifetime) {
    service = createService(lifetime);
    CreatedLink created = service.createLink(null, "https://example.com", 2);

    clock.advance(lifetime.minusSeconds(1));
    assertEquals(0, service.cleanupExpiredLinks());
    assertEquals(1, service.getUserLinks(created.userId()).size());

    clock.advance(Duration.ofSeconds(1));
    assertEquals(1, service.cleanupExpiredLinks());
    assertTrue(service.getUserLinks(created.userId()).isEmpty());
    assertTrue(service.takeNotifications(created.userId()).get(0).contains("истёк"));
  }

  @Test
  void cleanerDeterministicallyRemovesExpiredLink() {
    CreatedLink created = service.createLink(null, "https://example.com", 2);
    clock.advance(Duration.ofHours(24));

    try (ExpiredLinkCleaner cleaner = new ExpiredLinkCleaner(service, Duration.ofMinutes(1))) {
      assertEquals(1, cleaner.runOnce());
    }
    assertTrue(service.getUserLinks(created.userId()).isEmpty());
    assertTrue(service.takeNotifications(created.userId()).get(0).contains("истёк"));
  }

  @Test
  void accessAtExpirationDeletesLinkAndNotifiesOwner() {
    service = createService(Duration.ofHours(1));
    CreatedLink created = service.createLink(null, "https://example.com", 2);
    clock.advance(Duration.ofHours(1));

    LinkLimiterException exception =
        assertThrows(LinkLimiterException.class, () -> service.accessLink(created.shortUrl()));

    assertTrue(exception.getMessage().contains("истёк"));
    assertTrue(service.getUserLinks(created.userId()).isEmpty());
    assertEquals(1, service.takeNotifications(created.userId()).size());
  }

  @Test
  void onlyOwnerCanDeleteLink() {
    CreatedLink ownerLink = service.createLink(null, "https://example.com", 2);
    CreatedLink anotherUser = service.createLink(null, "https://example.org", 2);

    LinkLimiterException exception =
        assertThrows(
            LinkLimiterException.class,
            () -> service.deleteLink(anotherUser.userId(), ownerLink.shortUrl()));
    assertTrue(exception.getMessage().contains("только её владелец"));
  }

  @Test
  void onlyOwnerCanUpdateClickLimit() {
    CreatedLink ownerLink = service.createLink(null, "https://example.com", 2);
    CreatedLink anotherUser = service.createLink(null, "https://example.org", 2);

    service.updateClickLimit(ownerLink.userId(), ownerLink.shortUrl(), 5);
    assertEquals(5, service.getUserLinks(ownerLink.userId()).get(0).getClickLimit());
    assertThrows(
        LinkLimiterException.class,
        () -> service.updateClickLimit(anotherUser.userId(), ownerLink.shortUrl(), 6));
  }

  @Test
  void newLimitMustExceedCompletedClicks() {
    CreatedLink created = service.createLink(null, "https://example.com", 3);
    service.accessLink(created.shortUrl());

    assertThrows(
        LinkLimiterException.class,
        () -> service.updateClickLimit(created.userId(), created.shortUrl(), 1));
    service.updateClickLimit(created.userId(), created.shortUrl(), 2);
    assertEquals(2, service.getUserLinks(created.userId()).get(0).getClickLimit());
  }

  @Test
  void rejectsInvalidUrlAndNonPositiveLimit() {
    assertThrows(LinkLimiterException.class, () -> service.createLink(null, "example.com", 2));
    assertThrows(
        LinkLimiterException.class, () -> service.createLink(null, "https://example.com", 0));
  }

  private LinkService createService(Duration linkLifetime) {
    return new LinkService(
        new InMemoryUserRepository(),
        new InMemoryShortLinkRepository(),
        new InMemoryNotificationRepository(),
        new ShortCodeGenerator(),
        new UrlValidator(),
        new ShortLinkParser("clck.ru/"),
        new AppConfig("clck.ru/", linkLifetime, Duration.ofDays(1), 6, 1, 1_000_000),
        clock);
  }

  private static Stream<Duration> linkLifetimes() {
    return Stream.of(Duration.ofHours(1), Duration.ofHours(24));
  }

  private static final class MutableClock extends Clock {
    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
