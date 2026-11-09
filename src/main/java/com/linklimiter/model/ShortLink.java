package com.linklimiter.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ShortLink {
    private final UUID id;
    private final UUID ownerId;
    private final URI originalUrl;
    private final String shortCode;
    private int clickLimit;
    private int clickCount;
    private final Instant createdAt;
    private final Instant expiresAt;
    private LinkStatus status;

    public ShortLink(
            UUID id,
            UUID ownerId,
            URI originalUrl,
            String shortCode,
            int clickLimit,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.originalUrl = Objects.requireNonNull(originalUrl);
        this.shortCode = Objects.requireNonNull(shortCode);
        this.clickLimit = clickLimit;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.status = LinkStatus.ACTIVE;
    }

    public void registerClick() {
        if (status != LinkStatus.ACTIVE) {
            throw new IllegalStateException("Переход по неактивной ссылке невозможен");
        }
        clickCount++;
        if (clickCount >= clickLimit) {
            status = LinkStatus.LIMIT_REACHED;
        }
    }

    public void markExpired() {
        if (status != LinkStatus.DELETED) {
            status = LinkStatus.EXPIRED;
        }
    }

    public void markDeleted() {
        status = LinkStatus.DELETED;
    }

    public void updateClickLimit(int newClickLimit) {
        if (status != LinkStatus.ACTIVE) {
            throw new IllegalStateException("Изменить можно только активную ссылку");
        }
        if (newClickLimit <= clickCount) {
            throw new IllegalArgumentException(
                    "Новый лимит должен быть больше количества выполненных переходов"
            );
        }
        clickLimit = newClickLimit;
    }

    public boolean isExpiredAt(Instant instant) {
        return !instant.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public URI getOriginalUrl() {
        return originalUrl;
    }

    public String getShortCode() {
        return shortCode;
    }

    public int getClickLimit() {
        return clickLimit;
    }

    public int getClickCount() {
        return clickCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public LinkStatus getStatus() {
        return status;
    }
}
