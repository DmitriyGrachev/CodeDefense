package dev.codedefense.terminal;
public final class ConsoleConfirmationPrompt implements ConfirmationPrompt {
    @Override public boolean confirm(String prompt) { java.io.Console console = System.console(); if (console == null) return false; String line = console.readLine(); return line != null && (line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes")); }
}
