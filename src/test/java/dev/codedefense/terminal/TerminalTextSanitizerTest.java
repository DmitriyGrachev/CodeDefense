package dev.codedefense.terminal;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class TerminalTextSanitizerTest {
 @Test void stripsTerminalAndBidiControlsButPreservesUnicode() {
   assertEquals("Hello red Привет 😀 next tab", TerminalTextSanitizer.singleLine("\u001b]0;title\u0007Hello \u001b[31mred\u001b[0m Привет 😀\r\nnext\t\u202Etab"));
 }
}
