package dev.codedefense.terminal;
import static org.junit.jupiter.api.Assertions.*;
import dev.codedefense.interview.InterviewCancelledException;
import java.util.concurrent.atomic.AtomicInteger;
import org.jline.reader.*;
import org.junit.jupiter.api.Test;
class JLineUserInputTest {
 @Test void initializesReaderLazilyOnceAndReturnsInput() {
   AtomicInteger built=new AtomicInteger(); JLineUserInput input=new JLineUserInput(() -> { built.incrementAndGet(); return prompt -> "answer"; });
   assertEquals(0,built.get()); assertEquals("answer",input.readAnswer("> ")); assertEquals("answer",input.readAnswer("> ")); assertEquals(1,built.get());
 }
 @Test void mapsInterruptAndEofToSafeCancellation() {
   for (RuntimeException failure : new RuntimeException[]{new UserInterruptException(""), new EndOfFileException()}) {
     JLineUserInput input=new JLineUserInput(() -> prompt -> { throw failure; });
     InterviewCancelledException error=assertThrows(InterviewCancelledException.class,()->input.readAnswer("> "));
     assertEquals("Session cancelled. No report was generated.",error.getMessage());
   }
 }
}
