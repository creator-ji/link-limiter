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
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LinkService {
    private static final int MAX_CODE_GENERATION_ATTEMPTS = 1_000;

    private final UserRepository userRepository;
    private final ShortLinkRepository linkRepository;
    private final NotificationRepository notificationRepository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final ShortLinkParser shortLinkParser;
    private final AppConfig config;
    private final Clock clock;

    public LinkService(
            UserRepository userRepository,
            ShortLinkRepository linkRepository,
            NotificationRepository notificationRepository,
            ShortCodeGenerator codeGenerator,
            UrlValidator urlValidator,
            ShortLinkParser shortLinkParser,
            AppConfig config,
            Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.linkRepository = Objects.requireNonNull(linkRepository);
        this.notificationRepository = Objects.requireNonNull(notificationRepository);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.urlValidator = Objects.requireNonNull(urlValidator);
        this.shortLinkParser = Objects.requireNonNull(shortLinkParser);
        this.config = Objects.requireNonNull(config);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized CreatedLink createLink(UUID requestedUserId, String rawUrl, int clickLimit) {
        if (clickLimit <= 0) {
            throw new LinkLimiterException("Лимит переходов должен быть положительным числом");
        }

        URI originalUrl = urlValidator.validate(rawUrl);
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
        return new CreatedLink(
                user.id(),
                buildShortUrl(shortCode),
                link.getExpiresAt()
        );
    }

    public synchronized LinkAccess accessLink(String shortUrlOrCode) {
        String shortCode = shortLinkParser.parse(shortUrlOrCode);
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
                            + buildShortUrl(link.getShortCode())
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
        cleanupExpiredLinks(linkRepository.findByOwnerId(userId));
        return linkRepository.findByOwnerId(userId).stream()
                .sorted(Comparator.comparing(ShortLink::getCreatedAt))
                .toList();
    }

    public synchronized void deleteLink(UUID userId, String shortUrlOrCode) {
        requireExistingUser(userId);
        String shortCode = shortLinkParser.parse(shortUrlOrCode);
        ShortLink link = requireOwnedLink(userId, shortCode);
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
        String shortCode = shortLinkParser.parse(shortUrlOrCode);
        ShortLink link = requireOwnedLink(userId, shortCode);
        if (link.isExpiredAt(clock.instant())) {
            expireAndDelete(link);
            throw new LinkLimiterException("Срок действия ссылки истёк");
        }
        try {
            link.updateClickLimit(newClickLimit);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new LinkLimiterException(exception.getMessage(), exception);
        }
    }

    public synchronized List<String> takeNotifications(UUID userId) {
        requireExistingUser(userId);
        cleanupExpiredLinks(linkRepository.findByOwnerId(userId));
        return notificationRepository.takeAll(userId);
    }

    public synchronized int cleanupExpiredLinks() {
        return cleanupExpiredLinks(linkRepository.findAll());
    }

    private int cleanupExpiredLinks(List<ShortLink> links) {
        int deletedCount = 0;
        Instant now = clock.instant();
        for (ShortLink link : links) {
            if (link.isExpiredAt(now)) {
                expireAndDelete(link);
                deletedCount++;
            }
        }
        return deletedCount;
    }

    private void expireAndDelete(ShortLink link) {
        link.markExpired();
        notificationRepository.add(
                link.getOwnerId(),
                "Срок действия ссылки "
                        + buildShortUrl(link.getShortCode())
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
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate(config.shortCodeLength());
            if (!linkRepository.wasCodeUsed(code)) {
                return code;
            }
        }
        throw new LinkLimiterException(
                "Не удалось создать уникальный короткий код. Повторите попытку"
        );
    }

    private String buildShortUrl(String shortCode) {
        return config.shortLinkPrefix() + shortCode;
    }
}
