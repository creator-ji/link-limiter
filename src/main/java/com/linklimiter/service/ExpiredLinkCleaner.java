package com.linklimiter.service;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExpiredLinkCleaner implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final LinkService linkService;
    private final Duration cleanupInterval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public ExpiredLinkCleaner(LinkService linkService, Duration cleanupInterval) {
        if (linkService == null) {
            throw new IllegalArgumentException("Сервис ссылок не может быть null");
        }
        if (cleanupInterval == null || cleanupInterval.toMillis() <= 0) {
            throw new IllegalArgumentException("Интервал очистки должен быть не меньше 1 мс");
        }
        this.linkService = linkService;
        this.cleanupInterval = cleanupInterval;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "expired-links-cleaner");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        long intervalMillis = cleanupInterval.toMillis();
        executor.scheduleWithFixedDelay(
                this::runSafely,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public int runOnce() {
        return linkService.cleanupExpiredLinks();
    }

    private void runSafely() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            System.err.println("Не удалось очистить просроченные ссылки: "
                    + exception.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    SHUTDOWN_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            )) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
