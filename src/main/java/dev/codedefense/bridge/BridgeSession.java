package dev.codedefense.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Optional;

/** Owns one bounded request/event stream without logging message content. */
public final class BridgeSession {
    private final InputStream input;
    private final OutputStream output;
    private final BridgeJsonCodec codec;
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    public BridgeSession(InputStream input, OutputStream output) {
        this(input, output, new BridgeJsonCodec());
    }

    public BridgeSession(InputStream input, OutputStream output, BridgeJsonCodec codec) {
        this.input = Objects.requireNonNull(input, "input");
        this.output = Objects.requireNonNull(output, "output");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public void emit(BridgeEvent event) {
        Objects.requireNonNull(event, "event");
        byte[] line = codec.encodeEvent(event);
        synchronized (writeLock) {
            try {
                output.write(line);
                output.flush();
            } catch (IOException exception) {
                throw new BridgeProtocolException("Bridge output is unavailable.", exception);
            }
        }
    }

    public Optional<BridgeRequest> readRequest() {
        synchronized (readLock) {
            byte[] line = readBoundedLine();
            if (line == null) {
                return Optional.empty();
            }
            return Optional.of(codec.decodeRequest(line));
        }
    }

    private byte[] readBoundedLine() {
        ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        try {
            while (line.size() < BridgeProtocol.MAX_LINE_BYTES) {
                int value = input.read();
                if (value == -1) {
                    return line.size() == 0 ? null : line.toByteArray();
                }
                line.write(value);
                if (value == '\n') {
                    return line.toByteArray();
                }
            }
            int overflow = input.read();
            if (overflow != -1) {
                throw new BridgeProtocolException("Bridge message exceeds the supported line limit.");
            }
            return line.toByteArray();
        } catch (IOException exception) {
            throw new BridgeProtocolException("Bridge input is unavailable.", exception);
        }
    }
}
