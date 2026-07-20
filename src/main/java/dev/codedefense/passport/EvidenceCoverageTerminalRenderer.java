package dev.codedefense.passport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codedefense.application.EvidenceCoverageView;
import dev.codedefense.bridge.BridgeEvidenceCoveragePublisher;
import dev.codedefense.domain.EvidenceCoverageHunk;
import dev.codedefense.domain.EvidenceCoverageState;
import java.util.Comparator;
import java.util.Objects;

public final class EvidenceCoverageTerminalRenderer {
    private final ObjectMapper mapper = new ObjectMapper();

    public String render(EvidenceCoverageView view, String format) {
        Objects.requireNonNull(view, "view");
        return switch (Objects.requireNonNull(format, "format")) {
            case "text" -> text(view);
            case "json" -> json(view);
            default -> throw new IllegalArgumentException("Unknown coverage format");
        };
    }

    private String text(EvidenceCoverageView view) {
        StringBuilder output = new StringBuilder();
        if (view.summary().isEmpty()) {
            return output.append(view.status()).append('\n').toString();
        }
        var summary = view.summary().orElseThrow();
        output.append("Evidence Coverage: ").append(summary.referencedHunks()).append(" / ")
                .append(summary.measurableHunks()).append(" · ")
                .append(summary.percentage().isPresent() ? summary.percentage().getAsInt() + "%" : "unavailable")
                .append('\n');
        view.details().ifPresent(details -> details.hunks().stream()
                .sorted(Comparator.comparing((EvidenceCoverageHunk value) ->
                                value.state() == EvidenceCoverageState.UNREFERENCED ? 0 : 1)
                        .thenComparing(EvidenceCoverageHunk::relativePath)
                        .thenComparingInt(EvidenceCoverageHunk::ordinal))
                .forEach(hunk -> output.append(hunk.relativePath()).append(':').append(hunk.startLine())
                        .append(" — ").append(label(hunk)).append('\n')));
        if (view.details().isEmpty()) output.append(view.status()).append('\n');
        return output.append(BridgeEvidenceCoveragePublisher.DISCLAIMER).append('\n').toString();
    }

    private String json(EvidenceCoverageView view) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("schemaVersion", 1);
            root.put("status", view.status());
            if (view.summary().isEmpty()) root.putNull("summary");
            else {
                var summary = view.summary().orElseThrow();
                ObjectNode node = root.putObject("summary");
                node.put("totalHunks", summary.totalHunks());
                node.put("measurableHunks", summary.measurableHunks());
                node.put("referencedHunks", summary.referencedHunks());
            }
            ArrayNode hunks = root.putArray("hunks");
            view.details().ifPresent(details -> details.hunks().forEach(hunk -> {
                ObjectNode node = hunks.addObject();
                node.put("relativePath", hunk.relativePath()); node.put("ordinal", hunk.ordinal());
                node.put("startLine", hunk.startLine()); node.put("endLine", hunk.endLine());
                node.put("navigable", hunk.navigable()); node.put("state", hunk.state().name());
                ArrayNode categories = node.putArray("categoryIds"); hunk.categoryIds().forEach(categories::add);
            }));
            root.put("disclaimer", BridgeEvidenceCoveragePublisher.DISCLAIMER);
            return mapper.writeValueAsString(root) + "\n";
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Coverage could not be rendered", exception);
        }
    }

    private static String label(EvidenceCoverageHunk hunk) {
        return switch (hunk.state()) {
            case REFERENCED -> String.join(", ", hunk.categoryIds());
            case UNREFERENCED -> "Not referenced";
            case UNMEASURABLE -> "Unmeasurable";
        };
    }
}
