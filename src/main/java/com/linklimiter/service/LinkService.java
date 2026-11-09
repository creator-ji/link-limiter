package com.linklimiter.service;

import com.linklimiter.config.AppConfig;
import com.linklimiter.exception.LinkLimiterException;
import com.linklimiter.model.LinkStatus;
import com.linklimiter.model.ShortLink;
import com.linklimiter.model.User;
import com.linklimiter.repository.NotificationRepository;
import com.linklimiter.repository.ShortLinkRepository;
import com.linklimiter.repository.UserRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LinkService implements AutoCloseable {
    private final UserRepository userRepository;
    private final ShortLinkRepository linkRepository;
    private final NotificationRepository notificationRepository;
    private final ShortCodeGenerator codeGenerator;
    private final AppConfig config;
    private final Clock clock;
    private final ScheduledExecutorService cleanupExecutor;

    public LinkService(
            UserRepository userRepository,
            ShortLinkRepository linkRepository,
            NotificationRepository notificationRepository,
            ShortCodeGenerator codeGenerator,
            AppConfig config,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.linkRepository = linkRepository;
        this.notificationRepository = notificationRepository;
        this.codeGenerator = codeGenerator;
        this.config = config;
        this.clock = clock;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "expired-links-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = config.cleanupInterval().toMillis();
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupExpiredLinksSafely,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized CreatedLink createLink(UUID requestedUserId, String rawUrl, int clickLimit) {
        if (clickLimit <= 0) {
            throw new LinkLimiterException("Лимит переходов должен быть положительным числом");
        }

        URI originalUrl = parseAndValidateUrl(rawUrl);
        User user = resolveUser(requestedUserId);
        String shortCode = generateUniqueCode();
        Instant createdAt = clock.instant();
        ShortLink link = new ShortLink(
                UUID.randomUUID(),
                user.id(),
                originalUrl,
                shortCode,
                clickLimit,
                createdAt,
                createdAt.plus(config.linkLifetime())
        );
        linkRepository.save(link);
        return new CreatedLink(user.id(), config.shortLinkPrefix() + shortCode, link);
    }

    public synchronized LinkAccess accessLink(String shortUrlOrCode) {
        String shortCode = extractShortCode(shortUrlOrCode);
        ShortLink link = linkRepository.findByCode(shortCode)
                .orElseThrow(() -> new LinkLimiterException("Короткая ссылка не найдена"));

        if (link.isExpiredAt(clock.instant())) {
            expireAndDelete(link);
            throw new LinkLimiterException("Срок действия ссылки истёк. Создайте новую ссылку");
        }
        if (link.getStatus() == LinkStatus.LIMIT_REACHED) {
            throw new LinkLimiterException("Лимит переходов исчерпан. Ссылка недоступна");
        }
        if (link.getStatus() != LinkStatus.ACTIVE) {
            throw new LinkLimiterException("Ссылка недоступна");
        }

        link.registerClick();
        if (link.getStatus() == LinkStatus.LIMIT_REACHED) {
            notificationRepository.add(
                    link.getOwnerId(),
                    "Лимит переходов для ссылки "
                            + config.shortLinkPrefix() + link.getShortCode()
                            + " исчерпан"
            );
        }
        return new LinkAccess(
                link.getOriginalUrl(),
                Math.max(0, link.getClickLimit() - link.getClickCount())
        );
    }

    public synchronized List<ShortLink> getUserLinks(UUID userId) {
        requireExistingUser(userId);
        cleanupExpiredLinks();
        return linkRepository.findByOwnerId(userId).stream()
                .sorted(Comparator.comparing(ShortLink::getCreatedAt))
                .toList();
    }

    public synchronized void deleteLink(UUID userId, String shortUrlOrCode) {
        requireExistingUser(userId);
        String shortCode = extractShortCode(shortUrlOrCode);
        ShortLink link = requireOwnedLink(userId, shortCode);
        link.markDeleted();
        linkRepository.delete(link);
    }

    public synchronized void updateClickLimit(
            UUID userId,
            String shortUrlOrCode,
            int newClickLimit
    ) {
        requireExistingUser(userId);
        if (newClickLimit <= 0) {
            throw new LinkLimiterException("Лимит переходов должен быть положительным числом");
        }
        String shortCode = extractShortCode(shortUrlOrCode);
        ShortLink link = requireOwnedLink(userId, shortCode);
        if (link.isExpiredAt(clock.instant())) {
            expireAndDelete(link);
            throw new LinkLimiterException("Срок действия ссылки истёк");
        }
        try {
            link.updateClickLimit(newClickLimit);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new LinkLimiterException(exception.getMessage());
        }
    }

    public synchronized List<String> takeNotifications(UUID userId) {
        requireExistingUser(userId);
        cleanupExpiredLinks();
        return notificationRepository.takeAll(userId);
    }

    public synchronized int cleanupExpiredLinks() {
        int deletedCount = 0;
        Instant now = clock.instant();
        for (ShortLink link : linkRepository.findAll()) {
            if (link.isExpiredAt(now)) {
                expireAndDelete(link);
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private void cleanupExpiredLinksSafely() {
        try {
            cleanupExpiredLinks();
        } catch (RuntimeException exception) {
            System.err.println("Не удалось очистить просроченные ссылки: " + exception.getMessage());
        }
    }

    private void expireAndDelete(ShortLink link) {
        link.markExpired();
        notificationRepository.add(
                link.getOwnerId(),
                "Срок действия ссылки "
                        + config.shortLinkPrefix() + link.getShortCode()
                        + " истёк. Создайте новую ссылку"
        );
        linkRepository.delete(link);
    }

    private User resolveUser(UUID requestedUserId) {
        if (requestedUserId == null) {
            return userRepository.save(new User(UUID.randomUUID()));
        }
        return userRepository.findById(requestedUserId)
                .orElseThrow(() -> new LinkLimiterException("Пользователь с таким UUID не найден"));
    }

    private void requireExistingUser(UUID userId) {
        if (userId == null || userRepository.findById(userId).isEmpty()) {
            throw new LinkLimiterException("Пользователь с таким UUID не найден");
        }
    }

    private ShortLink requireOwnedLink(UUID userId, String shortCode) {
        ShortLink link = linkRepository.findByCode(shortCode)
                .orElseThrow(() -> new LinkLimiterException("Короткая ссылка не найдена"));
        if (!link.getOwnerId().equals(userId)) {
            throw new LinkLimiterException("Изменить или удалить ссылку может только её владелец");
        }
        return link;
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = codeGenerator.generate(config.shortCodeLength());
        } while (linkRepository.findByCode(code).isPresent());
        return code;
    }

    private URI parseAndValidateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new LinkLimiterException("URL не может быть пустым");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                    && !scheme.toLowerCase(Locale.ROOT).equals("https"))
                    || uri.getHost() == null) {
                throw new LinkLimiterException(
                        "Введите абсолютный URL с протоколом http или https"
                );
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new LinkLimiterException("Некорректный URL");
        }
    }

    private String extractShortCode(String shortUrlOrCode) {
        if (shortUrlOrCode == null || shortUrlOrCode.isBlank()) {
            throw new LinkLimiterException("Короткая ссылка не может быть пустой");
        }
        String value = shortUrlOrCode.trim();
        if (value.startsWith(config.shortLinkPrefix())) {
            value = value.substring(config.shortLinkPrefix().length());
        } else {
            int slashIndex = value.lastIndexOf('/');
            if (slashIndex >= 0) {
                value = value.substring(slashIndex + 1);
            }
        }
        if (value.isBlank()) {
            throw new LinkLimiterException("Короткая ссылка имеет неверный формат");
        }
        return value;
    }

    @Override
    public void close() {
        cleanupExecutor.shutdownNow();
    }
}
