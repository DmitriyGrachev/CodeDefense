package dev.codedefense.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AppServerProjectionCodecTest {
    private final AppServerProjectionCodec codec = new AppServerProjectionCodec();

    @Test
    void projectsOnlyFileChangesAndSkipsTranscriptSubtrees() {
        String secret = "PRIVATE-TRANSCRIPT-MARKER";
        String json = """
                {"thread":{"id":"thread-secret-id","cwd":"C:/repo","source":{"type":"cli"},
                "turns":[{"id":"turn-1","items":[
                  {"id":"message-1","type":"userMessage","content":"%s"},
                  {"id":"change-1","type":"fileChange","changes":[
                    {"path":"src/App.java","kind":"update","diff":"@@ -1 +1 @@\\n-old\\n+new"}
                  ],"toolOutput":"%s"}
                ]}]}}
                """.formatted(secret, secret);

        AppServerThread thread = codec.decodeThread(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, thread.items().size());
        assertEquals("src/App.java", thread.items().getFirst().fileChanges().getFirst().path());
        assertFalse(thread.toString().contains("thread-secret-id"));
        assertFalse(thread.items().getFirst().toString().contains(secret));
        assertFalse(thread.items().getFirst().fileChanges().getFirst().toString().contains("old"));
    }

    @Test
    void decodesPagedItemProjection() {
        String json = """
                {"data":[{"id":"c1","type":"fileChange","changes":[
                {"path":"src/A.java","kind":"create","patch":"@@ -0,0 +1 @@\\n+class A {}"}]}],
                "nextCursor":null,"messages":["PRIVATE"]}
                """;
        assertEquals(1, codec.decodeItems(json.getBytes(StandardCharsets.UTF_8)).size());
    }

    @Test
    void rejectsMalformedDataWithSafeException() {
        AppServerProtocolException exception = assertThrows(AppServerProtocolException.class,
                () -> codec.decodeThread("{\"thread\":{\"id\":\"SECRET\"}}".getBytes(StandardCharsets.UTF_8)));
        assertFalse(exception.getMessage().contains("SECRET"));
    }
}
