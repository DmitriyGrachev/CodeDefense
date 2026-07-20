package dev.codedefense.ci;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.codedefense.ai.JdkProcessExecutor;
import dev.codedefense.change.GitCliChangeSource;
import dev.codedefense.change.GitCliStagedChangeSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommitPassportContinuityCheckerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void classifiesMatchingMissingAndMismatchedCommitTrailers() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("repo"));
        git(root, "init");
        git(root, "config", "user.email", "test@example.invalid");
        git(root, "config", "user.name", "CodeDefense Test");
        Path source = Files.createDirectories(root.resolve("src")).resolve("App.java");
        Files.writeString(source, "class App { int value = 0; }\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "base");
        String base = git(root, "rev-parse", "HEAD").trim();

        Files.writeString(source, "class App { int value = 1; }\n");
        git(root, "add", ".");
        String fingerprint = new GitCliStagedChangeSource(new JdkProcessExecutor())
                .captureIdentity(root).diffFingerprint();
        git(root, "commit", "-m", "matched", "-m", "CodeDefense-Passport: sha256:" + fingerprint);
        Files.writeString(source, "class App { int value = 2; }\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "missing");
        Files.writeString(source, "class App { int value = 3; }\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "mismatch", "-m", "CodeDefense-Passport: sha256:" + "f".repeat(64));

        var checker = new CommitPassportContinuityChecker(
                new GitCommitRangeReader(new JdkProcessExecutor()),
                new GitCliChangeSource(new JdkProcessExecutor()), new PassportTrailerParser());
        PassportContinuityResult result = checker.check(root, base, "HEAD");

        assertEquals(List.of(CiPassportStatus.MATCHED, CiPassportStatus.MISSING, CiPassportStatus.MISMATCH),
                result.commits().stream().map(CommitContinuityResult::status).toList());
        assertEquals(0, CiPassportPolicy.ADVISORY.exitCode(result));
        assertEquals(1, CiPassportPolicy.REQUIRED.exitCode(result));
    }

    @Test
    void classifiesMergeCommitWithoutTrailerAsMissingInsteadOfUnavailable() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("merge-repo"));
        git(root, "init");
        git(root, "config", "user.email", "test@example.invalid");
        git(root, "config", "user.name", "CodeDefense Test");
        Files.writeString(root.resolve("base.txt"), "base\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "base");
        String base = git(root, "rev-parse", "HEAD").trim();
        String baseBranch = git(root, "branch", "--show-current").trim();

        git(root, "switch", "-c", "feature");
        Files.writeString(root.resolve("feature.txt"), "feature\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "feature");
        git(root, "switch", baseBranch);
        Files.writeString(root.resolve("main.txt"), "main\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "main");
        git(root, "merge", "--no-ff", "feature", "-m", "merge feature");

        var checker = new CommitPassportContinuityChecker(
                new GitCommitRangeReader(new JdkProcessExecutor()),
                new GitCliChangeSource(new JdkProcessExecutor()), new PassportTrailerParser());
        PassportContinuityResult result = checker.check(root, base, "HEAD");

        assertEquals(List.of(CiPassportStatus.MISSING, CiPassportStatus.MISSING, CiPassportStatus.MISSING),
                result.commits().stream().map(CommitContinuityResult::status).toList());
        assertEquals(0, CiPassportPolicy.ADVISORY.exitCode(result));
    }

    private static String git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
