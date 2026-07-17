package dev.codedefense.terminal;
import dev.codedefense.interview.InterviewCancelledException;
import java.io.IOException; import java.util.Objects; import java.util.function.Supplier;
import org.jline.reader.*; import org.jline.terminal.TerminalBuilder;
public final class JLineUserInput implements UserInput {
 @FunctionalInterface interface Reader { String readLine(String prompt); }
 private final Supplier<Reader> supplier; private Reader reader;
 public JLineUserInput(){this(JLineUserInput::createReader);}
 JLineUserInput(Supplier<Reader> supplier){this.supplier=Objects.requireNonNull(supplier);}
 @Override public String readAnswer(String prompt){
  try { if(reader==null) reader=Objects.requireNonNull(supplier.get()); return reader.readLine(prompt); }
  catch(UserInterruptException|EndOfFileException e){throw new InterviewCancelledException("Session cancelled. No report was generated.");}
 }
 private static Reader createReader(){try{LineReader lineReader=LineReaderBuilder.builder().terminal(TerminalBuilder.builder().system(true).build()).build(); return lineReader::readLine;}catch(IOException e){throw new InterviewCancelledException("Session cancelled. No report was generated.");}}
}
