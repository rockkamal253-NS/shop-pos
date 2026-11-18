package com.example.shoppos.common.web;

import jakarta.servlet.http.HttpServletResponse;

/** Pushes a toast to HTMX clients via the HX-Trigger response header. */
public final class Toasts {

    private Toasts() {
    }

    public static void send(HttpServletResponse response, String message, String type) {
        response.setHeader("HX-Trigger", "{\"toast\":{\"message\":\"" + escape(message) + "\",\"type\":\"" + escape(type) + "\"}}");
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
