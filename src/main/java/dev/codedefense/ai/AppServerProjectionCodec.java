package dev.codedefense.ai;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Streaming projection that deliberately skips transcript and tool-output subtrees. */
public final class AppServerProjectionCodec {
    private static final int MAXIMUM_ITEMS = 1_000;
    private static final int MAXIMUM_PATHS = 100;
    private final JsonFactory factory = JsonFactory.builder().build();

    public AppServerThread decodeThread(byte[] json) {
        try (JsonParser parser = factory.createParser(json)) {
            require(parser.nextToken(), JsonToken.START_OBJECT);
            ThreadBuilder builder = new ThreadBuilder();
            readObject(parser, builder);
            if (parser.nextToken() != null) throw invalid();
            return builder.build();
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AppServerProtocolException protocol) throw protocol;
            throw invalid();
        }
    }

    public List<AppServerThreadItem> decodeItems(byte[] json) {
        return decodeItemPage(json).items();
    }

    ItemPage decodeItemPage(byte[] json) {
        try (JsonParser parser = factory.createParser(json)) {
            require(parser.nextToken(), JsonToken.START_OBJECT);
            PageBuilder page = new PageBuilder();
            readItemsObject(parser, page);
            if (parser.nextToken() != null) throw invalid();
            return new ItemPage(page.items, page.nextCursor);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof AppServerProtocolException protocol) throw protocol;
            throw invalid();
        }
    }

    private static void readItemsObject(JsonParser parser, PageBuilder page) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if (field.equals("result") && value == JsonToken.START_OBJECT) {
                readItemsObject(parser, page);
            } else if ((field.equals("data") || field.equals("items")) && value == JsonToken.START_ARRAY) {
                readItems(parser, page.items);
            } else if (field.equals("nextCursor") && value == JsonToken.VALUE_STRING) {
                page.nextCursor = parser.getText();
            } else {
                parser.skipChildren();
            }
        }
    }

    private static void readObject(JsonParser parser, ThreadBuilder builder) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if ((field.equals("thread") || field.equals("result")) && value == JsonToken.START_OBJECT) {
                readObject(parser, builder);
            } else if (field.equals("id") && value == JsonToken.VALUE_STRING && builder.id == null) {
                builder.id = parser.getText();
            } else if (field.equals("cwd") && value == JsonToken.VALUE_STRING) {
                builder.cwd = parser.getText();
            } else if (field.equals("source")) {
                builder.source = sourceKind(parser, value);
            } else if (field.equals("turns") && value == JsonToken.START_ARRAY) {
                readTurns(parser, builder.items);
            } else if ((field.equals("items") || field.equals("data")) && value == JsonToken.START_ARRAY) {
                readItems(parser, builder.items);
            } else {
                parser.skipChildren();
            }
        }
    }

    private static String sourceKind(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.VALUE_STRING) return parser.getText();
        if (token != JsonToken.START_OBJECT) { parser.skipChildren(); return "unknown"; }
        String type = "unknown";
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName(); JsonToken value = parser.nextToken();
            if (name.equals("type") && value == JsonToken.VALUE_STRING) type = parser.getText();
            else parser.skipChildren();
        }
        return type;
    }

    private static void readTurns(JsonParser parser, List<AppServerThreadItem> items) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName(); JsonToken value = parser.nextToken();
                if (field.equals("items") && value == JsonToken.START_ARRAY) readItems(parser, items);
                else parser.skipChildren();
            }
        }
    }

    private static void readItems(JsonParser parser, List<AppServerThreadItem> items) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) { parser.skipChildren(); continue; }
            ItemBuilder item = readItem(parser);
            if (item.isFileChange()) {
                if (items.size() >= MAXIMUM_ITEMS) throw invalid();
                items.add(item.build());
            }
        }
    }

    private static ItemBuilder readItem(JsonParser parser) throws IOException {
        ItemBuilder item = new ItemBuilder();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String field = parser.currentName(); JsonToken value = parser.nextToken();
            if (field.equals("id") && value == JsonToken.VALUE_STRING) item.id = parser.getText();
            else if (field.equals("type") && value == JsonToken.VALUE_STRING) item.type = parser.getText();
            else if (field.equals("changes") && value == JsonToken.START_ARRAY) readChanges(parser, item);
            else parser.skipChildren();
        }
        return item;
    }

    private static void readChanges(JsonParser parser, ItemBuilder item) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) { parser.skipChildren(); continue; }
            String path = null, kind = null, patch = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName(); JsonToken value = parser.nextToken();
                if (field.equals("path") && value == JsonToken.VALUE_STRING) path = parser.getText();
                else if (field.equals("kind") && value == JsonToken.VALUE_STRING) kind = parser.getText();
                else if ((field.equals("diff") || field.equals("patch")) && value == JsonToken.VALUE_STRING) {
                    patch = parser.getText();
                } else parser.skipChildren();
            }
            if (path != null && kind != null && patch != null) {
                if (item.changes.size() >= MAXIMUM_PATHS) throw invalid();
                item.changes.add(new RawChange(path, kind, patch));
            }
        }
    }

    private static void require(JsonToken actual, JsonToken expected) {
        if (actual != expected) throw invalid();
    }
    private static AppServerProtocolException invalid() {
        return new AppServerProtocolException(AppServerProtocolException.Kind.INVALID_RESPONSE);
    }
    private static final class ThreadBuilder {
        String id, cwd, source = "unknown"; final List<AppServerThreadItem> items = new ArrayList<>();
        AppServerThread build() { return new AppServerThread(id, cwd, source, items); }
    }
    record ItemPage(List<AppServerThreadItem> items, String nextCursor) {
        ItemPage { items = List.copyOf(items); }
    }
    private static final class PageBuilder {
        final List<AppServerThreadItem> items = new ArrayList<>(); String nextCursor;
    }
    private record RawChange(String path, String kind, String patch) {}
    private static final class ItemBuilder {
        String id, type; final List<RawChange> changes = new ArrayList<>();
        boolean isFileChange() { return "fileChange".equals(type) || "file_change".equals(type); }
        AppServerThreadItem build() {
            return new AppServerThreadItem(id, type, changes.stream()
                    .map(change -> new AppServerFileChange(id, change.path(), change.kind(), change.patch())).toList());
        }
    }
}
