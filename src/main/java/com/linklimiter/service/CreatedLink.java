package com.linklimiter.service;

import com.linklimiter.model.ShortLink;

import java.util.UUID;

public record CreatedLink(UUID userId, String shortUrl, ShortLink link) {
}
