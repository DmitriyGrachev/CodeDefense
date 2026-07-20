package dev.codedefense.bridge;

import java.util.List;

public record BridgeCoverageHunk(String relativePath, int ordinal, int startLine, int endLine,
        boolean navigable, String state, List<String> categoryIds) {
    public BridgeCoverageHunk {
        relativePath = BridgeProtocol.requireRelativePath(relativePath, "relativePath", 4096);
        BridgeProtocol.requireRange(ordinal, 1, 256, "ordinal");
        BridgeProtocol.requireRange(startLine, 1, Integer.MAX_VALUE, "startLine");
        BridgeProtocol.requireRange(endLine, startLine, Integer.MAX_VALUE, "endLine");
        state = BridgeProtocol.requireText(state, "state", 32);
        categoryIds = BridgeProtocol.copyStrings(categoryIds, "categoryIds", 3, 64);
    }
}
