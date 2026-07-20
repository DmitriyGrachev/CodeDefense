package dev.codedefense.application;
import java.io.PrintWriter;import java.nio.file.Path;
@FunctionalInterface public interface RetryChangeDefenseRunner {int retry(String attemptId,Path repository,boolean dryRun,boolean yes,PrintWriter out,PrintWriter err);}
