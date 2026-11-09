package com.linklimiter.model;

import java.util.Objects;
import java.util.UUID;

public record User(UUID id) {
    public User {
        Objects.requireNonNull(id, "UUID пользователя не может быть null");
    }
}
