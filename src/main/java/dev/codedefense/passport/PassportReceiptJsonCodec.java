package dev.codedefense.passport;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codedefense.domain.ChangeKind;
import dev.codedefense.domain.PassportCategoryReceipt;
import dev.codedefense.domain.PassportFileReceipt;
import dev.codedefense.domain.PassportReceipt;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.PassportAttemptId;
import dev.codedefense.domain.Readiness;
import dev.codedefense.domain.Verdict;
import dev.codedefense.domain.DefenseFocus;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Deterministic strict JSON boundary for source-free Passport receipts. */
public final class PassportReceiptJsonCodec {
    public static final int MAXIMUM_RECEIPT_BYTES = 256 * 1024;
    private static final Set<String> ROOT_FIELDS_V1 = Set.of("schemaVersion", "receiptId",
            "repositoryIdentityHash", "changeKind", "baseCommit", "sourceIdentity",
            "diffFingerprint", "createdAt", "statusAtCreation", "files", "categories",
            "overallScore", "readiness", "skippedPrimaryCount", "model");
    private static final Set<String> ROOT_FIELDS_V2 = java.util.stream.Stream.concat(
            ROOT_FIELDS_V1.stream(), java.util.stream.Stream.of("attemptId", "supersedes", "attemptNumber"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> ROOT_FIELDS_V3 = java.util.stream.Stream.concat(
            ROOT_FIELDS_V2.stream(), java.util.stream.Stream.of("focus"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<String> FILE_FIELDS = Set.of("path", "previousPath", "status",
            "addedLines", "deletedLines");
    private static final Set<String> CATEGORY_FIELDS = Set.of("id", "primaryVerdict",
            "primaryScore", "followUpVerdict", "followUpScore", "finalScore");

    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

    public byte[] encode(PassportReceipt receipt) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", receipt.schemaVersion());
            root.put("receiptId", receipt.receiptId());
            root.put("repositoryIdentityHash", receipt.repositoryIdentityHash());
            root.put("changeKind", receipt.changeKind().name());
            root.put("baseCommit", receipt.baseCommit());
            root.put("sourceIdentity", receipt.sourceIdentity());
            root.put("diffFingerprint", receipt.diffFingerprint());
            root.put("createdAt", receipt.createdAt().toString());
            root.put("statusAtCreation", receipt.statusAtCreation().name());
            ArrayNode files = root.putArray("files");
            for (PassportFileReceipt file : receipt.files()) {
                ObjectNode node = files.addObject();
                node.put("path", file.path());
                if (file.previousPath() == null) node.putNull("previousPath");
                else node.put("previousPath", file.previousPath());
                node.put("status", file.status());
                node.put("addedLines", file.addedLines());
                node.put("deletedLines", file.deletedLines());
            }
            ArrayNode categories = root.putArray("categories");
            for (PassportCategoryReceipt category : receipt.categories()) {
                ObjectNode node = categories.addObject();
                node.put("id", category.id());
                node.put("primaryVerdict", category.primaryVerdict().name());
                node.put("primaryScore", category.primaryScore());
                if (category.followUpVerdict().isPresent()) {
                    node.put("followUpVerdict", category.followUpVerdict().orElseThrow().name());
                    node.put("followUpScore", category.followUpScore().orElseThrow());
                } else {
                    node.putNull("followUpVerdict");
                    node.putNull("followUpScore");
                }
                node.put("finalScore", category.finalScore());
            }
            root.put("overallScore", receipt.overallScore());
            root.put("readiness", receipt.readiness().name());
            root.put("skippedPrimaryCount", receipt.skippedPrimaryCount());
            root.put("model", receipt.model());
            if (receipt.schemaVersion() >= 2) {
                root.put("attemptId", receipt.attemptId().value());
                if (receipt.supersedes().isPresent()) root.put("supersedes", receipt.supersedes().orElseThrow().value());
                else root.putNull("supersedes");
                root.put("attemptNumber", receipt.attemptNumber());
            }
            if (receipt.schemaVersion() >= 3) root.put("focus", receipt.focus().cliName());
            byte[] json = mapper.writeValueAsBytes(root);
            if (json.length + 1 > MAXIMUM_RECEIPT_BYTES) {
                throw ChangePassportPersistenceException.saveFailure();
            }
            byte[] result = java.util.Arrays.copyOf(json, json.length + 1);
            result[result.length - 1] = '\n';
            return result;
        } catch (ChangePassportPersistenceException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw ChangePassportPersistenceException.saveFailure();
        }
    }

    public PassportReceipt decode(byte[] utf8Json) {
        try {
            if (utf8Json == null || utf8Json.length == 0 || utf8Json.length > MAXIMUM_RECEIPT_BYTES) {
                throw invalid();
            }
            String json = decodeUtf8(utf8Json);
            JsonNode root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(json);
            int schemaVersion = integer(root, "schemaVersion");
            requireObject(root, schemaVersion == 1 ? ROOT_FIELDS_V1
                    : schemaVersion == 2 ? ROOT_FIELDS_V2 : ROOT_FIELDS_V3);
            List<PassportFileReceipt> files = new ArrayList<>();
            for (JsonNode node : requireArray(root, "files")) {
                requireObject(node, FILE_FIELDS);
                JsonNode previous = required(node, "previousPath");
                files.add(new PassportFileReceipt(text(node, "path"),
                        previous.isNull() ? null : textValue(previous), text(node, "status"),
                        integer(node, "addedLines"), integer(node, "deletedLines")));
            }
            List<PassportCategoryReceipt> categories = new ArrayList<>();
            for (JsonNode node : requireArray(root, "categories")) {
                requireObject(node, CATEGORY_FIELDS);
                JsonNode followVerdict = required(node, "followUpVerdict");
                JsonNode followScore = required(node, "followUpScore");
                categories.add(new PassportCategoryReceipt(text(node, "id"),
                        Verdict.valueOf(text(node, "primaryVerdict")), integer(node, "primaryScore"),
                        followVerdict.isNull() ? Optional.empty()
                                : Optional.of(Verdict.valueOf(textValue(followVerdict))),
                        followScore.isNull() ? Optional.empty()
                                : Optional.of(integerValue(followScore)),
                        integer(node, "finalScore")));
            }
            String receiptId = text(root, "receiptId");
            PassportAttemptId attemptId = new PassportAttemptId(schemaVersion == 1
                    ? receiptId : text(root, "attemptId"));
            Optional<PassportAttemptId> supersedes = schemaVersion == 1 || required(root, "supersedes").isNull()
                    ? Optional.empty() : Optional.of(new PassportAttemptId(text(root, "supersedes")));
            int attemptNumber = schemaVersion == 1 ? 1 : integer(root, "attemptNumber");
            DefenseFocus focus = schemaVersion < 3 ? DefenseFocus.BALANCED
                    : DefenseFocus.parse(text(root, "focus"));
            return new PassportReceipt(schemaVersion, receiptId,
                    text(root, "repositoryIdentityHash"), ChangeKind.valueOf(text(root, "changeKind")),
                    text(root, "baseCommit"), text(root, "sourceIdentity"),
                    text(root, "diffFingerprint"), Instant.parse(text(root, "createdAt")),
                    PassportStatus.valueOf(text(root, "statusAtCreation")), files, categories,
                    integer(root, "overallScore"), Readiness.valueOf(text(root, "readiness")),
                    integer(root, "skippedPrimaryCount"), text(root, "model"), attemptId, supersedes,
                    attemptNumber, focus);
        } catch (ChangePassportPersistenceException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw invalid();
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static void requireObject(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()) throw invalid();
        java.util.HashSet<String> actual = new java.util.HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw invalid();
    }

    private static Iterable<JsonNode> requireArray(JsonNode parent, String field) {
        JsonNode node = required(parent, field);
        if (!node.isArray()) throw invalid();
        return node;
    }

    private static JsonNode required(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) throw invalid();
        return node;
    }

    private static String text(JsonNode parent, String field) {
        return textValue(required(parent, field));
    }

    private static String textValue(JsonNode node) {
        if (!node.isTextual()) throw invalid();
        return node.textValue();
    }

    private static int integer(JsonNode parent, String field) {
        return integerValue(required(parent, field));
    }

    private static int integerValue(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) throw invalid();
        return node.intValue();
    }

    private static ChangePassportPersistenceException invalid() {
        return ChangePassportPersistenceException.receiptReadFailure();
    }
}
