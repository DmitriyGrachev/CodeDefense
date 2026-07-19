package dev.codedefense.cli;

import dev.codedefense.terminal.ConfirmationPrompt;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.Objects;

/** Reads one confirmation line from Picocli's configured input without closing it. */
final class PicocliConfirmationPrompt implements ConfirmationPrompt {
    private final Reader input;

    PicocliConfirmationPrompt(Reader input) {
        this.input = Objects.requireNonNull(input, "Command input");
    }

    @Override
    public boolean confirm(String prompt) {
        StringBuilder line = new StringBuilder();
        try {
            for (int value; (value = input.read()) >= 0;) {
                if (value == '\n' || value == '\r') {
                    break;
                }
                line.append((char) value);
            }
        } catch (IOException exception) {
            return false;
        }
        return switch (line.toString().trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes" -> true;
            default -> false;
        };
    }
}
