package dev.nexus.launcher.net;

import java.io.IOException;

public final class HttpStatusException extends IOException {
    private final int status;
    private final String body;

    public HttpStatusException(int status, String url, String body) {
        super("HTTP " + status + " from " + url);
        this.status = status;
        this.body = body == null ? "" : body;
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }
}
