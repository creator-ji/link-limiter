package com.linklimiter.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortLinkTest {
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void reachesLimitExactlyAfterLastAllowedClick() {
        ShortLink link = createLink(2);

        link.registerClick();
        assertEquals(LinkStatus.ACTIVE, link.getStatus());

        link.registerClick();
        assertEquals(LinkStatus.LIMIT_REACHED, link.getStatus());
        assertThrows(IllegalStateException.class, link::registerClick);
    }

    @Test
    void validatesConstructorInvariants() {
        assertThrows(IllegalArgumentException.class, () -> createLink(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShortLink(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        URI.create("https://example.com"),
                        " ",
                        1,
                        CREATED_AT,
                        CREATED_AT.plusSeconds(1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShortLink(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        URI.create("https://example.com"),
                        "Ab12",
                        1,
                        CREATED_AT,
                        CREATED_AT
                )
        );
    }

    @Test
    void newLimitMustExceedCompletedClicks() {
        ShortLink link = createLink(3);
        link.registerClick();

        assertThrows(IllegalArgumentException.class, () -> link.updateClickLimit(1));
        link.updateClickLimit(2);
        assertEquals(2, link.getClickLimit());
    }

    private ShortLink createLink(int clickLimit) {
        return new ShortLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                URI.create("https://example.com"),
                "Ab12",
                clickLimit,
                CREATED_AT,
                CREATED_AT.plusSeconds(60)
        );
    }
}
