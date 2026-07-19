package dev.codedefense.application;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.passport.ChangePassportPersistenceException;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportReceiptJsonCodec;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class ExportChangePassportUseCase {
    private final ChangePassportStore store;
    private final PassportReceiptJsonCodec codec;

    public ExportChangePassportUseCase(ChangePassportStore store, PassportReceiptJsonCodec codec) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public int export(Path repository, Path output, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        var latest = store.readLatest();
        if (latest.isEmpty()) {
            out.println("No Change Passport is available to export.");
            return ExitCodes.SUCCESS;
        }
        Path target = output.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            Path parent = target.getParent();
            if (parent == null || Files.isSymbolicLink(parent)
                    || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("unsafe output parent");
            }
            parent.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("output exists");
            }
            byte[] bytes = codec.encode(latest.orElseThrow().receipt());
            temporary = Files.createTempFile(parent, ".codedefense-export-", ".tmp");
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            temporary = null;
            out.println("Change Passport exported: " + target.getFileName());
            return ExitCodes.SUCCESS;
        } catch (IOException | RuntimeException exception) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
            throw ChangePassportPersistenceException.saveFailure();
        }
    }
}
