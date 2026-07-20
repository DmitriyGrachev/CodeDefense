package dev.codedefense.jetbrains.fixture;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class FakeBridgeMain {
    private FakeBridgeMain() { }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "exchange" -> {
                System.out.print("{\"protocolVersion\":1,\"type\":\"hello\",\"capabilities\":[]}\n");
                System.out.flush();
                String request = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                if (request == null || !request.contains("\"type\":\"answer\"")) System.exit(12);
                System.err.print("x".repeat(512 * 1024));
                System.err.flush();
                System.out.print("{\"protocolVersion\":1,\"type\":\"completed\","
                        + "\"exitCode\":0,\"codexInvoked\":false}\n");
                System.out.flush();
            }
            case "sleep" -> Thread.sleep(60_000);
            case "fail" -> System.exit(23);
            case "malformed" -> {
                System.out.print("not-json\n");
                System.out.flush();
            }
            case "oversized" -> {
                System.out.print("x".repeat(300 * 1024) + "\n");
                System.out.flush();
            }
            case "rejectProtocol" -> System.exit(2);
            case "legacyUnsupported" -> {
                System.out.print("{\"protocolVersion\":1,\"type\":\"error\","
                        + "\"code\":\"INVALID_REQUEST\","
                        + "\"message\":\"Unsupported protocol version.\",\"exitCode\":2}\n");
                System.out.flush();
                System.exit(2);
            }
            case "wrongVersionHello" -> {
                System.out.print("{\"protocolVersion\":1,\"type\":\"hello\",\"capabilities\":[]}\n");
                System.out.flush();
                System.exit(2);
            }
            case "requestThenInvalid" -> {
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                System.exit(2);
            }
            case "eventThenInvalid" -> {
                int version = Integer.parseInt(args[1]);
                System.out.print("{\"protocolVersion\":" + version
                        + ",\"type\":\"hello\",\"capabilities\":[]}\n");
                System.out.flush();
                System.exit(2);
            }
            case "exchangeVersion" -> {
                int version = Integer.parseInt(args[1]);
                String requestType = args[2];
                System.out.print("{\"protocolVersion\":" + version
                        + ",\"type\":\"hello\",\"capabilities\":[]}\n");
                System.out.flush();
                String request = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
                if (request == null || !request.contains("\"protocolVersion\":" + version)
                        || !request.contains("\"type\":\"" + requestType + "\"")) {
                    System.exit(14);
                }
                System.out.print("{\"protocolVersion\":" + version
                        + ",\"type\":\"completed\",\"exitCode\":0,\"codexInvoked\":false}\n");
                System.out.flush();
            }
            default -> System.exit(13);
        }
    }
}
