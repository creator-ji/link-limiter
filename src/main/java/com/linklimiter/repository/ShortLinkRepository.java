package com.linklimiter.repository;

import com.linklimiter.model.ShortLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShortLinkRepository {
  ShortLink save(ShortLink link);

  Optional<ShortLink> findByCode(String shortCode);

  boolean wasCodeUsed(String shortCode);

  List<ShortLink> findByOwnerId(UUID ownerId);

  List<ShortLink> findAll();

  void delete(ShortLink link);
}
