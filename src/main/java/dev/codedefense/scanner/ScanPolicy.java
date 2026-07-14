package dev.codedefense.scanner;

public record ScanPolicy() {
    public static ScanPolicy defaults() {
        return new ScanPolicy();
    }
}
