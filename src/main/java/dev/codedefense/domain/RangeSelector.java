package dev.codedefense.domain;

public record RangeSelector(String baseRevision, String headRevision) implements ChangeSelector {
    public RangeSelector {
        baseRevision = RevisionText.requireSafe(baseRevision, "baseRevision");
        headRevision = RevisionText.requireSafe(headRevision, "headRevision");
    }
    public static RangeSelector parse(String value) {
        if (value == null) throw new NullPointerException("range");
        String[] parts = value.split("\\.\\.\\.", -1);
        if (parts.length != 2) throw new IllegalArgumentException("range must use BASE...HEAD");
        return new RangeSelector(parts[0], parts[1]);
    }
    @Override public ChangeKind kind() { return ChangeKind.RANGE; }
}
