package com.linklimiter.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryNotificationRepository implements NotificationRepository {
    private final Map<UUID, List<String>> notifications = new ConcurrentHashMap<>();

    @Override
    public void add(UUID userId, String message) {
        notifications.compute(userId, (id, messages) -> {
            List<String> result = messages == null ? new ArrayList<>() : messages;
            result.add(message);
            return result;
        });
    }

    @Override
    public List<String> takeAll(UUID userId) {
        List<String> messages = notifications.remove(userId);
        return messages == null ? List.of() : List.copyOf(messages);
    }
}
