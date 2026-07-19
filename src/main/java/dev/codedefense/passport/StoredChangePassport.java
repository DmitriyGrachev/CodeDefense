package dev.codedefense.passport;

import dev.codedefense.domain.PassportReceipt;
import java.nio.file.Path;
import java.util.Objects;

public record StoredChangePassport(
        Path markdownPath,
        Path receiptPath,
        PassportReceipt receipt) {
    public StoredChangePassport {
        markdownPath = requireAbsolute(markdownPath, "markdownPath");
        receiptPath = requireAbsolute(receiptPath, "receiptPath");
        Objects.requireNonNull(receipt, "receipt");
        if (!markdownPath.getParent().equals(receiptPath.getParent())
                || !base(markdownPath).equals(base(receiptPath))) {
            throw new IllegalArgumentException("stored Passport artifacts must be paired");
        }
    }

    private static Path requireAbsolute(Path path, String field) {
        Objects.requireNonNull(path, field);
        path = path.toAbsolutePath().normalize();
        if (!path.isAbsolute()) throw new IllegalArgumentException(field + " must be absolute");
        return path;
    }

    private static String base(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override
    public String toString() {
        return "StoredChangePassport[receiptId=%s, status=%s]"
                .formatted(receipt.receiptId(), receipt.statusAtCreation());
    }
}
