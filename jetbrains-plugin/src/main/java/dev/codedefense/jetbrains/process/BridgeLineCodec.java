package dev.codedefense.jetbrains.process;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BridgeLineCodec {
    public static final int MAX_LINE_BYTES = 256 * 1024;
    public static final int MAX_SESSION_BYTES = 4 * 1024 * 1024;
    public static final int MAX_ANSWER_BYTES = 8 * 1024;
    private static final int PROTOCOL_VERSION_1 = 1;
    private static final int PROTOCOL_VERSION_2 = 2;
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private final int requestProtocolVersion;

    public BridgeLineCodec() {
        this(PROTOCOL_VERSION_2);
    }

    public BridgeLineCodec(int requestProtocolVersion) {
        if (requestProtocolVersion != PROTOCOL_VERSION_1 && requestProtocolVersion != PROTOCOL_VERSION_2) {
            throw new IllegalArgumentException("Unsupported bridge protocol version.");
        }
        this.requestProtocolVersion = requestProtocolVersion;
    }

    public int protocolVersion() {
        return requestProtocolVersion;
    }

    public BridgeMessage decodeEvent(byte[] encodedLine) {
        Objects.requireNonNull(encodedLine, "encodedLine");
        if (encodedLine.length == 0 || encodedLine.length > MAX_LINE_BYTES) {
            throw invalidEvent();
        }
        String line = strictUtf8(encodedLine);
        if (line.endsWith("\n")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty() || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw invalidEvent();
        }
        try {
            JsonNode root = MAPPER.readTree(line);
            if (root == null || !root.isObject()) {
                throw invalidEvent();
            }
            JsonNode version = root.get("protocolVersion");
            JsonNode type = root.get("type");
            if (version == null || !version.isIntegralNumber() || !version.canConvertToInt()
                    || (version.intValue() != PROTOCOL_VERSION_1 && version.intValue() != PROTOCOL_VERSION_2)
                    || type == null || !type.isTextual() || type.textValue().isBlank()
                    || type.textValue().length() > 64) {
                throw invalidEvent();
            }
            List<EvidenceLocationView> evidence = validateEvent(root, type.textValue(), version.intValue());
            return new BridgeMessage(root.deepCopy(), evidence);
        } catch (JsonProcessingException exception) {
            throw invalidEvent(exception);
        }
    }

    private List<EvidenceLocationView> validateEvent(JsonNode root, String type, int protocolVersion) {
        List<EvidenceLocationView> evidence = List.of();
        switch (type) {
            case "hello" -> {
                fields(root, "protocolVersion", "type", "capabilities");
                strings(root, "capabilities", 32, 64);
            }
            case "preview" -> {
                fields(root, "protocolVersion", "type", "projectName", "changeKind", "focus",
                        "selectedFiles", "addedLines", "deletedLines");
                text(root, "projectName", 512); text(root, "changeKind", 64); text(root, "focus", 64);
                integer(root, "selectedFiles", 0, 30);
                integer(root, "addedLines", 0, Integer.MAX_VALUE);
                integer(root, "deletedLines", 0, Integer.MAX_VALUE);
            }
            case "confirmationRequired" -> {
                fields(root, "protocolVersion", "type", "message"); text(root, "message", 1024);
            }
            case "question" -> {
                if (protocolVersion == PROTOCOL_VERSION_1) {
                    fields(root, "protocolVersion", "type", "number", "total", "followUp", "prompt");
                } else {
                    fields(root, "protocolVersion", "type", "number", "total", "followUp", "prompt", "evidence");
                }
                int number = integer(root, "number", 1, 3);
                int total = integer(root, "total", 1, 3);
                if (number > total) throw invalidEvent();
                boolean followUp = bool(root, "followUp");
                text(root, "prompt", 16_384);
                if (protocolVersion == PROTOCOL_VERSION_2) {
                    evidence = evidence(root, followUp);
                }
            }
            case "evaluation" -> {
                fields(root, "protocolVersion", "type", "verdict", "score", "feedback",
                        "understoodConcepts", "missingConcepts");
                text(root, "verdict", 32); integer(root, "score", 0, 100); text(root, "feedback", 16_384);
                strings(root, "understoodConcepts", 32, 2048);
                strings(root, "missingConcepts", 32, 2048);
            }
            case "questionScore" -> {
                fields(root, "protocolVersion", "type", "questionNumber", "score");
                integer(root, "questionNumber", 1, 3); integer(root, "score", 0, 100);
            }
            case "summary" -> {
                fields(root, "protocolVersion", "type", "questionScores", "overallScore", "readiness");
                JsonNode scores = root.get("questionScores");
                if (scores == null || !scores.isArray() || scores.size() != 3) throw invalidEvent();
                scores.forEach(score -> integral(score, 0, 100));
                integer(root, "overallScore", 0, 100); text(root, "readiness", 64);
            }
            case "passportSaved" -> {
                fields(root, "protocolVersion", "type", "path", "status", "shortFingerprint");
                text(root, "path", 4096); text(root, "status", 32); text(root, "shortFingerprint", 64);
            }
            case "provenance" -> {
                fields(root, "protocolVersion", "type", "status", "disclaimer");
                text(root, "status", 64); text(root, "disclaimer", 512);
            }
            case "completed" -> {
                fields(root, "protocolVersion", "type", "exitCode", "codexInvoked");
                integer(root, "exitCode", 0, 255); bool(root, "codexInvoked");
            }
            case "error" -> {
                fields(root, "protocolVersion", "type", "code", "message", "exitCode");
                text(root, "code", 64); text(root, "message", 1024); integer(root, "exitCode", 1, 255);
            }
            default -> throw invalidEvent();
        }
        return evidence;
    }

    private List<EvidenceLocationView> evidence(JsonNode root, boolean followUp) {
        JsonNode value = root.get("evidence");
        if (value == null || !value.isArray() || value.size() > 10
                || (followUp && !value.isEmpty()) || (!followUp && value.isEmpty())) {
            throw invalidEvent();
        }
        List<EvidenceLocationView> result = new ArrayList<>();
        Set<EvidenceLocationView> unique = new HashSet<>();
        value.forEach(item -> {
            if (!item.isObject()) throw invalidEvent();
            fields(item, "relativePath", "startLine", "endLine");
            try {
                EvidenceLocationView location = new EvidenceLocationView(
                        text(item, "relativePath", 4096),
                        integer(item, "startLine", 1, Integer.MAX_VALUE),
                        integer(item, "endLine", 1, Integer.MAX_VALUE));
                if (!unique.add(location)) throw invalidEvent();
                result.add(location);
            } catch (IllegalArgumentException exception) {
                throw invalidEvent(exception);
            }
        });
        return List.copyOf(result);
    }

    private void fields(JsonNode root, String... expected) {
        Set<String> actual = new HashSet<>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(Set.of(expected))) throw invalidEvent();
    }

    private String text(JsonNode root, String name, int maximumLength) {
        JsonNode value = root.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()
                || value.textValue().length() > maximumLength) throw invalidEvent();
        return value.textValue();
    }

    private int integer(JsonNode root, String name, int minimum, int maximum) {
        JsonNode value = root.get(name);
        return integral(value, minimum, maximum);
    }

    private int integral(JsonNode value, int minimum, int maximum) {
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) throw invalidEvent();
        int number = value.intValue();
        if (number < minimum || number > maximum) throw invalidEvent();
        return number;
    }

    private boolean bool(JsonNode root, String name) {
        JsonNode value = root.get(name);
        if (value == null || !value.isBoolean()) throw invalidEvent();
        return value.booleanValue();
    }

    private void strings(JsonNode root, String name, int maximumCount, int maximumLength) {
        JsonNode value = root.get(name);
        if (value == null || !value.isArray() || value.size() > maximumCount) throw invalidEvent();
        value.forEach(item -> {
            if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > maximumLength) {
                throw invalidEvent();
            }
        });
    }

    public byte[] confirmRequest(boolean accepted) {
        var root = request("confirm");
        root.put("accepted", accepted);
        return encode(root);
    }

    public byte[] answerRequest(String answer) {
        Objects.requireNonNull(answer, "answer");
        if (answer.indexOf('\n') >= 0 || answer.indexOf('\r') >= 0
                || answer.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES) {
            throw new BridgeTransportException("The bridge answer is invalid.");
        }
        var root = request("answer");
        root.put("answer", answer);
        return encode(root);
    }

    public byte[] skipRequest() {
        return encode(request("skip"));
    }

    public byte[] cancelRequest() {
        return encode(request("cancel"));
    }

    public byte[] provenanceConsentRequest(String threadId, boolean consent) {
        Objects.requireNonNull(threadId, "threadId");
        if (!consent || threadId.isBlank() || threadId.getBytes(StandardCharsets.UTF_8).length > 4_096
                || threadId.chars().anyMatch(Character::isISOControl)) {
            throw new BridgeTransportException("The provenance consent request is invalid.");
        }
        var root = request("provenanceConsent");
        root.put("threadId", threadId);
        root.put("consent", true);
        return encode(root);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode request(String type) {
        var root = MAPPER.createObjectNode();
        root.put("protocolVersion", requestProtocolVersion);
        root.put("type", type);
        return root;
    }

    private byte[] encode(JsonNode root) {
        try {
            byte[] bytes = (MAPPER.writeValueAsString(root) + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_LINE_BYTES) {
                throw new BridgeTransportException("The bridge request is too large.");
            }
            return bytes;
        } catch (JsonProcessingException exception) {
            throw new BridgeTransportException("The bridge request could not be encoded.", exception);
        }
    }

    private String strictUtf8(byte[] bytes) {
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException exception) {
            throw invalidEvent(exception);
        }
    }

    private BridgeTransportException invalidEvent() {
        return new BridgeTransportException("CodeDefense returned an invalid bridge event.");
    }

    private BridgeTransportException invalidEvent(Throwable cause) {
        return new BridgeTransportException("CodeDefense returned an invalid bridge event.", cause);
    }

    public static final class BridgeMessage {
        private final JsonNode node;
        private final List<EvidenceLocationView> evidence;

        private BridgeMessage(JsonNode node, List<EvidenceLocationView> evidence) {
            this.node = node;
            this.evidence = List.copyOf(evidence);
        }

        public String type() {
            return node.path("type").textValue();
        }

        public int protocolVersion() {
            return node.path("protocolVersion").intValue();
        }

        public String text(String field) {
            JsonNode value = node.get(field);
            return value != null && value.isTextual() ? value.textValue() : null;
        }

        public int integer(String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
                throw new BridgeTransportException("The bridge event field is invalid.");
            }
            return value.intValue();
        }

        public boolean bool(String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isBoolean()) {
                throw new BridgeTransportException("The bridge event field is invalid.");
            }
            return value.booleanValue();
        }

        public JsonNode field(String field) {
            JsonNode value = node.get(field);
            return value == null ? null : value.deepCopy();
        }

        public List<Integer> integers(String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray()) {
                throw new BridgeTransportException("The bridge event field is invalid.");
            }
            List<Integer> result = new ArrayList<>();
            value.forEach(item -> {
                if (!item.isIntegralNumber() || !item.canConvertToInt()) {
                    throw new BridgeTransportException("The bridge event field is invalid.");
                }
                result.add(item.intValue());
            });
            return List.copyOf(result);
        }

        public List<String> strings(String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray()) {
                throw new BridgeTransportException("The bridge event field is invalid.");
            }
            List<String> result = new ArrayList<>();
            value.forEach(item -> {
                if (!item.isTextual()) throw new BridgeTransportException("The bridge event field is invalid.");
                result.add(item.textValue());
            });
            return List.copyOf(result);
        }

        public List<EvidenceLocationView> evidence() {
            return List.copyOf(evidence);
        }

        @Override
        public String toString() {
            return "BridgeMessage[type=" + type() + "]";
        }
    }

    @Override
    public String toString() {
        return "BridgeLineCodec[requestProtocolVersion=" + requestProtocolVersion
                + ", supportedEventVersions=[1, 2]]";
    }
}
