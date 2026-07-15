package dev.codedefense.ai;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public final class ProcessFixtureMain {
    private ProcessFixtureMain() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("Fixture mode is required");
        }
        switch (arguments[0]) {
            case "echo" -> System.in.transferTo(System.out);
            case "both" -> {
                if (arguments.length != 3 && arguments.length != 4) {
                    throw new IllegalArgumentException("Unexpected fixture argument count");
                }
                System.out.print(arguments[1]);
                System.err.print(arguments[2]);
                if (arguments.length == 4) {
                    String value = System.getenv(arguments[3]);
                    System.out.print(value == null ? "<absent>" : value);
                }
            }
            case "fail" -> {
                requireArgumentCount(arguments, 3);
                System.err.print(arguments[2]);
                System.exit(Integer.parseInt(arguments[1]));
            }
            case "stderr" -> {
                requireArgumentCount(arguments, 2);
                writeRepeated(System.err, Integer.parseInt(arguments[1]));
            }
            case "sleep" -> {
                requireArgumentCount(arguments, 2);
                sleep(Long.parseLong(arguments[1]));
            }
            default -> throw new IllegalArgumentException("Unsupported fixture mode");
        }
    }

    private static void requireArgumentCount(String[] arguments, int expected) {
        if (arguments.length != expected) {
            throw new IllegalArgumentException("Unexpected fixture argument count");
        }
    }

    private static void writeRepeated(OutputStream output, int byteCount) throws IOException {
        if (byteCount < 0) {
            throw new IllegalArgumentException("Byte count must be non-negative");
        }
        byte[] buffer = new byte[Math.min(byteCount, 8 * 1024)];
        Arrays.fill(buffer, (byte) 'e');
        int remaining = byteCount;
        while (remaining > 0) {
            int written = Math.min(remaining, buffer.length);
            output.write(buffer, 0, written);
            remaining -= written;
        }
        output.flush();
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
