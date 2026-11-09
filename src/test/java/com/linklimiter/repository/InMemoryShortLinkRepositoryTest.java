package com.linklimiter.repository;

import com.linklimiter.model.ShortLink;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryShortLinkRepositoryTest {
    @Test
    void remembersCodeAfterLinkDeletion() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLink link = createLink("Ab12Cd");

        repository.save(link);
        repository.delete(link);

        assertTrue(repository.wasCodeUsed("Ab12Cd"));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(createLink("Ab12Cd"))
        );
    }

    private ShortLink createLink(String shortCode) {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        return new ShortLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                URI.create("https://example.com"),
                shortCode,
                2,
                createdAt,
                createdAt.plusSeconds(60)
        );
    }
}
