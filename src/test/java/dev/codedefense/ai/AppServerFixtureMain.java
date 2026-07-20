package dev.codedefense.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class AppServerFixtureMain {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private AppServerFixtureMain() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4 || !"app-server".equals(arguments[1])
                || !"--listen".equals(arguments[2]) || !"stdio://".equals(arguments[3])) {
            System.exit(23);
        }
        String mode = arguments[0];
        if (mode.equals("large-stderr")) {
            byte[] bytes = new byte[2 * 1024 * 1024]; Arrays.fill(bytes, (byte) 'e');
            System.err.write(bytes); System.err.flush();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode request = MAPPER.readTree(line);
                String method = request.path("method").asText();
                if (method.equals("initialized")) continue;
                long id = request.path("id").asLong();
                if (mode.equals("nonzero")) {
                    System.err.print("PRIVATE-SERVER-DIAGNOSTIC"); System.err.flush(); System.exit(17);
                }
                if (mode.equals("timeout")) { Thread.sleep(30_000); continue; }
                if (mode.equals("invalid-utf8")) {
                    System.out.write(new byte[] {(byte) 0xC3, (byte) 0x28, '\n'}); System.out.flush(); continue;
                }
                if (method.equals("initialize")) {
                    respond(mode, "{\"id\":" + id + ",\"result\":{\"serverInfo\":{\"name\":\"codex\"}}}");
                } else if (method.equals("thread/items/list") && mode.equals("unsupported")) {
                    respond(mode, "{\"id\":" + id + ",\"error\":{\"code\":-32601,\"message\":\"PRIVATE\"}}");
                } else if (method.equals("thread/items/list")) {
                    notification();
                    boolean secondPage = request.path("params").has("cursor");
                    String cursor = mode.equals("paged") && !secondPage ? ",\"nextCursor\":\"page-2\"" : "";
                    respond(mode, "{\"id\":" + id + ",\"result\":{\"data\":[" +
                            fileChange(secondPage ? "change-2" : "change-1", secondPage ? "src/B.java" : "src/App.java")
                            + "]" + cursor + "}}");
                } else if (method.equals("thread/read")) {
                    notification();
                    respond(mode, "{\"id\":" + id + ",\"result\":{\"thread\":{" +
                            "\"id\":\"private-thread-id\",\"cwd\":\"C:/repo\",\"source\":{\"type\":\"cli\"}," +
                            "\"turns\":[{\"items\":[{\"id\":\"m1\",\"type\":\"userMessage\"," +
                            "\"content\":\"PRIVATE-TRANSCRIPT\"}," + fileChange() + "]}]}}}");
                }
            }
        }
    }

    private static String fileChange() { return fileChange("change-1", "src/App.java"); }
    private static String fileChange(String id, String path) {
        return "{\"id\":\"" + id + "\",\"type\":\"fileChange\",\"changes\":[{" +
                "\"path\":\"" + path + "\",\"kind\":\"update\"," +
                "\"diff\":\"@@ -1 +1 @@\\n-old\\n+new\"}]}";
    }

    private static void notification() throws IOException {
        System.out.print("{\"method\":\"thread/status/changed\",\"params\":{\"secret\":\"PRIVATE\"}}\n");
        System.out.flush();
    }

    private static void respond(String mode, String response) throws IOException {
        if (mode.equals("fragmented")) {
            byte[] bytes = (response + "\n").getBytes(StandardCharsets.UTF_8);
            for (byte value : bytes) { System.out.write(value); System.out.flush(); }
        } else {
            System.out.print(response + "\n"); System.out.flush();
        }
    }
}
