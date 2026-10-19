package dev.nexus.launcher.auth;

import java.io.IOException;

/** Authentication failure with a message that can be shown to the user as is. */
public final class AuthException extends IOException {
    public AuthException(String userMessage) {
        super(userMessage);
    }

    public AuthException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
