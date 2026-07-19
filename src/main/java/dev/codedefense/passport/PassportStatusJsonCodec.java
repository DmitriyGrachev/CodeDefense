package dev.codedefense.passport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codedefense.application.PassportStatusView;
import java.nio.charset.StandardCharsets;

public final class PassportStatusJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper();
    public byte[] encode(PassportStatusView view) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("protocolVersion", view.protocolVersion()); root.put("present", view.present());
            root.put("status", view.status().name()); root.put("changeKind", view.changeKind());
            root.put("shortFingerprint", view.shortFingerprint()); root.put("focus", view.focus());
            root.put("attemptNumber", view.attemptNumber()); root.put("overallScore", view.overallScore());
            root.put("readiness", view.readiness());
            var categories = root.putArray("categories");
            for (var value : view.categories()) {
                var node = categories.addObject(); node.put("id", value.id()); node.put("score", value.finalScore());
            }
            return (mapper.writeValueAsString(root) + "\n").getBytes(StandardCharsets.UTF_8);
        } catch (Exception exception) { throw ChangePassportPersistenceException.readFailure(); }
    }
}
