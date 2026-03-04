package de.htwsaar.minicdn.router.dto;

import java.util.Map;

public record AdminFileResult(boolean success, int httpStatus, Object body) {
    public static AdminFileResult success(int status, Object body) {
        return new AdminFileResult(true, status, body);
    }

    public static AdminFileResult error(int status, String message) {
        return new AdminFileResult(false, status, Map.of("error", message));
    }

    public Map<String, Object> toMap() {
        if (body instanceof Map) {
            return (Map<String, Object>) body;
        }
        return Map.of("body", body != null ? body : "");
    }
}
