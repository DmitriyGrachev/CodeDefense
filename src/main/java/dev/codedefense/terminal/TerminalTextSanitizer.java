package dev.codedefense.terminal;
import java.util.regex.Pattern;
public final class TerminalTextSanitizer {
 private static final Pattern OSC=Pattern.compile("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)");
 private static final Pattern CSI=Pattern.compile("(?:\\u001B\\[|\\u009B)[0-?]*[ -/]*[@-~]");
 private TerminalTextSanitizer() {}
 public static String singleLine(String value) {
  if(value==null) return "";
  String normalized=CSI.matcher(OSC.matcher(value).replaceAll("")).replaceAll("").replace("\r\n","\n").replace('\r','\n');
  StringBuilder result=new StringBuilder(normalized.length());
  normalized.codePoints().forEach(cp->{if(cp=='\n'||cp=='\t'||cp==0x2028||cp==0x2029)result.append(' '); else if(!Character.isISOControl(cp)&&!bidi(cp))result.appendCodePoint(cp);});
  return result.toString().strip();
 }
 private static boolean bidi(int cp){return cp==0x061C||(cp>=0x200E&&cp<=0x200F)||(cp>=0x202A&&cp<=0x202E)||(cp>=0x2066&&cp<=0x2069);}
}
