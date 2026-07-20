package dev.codedefense.passport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageMap;
import dev.codedefense.domain.EvidenceCoverageState;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class EvidenceCoverageSidecarCodec {
    static final int MAXIMUM_BYTES = 256 * 1024;
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "receiptId", "diffFingerprint", "hunks");
    private static final Set<String> HUNK_FIELDS = Set.of(
            "relativePath", "ordinal", "startLine", "endLine", "navigable", "state", "categoryIds");
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    byte[] encode(StoredEvidenceCoverage stored) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("receiptId", stored.receiptId());
            root.put("diffFingerprint", stored.coverage().diffFingerprint());
            ArrayNode hunks = root.putArray("hunks");
            for (EvidenceCoverageHunk hunk : stored.coverage().hunks()) {
                ObjectNode node = hunks.addObject();
                node.put("relativePath", hunk.relativePath());
                node.put("ordinal", hunk.ordinal());
                node.put("startLine", hunk.startLine());
                node.put("endLine", hunk.endLine());
                node.put("navigable", hunk.navigable());
                node.put("state", hunk.state().name());
                ArrayNode categories = node.putArray("categoryIds");
                hunk.categoryIds().forEach(categories::add);
            }
            byte[] json = mapper.writeValueAsBytes(root);
            if (json.length + 1 > MAXIMUM_BYTES) throw invalid();
            byte[] result = java.util.Arrays.copyOf(json, json.length + 1);
            result[result.length - 1] = '\n';
            return result;
        } catch (RuntimeException | java.io.IOException exception) {
            throw invalid();
        }
    }

    StoredEvidenceCoverage decode(byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > MAXIMUM_BYTES) throw invalid();
            String json = strictUtf8(bytes);
            JsonNode root = mapper.readTree(json);
            fields(root, ROOT_FIELDS);
            if (integer(root, "schemaVersion") != 1) throw invalid();
            JsonNode array = root.get("hunks");
            if (array == null || !array.isArray() || array.size() > 256) throw invalid();
            List<EvidenceCoverageHunk> hunks = new ArrayList<>();
            for (JsonNode item : array) {
                fields(item, HUNK_FIELDS);
                JsonNode categories = item.get("categoryIds");
                if (categories == null || !categories.isArray() || categories.size() > 3) throw invalid();
                List<String> ids = new ArrayList<>();
                categories.forEach(value -> ids.add(textValue(value)));
                hunks.add(new EvidenceCoverageHunk(text(item, "relativePath"), integer(item, "ordinal"),
                        integer(item, "startLine"), integer(item, "endLine"), bool(item, "navigable"),
                        EvidenceCoverageState.valueOf(text(item, "state")), ids));
            }
            return new StoredEvidenceCoverage(text(root, "receiptId"),
                    new EvidenceCoverageMap(text(root, "diffFingerprint"), hunks));
        } catch (RuntimeException | java.io.IOException exception) {
            throw invalid();
        }
    }

    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw invalid();
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw invalid();
    }
    private static String text(JsonNode parent, String name) { return textValue(parent.get(name)); }
    private static String textValue(JsonNode node) { if (node == null || !node.isTextual()) throw invalid(); return node.textValue(); }
    private static int integer(JsonNode parent, String name) { JsonNode node=parent.get(name); if(node==null||!node.isIntegralNumber()||!node.canConvertToInt())throw invalid(); return node.intValue(); }
    private static boolean bool(JsonNode parent, String name) { JsonNode node=parent.get(name); if(node==null||!node.isBoolean())throw invalid(); return node.booleanValue(); }
    private static ChangePassportPersistenceException invalid() { return ChangePassportPersistenceException.receiptReadFailure(); }
}
