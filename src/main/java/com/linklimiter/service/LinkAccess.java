package com.linklimiter.service;

import java.net.URI;

public record LinkAccess(URI originalUrl, int remainingClicks) {
}
