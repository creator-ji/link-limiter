package com.linklimiter.service;

import com.linklimiter.config.AppConfig;
import com.linklimiter.exception.LinkLimiterException;
import com.linklimiter.repository.InMemoryNotificationRepository;
import com.linklimiter.repository.InMemoryShortLinkRepository;
import com.linklimiter.repository.InMemoryUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkServiceTest {
    private MutableClock clock;
    private LinkService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        service = new LinkService(
                new InMemoryUserRepository(),
                new InMemoryShortLinkRepository(),
                new InMemoryNotificationRepository(),
                new ShortCodeGenerator(),
                new AppConfig("clck.ru/", Duration.ofHours(24), Duration.ofDays(1), 6),
                clock
        );
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void differentUsersReceiveDifferentLinksForSameUrl() {
        CreatedLink first = service.createLink(null, "https://example.com/page", 5);
        CreatedLink second = service.createLink(null, "https://example.com/page", 5);

        assertNotEquals(first.userId(), second.userId());
        assertNotEquals(first.shortUrl(), second.shortUrl());
    }

    @Test
    void blocksLinkAfterClickLimitIsReached() {
        CreatedLink created = service.createLink(null, "https://example.com", 2);

        assertEquals(1, service.accessLink(created.shortUrl()).remainingClicks());
        assertEquals(0, service.accessLink(created.shortUrl()).remainingClicks());

        LinkLimiterException exception = assertThrows(
                LinkLimiterException.class,
                () -> service.accessLink(created.shortUrl())
        );
        assertTrue(exception.getMessage().contains("Лимит переходов исчерпан"));
        var notifications = service.takeNotifications(created.userId());
        assertEquals(1, notifications.size());
        assertTrue(notifications.get(0).contains("Лимит переходов"));
    }

    @ParameterizedTest(name = "удаляет ссылку после срока жизни {0}")
    @MethodSource("linkLifetimes")
    void removesLinkAtConfiguredLifetimeAndNotifiesOwner(Duration lifetime) {
        service.close();
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
    void scheduledCleanupAutomaticallyRemovesExpiredLink() throws InterruptedException {
        service.close();
        InMemoryShortLinkRepository linkRepository = new InMemoryShortLinkRepository();
        service = new LinkService(
                new InMemoryUserRepository(),
                linkRepository,
                new InMemoryNotificationRepository(),
                new ShortCodeGenerator(),
                new AppConfig(
                        "clck.ru/",
                        Duration.ofHours(1),
                        Duration.ofMillis(10),
                        6
                ),
                clock
        );
        CreatedLink created = service.createLink(null, "https://example.com", 2);
        clock.advance(Duration.ofHours(1));

        Instant waitDeadline = Instant.now().plusSeconds(2);
        while (linkRepository.findByCode(created.link().getShortCode()).isPresent()
                && Instant.now().isBefore(waitDeadline)) {
            Thread.sleep(10);
        }

        assertTrue(linkRepository.findByCode(created.link().getShortCode()).isEmpty());
        assertTrue(service.takeNotifications(created.userId()).get(0).contains("истёк"));
    }

    @Test
    void onlyOwnerCanDeleteLink() {
        CreatedLink ownerLink = service.createLink(null, "https://example.com", 2);
        CreatedLink anotherUser = service.createLink(null, "https://example.org", 2);

        LinkLimiterException exception = assertThrows(
                LinkLimiterException.class,
                () -> service.deleteLink(anotherUser.userId(), ownerLink.shortUrl())
        );
        assertTrue(exception.getMessage().contains("только её владелец"));
    }

    @Test
    void onlyOwnerCanUpdateClickLimit() {
        CreatedLink ownerLink = service.createLink(null, "https://example.com", 2);
        CreatedLink anotherUser = service.createLink(null, "https://example.org", 2);

        service.updateClickLimit(ownerLink.userId(), ownerLink.shortUrl(), 5);
        assertEquals(5, ownerLink.link().getClickLimit());
        assertThrows(
                LinkLimiterException.class,
                () -> service.updateClickLimit(anotherUser.userId(), ownerLink.shortUrl(), 6)
        );
    }

    @Test
    void rejectsInvalidUrlAndNonPositiveLimit() {
        assertThrows(LinkLimiterException.class, () -> service.createLink(null, "example.com", 2));
        assertThrows(
                LinkLimiterException.class,
                () -> service.createLink(null, "https://example.com", 0)
        );
    }

    private LinkService createService(Duration linkLifetime) {
        return new LinkService(
                new InMemoryUserRepository(),
                new InMemoryShortLinkRepository(),
                new InMemoryNotificationRepository(),
                new ShortCodeGenerator(),
                new AppConfig("clck.ru/", linkLifetime, Duration.ofDays(1), 6),
                clock
        );
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
