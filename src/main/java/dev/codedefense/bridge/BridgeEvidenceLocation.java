package dev.codedefense.bridge;

/** Source-free location metadata exposed through bridge protocol 2. */
public record BridgeEvidenceLocation(String relativePath, int startLine, int endLine) {
    public BridgeEvidenceLocation {
        relativePath = BridgeProtocol.requireRelativePath(relativePath, "relativePath", 4096);
        BridgeProtocol.requireRange(startLine, 1, Integer.MAX_VALUE, "startLine");
        BridgeProtocol.requireRange(endLine, startLine, Integer.MAX_VALUE, "endLine");
    }
}
