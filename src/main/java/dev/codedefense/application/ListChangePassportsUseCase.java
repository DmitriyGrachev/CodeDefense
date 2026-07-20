package dev.codedefense.application;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTerminalRenderer;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class ListChangePassportsUseCase {
    private final ChangePassportStore store;
    private final PassportTerminalRenderer renderer;

    public ListChangePassportsUseCase(ChangePassportStore store, PassportTerminalRenderer renderer) {
        this.store = Objects.requireNonNull(store, "store");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public int list(Path repository, int limit, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        renderer.renderList(store.list(limit), out);
        return ExitCodes.SUCCESS;
    }
}
