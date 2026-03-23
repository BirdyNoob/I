package com.icentric.Icentric.identity.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
    }

    public UserNotFoundException(String message) {
        super(message);
    }
}
