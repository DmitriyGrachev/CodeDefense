package dev.codedefense.application;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTerminalRenderer;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class ShowPassportTimelineUseCase {
    private final ChangePassportStore store;
    private final PassportTerminalRenderer renderer;
    public ShowPassportTimelineUseCase(ChangePassportStore store, PassportTerminalRenderer renderer) {
        this.store = Objects.requireNonNull(store); this.renderer = Objects.requireNonNull(renderer);
    }
    public int show(Path ignoredRepository, PrintWriter out, PrintWriter err) {
        var latest = store.readLatest();
        if (latest.isEmpty()) { out.println("No Change Passport timeline is available."); return ExitCodes.SUCCESS; }
        var history = store.listByFingerprint(latest.orElseThrow().receipt().diffFingerprint(), 20);
        renderer.renderTimeline(history, out);
        return ExitCodes.SUCCESS;
    }
}
