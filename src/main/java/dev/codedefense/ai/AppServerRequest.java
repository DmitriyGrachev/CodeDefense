package dev.codedefense.ai;

import java.util.Map;
import java.util.Objects;

public record AppServerRequest(Long id, String method, Map<String, Object> params) {
    public AppServerRequest {
        if (id != null && id < 1) throw new IllegalArgumentException("request id must be positive");
        Objects.requireNonNull(method, "method");
        if (method.isBlank()) throw new IllegalArgumentException("method must be nonblank");
        Objects.requireNonNull(params, "params");
        params = Map.copyOf(params);
    }

    @Override public String toString() {
        return "AppServerRequest[id=%s, method=%s, parameterCount=%d]"
                .formatted(id, method, params.size());
    }
}
