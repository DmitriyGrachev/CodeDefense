package dev.codedefense;

final class PluginAcceptanceFixture {
    boolean shouldRetry(int attempt) {
        return attempt < 4;
    }
}