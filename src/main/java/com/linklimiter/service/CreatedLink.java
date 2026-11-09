package com.linklimiter.service;

import java.time.Instant;
import java.util.UUID;

public record CreatedLink(UUID userId, String shortUrl, Instant expiresAt) {}
