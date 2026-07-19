package dev.codedefense.bridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict deterministic JSON codec for one bounded NDJSON bridge line. */
public final class BridgeJsonCodec {
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    public byte[] encodeRequest(BridgeRequest request) {
        ObjectNode node = base(type(request), request.protocolVersion());
        switch (request) {
            case BridgeRequest.ConfirmRequest value -> node.put("accepted", value.accepted());
            case BridgeRequest.AnswerRequest value -> node.put("answer", value.answer());
            case BridgeRequest.SkipRequest ignored -> { }
            case BridgeRequest.CancelRequest ignored -> { }
            case ProvenanceConsentRequest value -> {
                node.put("threadId", value.threadId());
                node.put("consent", value.consent());
            }
        }
        return encode(node);
    }

    public byte[] encodeEvent(BridgeEvent event) {
        return encodeEvent(event, event.protocolVersion());
    }

    byte[] encodeEvent(BridgeEvent event, int protocolVersion) {
        BridgeProtocol.requireSupportedVersion(protocolVersion);
        ObjectNode node = base(type(event), protocolVersion);
        switch (event) {
            case BridgeEvent.HelloEvent value -> strings(node, "capabilities", value.capabilities());
            case BridgeEvent.PreviewEvent value -> {
                node.put("projectName", value.projectName());
                node.put("changeKind", value.changeKind());
                node.put("focus", value.focus());
                node.put("selectedFiles", value.selectedFiles());
                node.put("addedLines", value.addedLines());
                node.put("deletedLines", value.deletedLines());
            }
            case BridgeEvent.ConfirmationRequiredEvent value -> node.put("message", value.message());
            case BridgeEvent.QuestionEvent value -> {
                List<BridgeEvidenceLocation> evidence = BridgeProtocol.copyEvidence(
                        protocolVersion, value.followUp(), value.evidence());
                node.put("number", value.number());
                node.put("total", value.total());
                node.put("followUp", value.followUp());
                node.put("prompt", value.prompt());
                if (protocolVersion == BridgeProtocol.VERSION_2) {
                    ArrayNode locations = node.putArray("evidence");
                    evidence.forEach(location -> {
                        ObjectNode encoded = locations.addObject();
                        encoded.put("relativePath", location.relativePath());
                        encoded.put("startLine", location.startLine());
                        encoded.put("endLine", location.endLine());
                    });
                }
            }
            case BridgeEvent.EvaluationEvent value -> {
                node.put("verdict", value.verdict());
                node.put("score", value.score());
                node.put("feedback", value.feedback());
                strings(node, "understoodConcepts", value.understoodConcepts());
                strings(node, "missingConcepts", value.missingConcepts());
            }
            case BridgeEvent.QuestionScoreEvent value -> {
                node.put("questionNumber", value.questionNumber());
                node.put("score", value.score());
            }
            case BridgeEvent.SummaryEvent value -> {
                ArrayNode scores = node.putArray("questionScores");
                value.questionScores().forEach(scores::add);
                node.put("overallScore", value.overallScore());
                node.put("readiness", value.readiness());
            }
            case BridgeEvent.PassportSavedEvent value -> {
                node.put("path", value.path());
                node.put("status", value.status());
                node.put("shortFingerprint", value.shortFingerprint());
            }
            case BridgeEvent.ProvenanceEvent value -> {
                node.put("status", value.status());
                node.put("disclaimer", value.disclaimer());
            }
            case BridgeEvent.CompletedEvent value -> {
                node.put("exitCode", value.exitCode());
                node.put("codexInvoked", value.codexInvoked());
            }
            case BridgeEvent.ErrorEvent value -> {
                node.put("code", value.code());
                node.put("message", value.message());
                node.put("exitCode", value.exitCode());
            }
        }
        return encode(node);
    }

    public BridgeRequest decodeRequest(byte[] line) {
        ObjectNode node = decodeObject(line);
        int version = version(node);
        String type = text(node, "type");
        try {
            return switch (type) {
                case "confirm" -> {
                    fields(node, "protocolVersion", "type", "accepted");
                    yield new BridgeRequest.ConfirmRequest(version, bool(node, "accepted"));
                }
                case "answer" -> {
                    fields(node, "protocolVersion", "type", "answer");
                    yield new BridgeRequest.AnswerRequest(version, text(node, "answer"));
                }
                case "skip" -> {
                    fields(node, "protocolVersion", "type");
                    yield new BridgeRequest.SkipRequest(version);
                }
                case "cancel" -> {
                    fields(node, "protocolVersion", "type");
                    yield new BridgeRequest.CancelRequest(version);
                }
                case "provenanceConsent" -> {
                    fields(node, "protocolVersion", "type", "threadId", "consent");
                    yield new ProvenanceConsentRequest(version, text(node, "threadId"), bool(node, "consent"));
                }
                default -> throw invalid();
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    public BridgeEvent decodeEvent(byte[] line) {
        ObjectNode node = decodeObject(line);
        int version = version(node);
        String type = text(node, "type");
        try {
            return switch (type) {
                case "hello" -> {
                    fields(node, "protocolVersion", "type", "capabilities");
                    yield new BridgeEvent.HelloEvent(version, strings(node, "capabilities"));
                }
                case "preview" -> {
                    fields(node, "protocolVersion", "type", "projectName", "changeKind", "focus",
                            "selectedFiles", "addedLines", "deletedLines");
                    yield new BridgeEvent.PreviewEvent(version, text(node, "projectName"), text(node, "changeKind"),
                            text(node, "focus"), integer(node, "selectedFiles"), integer(node, "addedLines"),
                            integer(node, "deletedLines"));
                }
                case "confirmationRequired" -> {
                    fields(node, "protocolVersion", "type", "message");
                    yield new BridgeEvent.ConfirmationRequiredEvent(version, text(node, "message"));
                }
                case "question" -> {
                    if (version == BridgeProtocol.VERSION_1) {
                        fields(node, "protocolVersion", "type", "number", "total", "followUp", "prompt");
                    } else {
                        fields(node, "protocolVersion", "type", "number", "total", "followUp", "prompt",
                                "evidence");
                    }
                    yield new BridgeEvent.QuestionEvent(version, integer(node, "number"), integer(node, "total"),
                            bool(node, "followUp"), text(node, "prompt"),
                            version == BridgeProtocol.VERSION_1 ? List.of() : evidence(node, "evidence"));
                }
                case "evaluation" -> {
                    fields(node, "protocolVersion", "type", "verdict", "score", "feedback",
                            "understoodConcepts", "missingConcepts");
                    yield new BridgeEvent.EvaluationEvent(version, text(node, "verdict"), integer(node, "score"),
                            text(node, "feedback"), strings(node, "understoodConcepts"),
                            strings(node, "missingConcepts"));
                }
                case "questionScore" -> {
                    fields(node, "protocolVersion", "type", "questionNumber", "score");
                    yield new BridgeEvent.QuestionScoreEvent(version, integer(node, "questionNumber"),
                            integer(node, "score"));
                }
                case "summary" -> {
                    fields(node, "protocolVersion", "type", "questionScores", "overallScore", "readiness");
                    yield new BridgeEvent.SummaryEvent(version, integers(node, "questionScores"),
                            integer(node, "overallScore"), text(node, "readiness"));
                }
                case "passportSaved" -> {
                    fields(node, "protocolVersion", "type", "path", "status", "shortFingerprint");
                    yield new BridgeEvent.PassportSavedEvent(version, text(node, "path"), text(node, "status"),
                            text(node, "shortFingerprint"));
                }
                case "provenance" -> {
                    fields(node, "protocolVersion", "type", "status", "disclaimer");
                    yield new BridgeEvent.ProvenanceEvent(version, text(node, "status"), text(node, "disclaimer"));
                }
                case "completed" -> {
                    fields(node, "protocolVersion", "type", "exitCode", "codexInvoked");
                    yield new BridgeEvent.CompletedEvent(version, integer(node, "exitCode"),
                            bool(node, "codexInvoked"));
                }
                case "error" -> {
                    fields(node, "protocolVersion", "type", "code", "message", "exitCode");
                    yield new BridgeEvent.ErrorEvent(version, text(node, "code"), text(node, "message"),
                            integer(node, "exitCode"));
                }
                default -> throw invalid();
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid(exception);
        }
    }

    private ObjectNode decodeObject(byte[] line) {
        if (line == null || line.length == 0 || line.length > BridgeProtocol.MAX_LINE_BYTES) {
            throw new BridgeProtocolException("Bridge message exceeds the supported line limit.");
        }
        String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(line)).toString();
        } catch (CharacterCodingException exception) {
            throw new BridgeProtocolException("Bridge message is not valid UTF-8.", exception);
        }
        if (json.endsWith("\n")) {
            json = json.substring(0, json.length() - 1);
        }
        if (json.contains("\n") || json.contains("\r")) {
            throw invalid();
        }
        try {
            JsonNode parsed = mapper.readValue(json, JsonNode.class);
            if (!(parsed instanceof ObjectNode object)) {
                throw invalid();
            }
            return object;
        } catch (JsonProcessingException exception) {
            throw invalid(exception);
        }
    }

    private byte[] encode(ObjectNode node) {
        try {
            byte[] json = (mapper.writeValueAsString(node) + "\n").getBytes(StandardCharsets.UTF_8);
            if (json.length > BridgeProtocol.MAX_LINE_BYTES) {
                throw new BridgeProtocolException("Bridge message exceeds the supported line limit.");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new BridgeProtocolException("Bridge message could not be encoded.", exception);
        }
    }

    private ObjectNode base(String type, int version) {
        ObjectNode node = mapper.createObjectNode();
        node.put("protocolVersion", version);
        node.put("type", type);
        return node;
    }

    private static int version(ObjectNode node) {
        int version = integer(node, "protocolVersion");
        if (version != BridgeProtocol.VERSION_1 && version != BridgeProtocol.VERSION_2) {
            throw new BridgeProtocolException("Unsupported bridge protocol version.");
        }
        return version;
    }

    private static String type(BridgeRequest request) {
        return switch (request) {
            case BridgeRequest.ConfirmRequest ignored -> "confirm";
            case BridgeRequest.AnswerRequest ignored -> "answer";
            case BridgeRequest.SkipRequest ignored -> "skip";
            case BridgeRequest.CancelRequest ignored -> "cancel";
            case ProvenanceConsentRequest ignored -> "provenanceConsent";
        };
    }

    private static String type(BridgeEvent event) {
        return switch (event) {
            case BridgeEvent.HelloEvent ignored -> "hello";
            case BridgeEvent.PreviewEvent ignored -> "preview";
            case BridgeEvent.ConfirmationRequiredEvent ignored -> "confirmationRequired";
            case BridgeEvent.QuestionEvent ignored -> "question";
            case BridgeEvent.EvaluationEvent ignored -> "evaluation";
            case BridgeEvent.QuestionScoreEvent ignored -> "questionScore";
            case BridgeEvent.SummaryEvent ignored -> "summary";
            case BridgeEvent.PassportSavedEvent ignored -> "passportSaved";
            case BridgeEvent.ProvenanceEvent ignored -> "provenance";
            case BridgeEvent.CompletedEvent ignored -> "completed";
            case BridgeEvent.ErrorEvent ignored -> "error";
        };
    }

    private static int integer(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid();
        }
        return value.intValue();
    }

    private static boolean bool(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isBoolean()) {
            throw invalid();
        }
        return value.booleanValue();
    }

    private static String text(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) {
            throw invalid();
        }
        return value.textValue();
    }

    private static List<String> strings(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray()) {
            throw invalid();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw invalid();
            }
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    private static List<Integer> integers(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray()) {
            throw invalid();
        }
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isIntegralNumber() || !item.canConvertToInt()) {
                throw invalid();
            }
            values.add(item.intValue());
        }
        return List.copyOf(values);
    }

    private static List<BridgeEvidenceLocation> evidence(ObjectNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray()) {
            throw invalid();
        }
        java.util.ArrayList<BridgeEvidenceLocation> locations = new java.util.ArrayList<>();
        for (JsonNode item : value) {
            if (!(item instanceof ObjectNode object)) {
                throw invalid();
            }
            fields(object, "relativePath", "startLine", "endLine");
            locations.add(new BridgeEvidenceLocation(text(object, "relativePath"),
                    integer(object, "startLine"), integer(object, "endLine")));
        }
        return List.copyOf(locations);
    }

    private static void strings(ObjectNode node, String name, List<String> values) {
        ArrayNode array = node.putArray(name);
        values.forEach(array::add);
    }

    private static void fields(ObjectNode node, String... expected) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        if (!names.equals(Set.of(expected))) {
            throw invalid();
        }
    }

    private static BridgeProtocolException invalid() {
        return new BridgeProtocolException("Bridge message is invalid.");
    }

    private static BridgeProtocolException invalid(Throwable cause) {
        if (cause instanceof BridgeProtocolException protocolException) {
            return protocolException;
        }
        return new BridgeProtocolException("Bridge message is invalid.", cause);
    }
}
