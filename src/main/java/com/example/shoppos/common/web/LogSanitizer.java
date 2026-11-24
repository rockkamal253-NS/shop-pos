package com.example.shoppos.common.web;

public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String safe(String value) {
        if (value == null) {
            return "null";
        }
        String truncated = value.length() > 64 ? value.substring(0, 64) : value;
        return truncated.replaceAll("[^a-zA-Z0-9._@-]", "_");
    }
}
