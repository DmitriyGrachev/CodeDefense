package dev.codedefense.application;

import dev.codedefense.cli.ExitCodes;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.passport.ChangePassportStore;
import dev.codedefense.passport.PassportTerminalRenderer;
import dev.codedefense.passport.PassportStatusJsonCodec;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;

public final class ShowLatestChangePassportUseCase {
    private final ChangePassportStore store;
    private final ChangePassportVerifier verifier;
    private final PassportTerminalRenderer renderer;

    public ShowLatestChangePassportUseCase(ChangePassportStore store,
            ChangePassportVerifier verifier, PassportTerminalRenderer renderer) {
        this.store = Objects.requireNonNull(store, "store");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public int show(Path repository, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        var latest = store.readLatest();
        if (latest.isEmpty()) {
            out.println("No Change Passport is available yet.");
            return ExitCodes.SUCCESS;
        }
        PassportStatus status = verifier.verify(repository)
                .map(value -> value.status()).orElse(PassportStatus.EXPIRED);
        renderer.render(latest.orElseThrow().receipt(), status, out);
        return ExitCodes.SUCCESS;
    }

    public int showJson(Path repository, PrintWriter out, PrintWriter err) {
        Objects.requireNonNull(repository); Objects.requireNonNull(out); Objects.requireNonNull(err);
        var latest = store.readLatest();
        if (latest.isEmpty()) {
            out.print("{\"protocolVersion\":1,\"present\":false}\n"); out.flush();
            return ExitCodes.SUCCESS;
        }
        PassportStatus status = verifier.verify(repository).map(value -> value.status()).orElse(PassportStatus.EXPIRED);
        var receipt = latest.orElseThrow().receipt();
        PassportStatusView view = new PassportStatusView(1, true, status, receipt.changeKind().name(),
                receipt.diffFingerprint().substring(0, 12), receipt.focus().cliName(), receipt.attemptNumber(),
                receipt.overallScore(), receipt.readiness().name(), receipt.categories());
        out.print(new String(new PassportStatusJsonCodec().encode(view), java.nio.charset.StandardCharsets.UTF_8));
        out.flush();
        return ExitCodes.SUCCESS;
    }
}
