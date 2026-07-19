package dev.codedefense.ai;

import java.util.Arrays;
import java.util.Objects;

public final class AppServerResponse {
    private final long id;
    private final Integer errorCode;
    private final byte[] json;

    public AppServerResponse(long id, Integer errorCode, byte[] json) {
        if (id < 1) throw new IllegalArgumentException("response id must be positive");
        this.id = id;
        this.errorCode = errorCode;
        this.json = Arrays.copyOf(Objects.requireNonNull(json, "json"), json.length);
    }

    public long id() { return id; }
    public Integer errorCode() { return errorCode; }
    public byte[] json() { return Arrays.copyOf(json, json.length); }

    @Override public String toString() {
        return "AppServerResponse[id=%d, error=%s, byteCount=%d]"
                .formatted(id, errorCode != null, json.length);
    }
}
