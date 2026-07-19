package dev.codedefense.application;

import dev.codedefense.change.GitChangeAdapters;
import dev.codedefense.change.GitChangeSource;
import dev.codedefense.domain.ChangePassport;
import dev.codedefense.domain.ChangeSelector;
import dev.codedefense.domain.GitChange;
import dev.codedefense.domain.InterviewSession;
import dev.codedefense.domain.PassportStatus;
import dev.codedefense.domain.ProjectAnalysis;
import dev.codedefense.domain.DefenseFocus;
import dev.codedefense.passport.ChangePassportStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class GitChangePassportService {
    private final GitChangeSource source;
    private final ChangePassportStore store;
    private final Clock clock;
    private final String model;
    public GitChangePassportService(GitChangeSource source, ChangePassportStore store, Clock clock, String model) {
        this.source = Objects.requireNonNull(source); this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock); this.model = Objects.requireNonNull(model);
    }
    public SavedChangePassport createAndSave(ChangeSelector selector, GitChange before,
            ProjectAnalysis analysis, InterviewSession session) {
        return createAndSave(selector, before, analysis, session, DefenseFocus.BALANCED);
    }
    public SavedChangePassport createAndSave(ChangeSelector selector, GitChange before,
            ProjectAnalysis analysis, InterviewSession session, DefenseFocus focus) {
        var current = source.captureIdentity(before.repositoryRoot(), selector);
        PassportStatus status = before.identity().equals(current) ? PassportStatus.CURRENT : PassportStatus.EXPIRED;
        ChangePassport passport = new ChangePassport(GitChangeAdapters.asStagedChange(before), before.kind(),
                before.targetIdentity(), analysis, session, Instant.now(clock), model, status, focus);
        return new SavedChangePassport(store.save(passport), status);
    }
}
