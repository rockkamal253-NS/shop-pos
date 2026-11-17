package com.example.shoppos.common.web;

import jakarta.servlet.http.HttpServletResponse;

/** Pushes a toast to HTMX clients via the HX-Trigger response header. */
public final class Toasts {

    private Toasts() {
    }

    public static void send(HttpServletResponse response, String message, String type) {
        response.setHeader("HX-Trigger", "{\"toast\":{\"message\":\"" + escape(message) + "\",\"type\":\"" + type + "\"}}");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
