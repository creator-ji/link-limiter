package com.linklimiter.repository;

import com.linklimiter.model.ShortLink;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryShortLinkRepository implements ShortLinkRepository {
  private final Map<String, ShortLink> linksByCode = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> codesByOwner = new ConcurrentHashMap<>();
  private final Set<String> usedCodes = ConcurrentHashMap.newKeySet();

  @Override
  public ShortLink save(ShortLink link) {
    if (!usedCodes.add(link.getShortCode())) {
      throw new IllegalArgumentException("Короткий код уже использовался");
    }
    linksByCode.put(link.getShortCode(), link);
    codesByOwner
        .computeIfAbsent(link.getOwnerId(), ignored -> ConcurrentHashMap.newKeySet())
        .add(link.getShortCode());
    return link;
  }

  @Override
  public Optional<ShortLink> findByCode(String shortCode) {
    return Optional.ofNullable(linksByCode.get(shortCode));
  }

  @Override
  public boolean wasCodeUsed(String shortCode) {
    return usedCodes.contains(shortCode);
  }

  @Override
  public List<ShortLink> findByOwnerId(UUID ownerId) {
    Set<String> codes = codesByOwner.getOrDefault(ownerId, Set.of());
    List<ShortLink> result = new ArrayList<>();
    for (String code : codes) {
      ShortLink link = linksByCode.get(code);
      if (link != null) {
        result.add(link);
      }
    }
    return List.copyOf(result);
  }

  @Override
  public List<ShortLink> findAll() {
    return List.copyOf(linksByCode.values());
  }

  @Override
  public void delete(ShortLink link) {
    linksByCode.remove(link.getShortCode(), link);
    codesByOwner.computeIfPresent(
        link.getOwnerId(),
        (ownerId, codes) -> {
          codes.remove(link.getShortCode());
          return codes.isEmpty() ? null : codes;
        });
  }
}
