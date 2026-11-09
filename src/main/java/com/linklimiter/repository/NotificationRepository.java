package com.linklimiter.repository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
  void add(UUID userId, String message);

  List<String> takeAll(UUID userId);
}
