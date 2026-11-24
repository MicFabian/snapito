package io.github.micfabian.snapito.comparison;

import io.github.micfabian.snapito.Comparison;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public class TextComparison implements Comparison {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private boolean ignoreWhitespace = true;
  private boolean ignoreCase = false;

  public TextComparison ignoringWhitespace(boolean ignoreWhitespace) {
    this.ignoreWhitespace = ignoreWhitespace;
    return this;
  }

  public TextComparison ignoringCase(boolean ignoreCase) {
    this.ignoreCase = ignoreCase;
    return this;
  }

  public boolean isIgnoreWhitespace() {
    return ignoreWhitespace;
  }

  public boolean isIgnoreCase() {
    return ignoreCase;
  }

  @Override
  public String fileExtension() {
    return "txt";
  }

  @Override
  public Object beforeComparison(Object input) {
    String text = String.valueOf(input);
    if (ignoreWhitespace) {
      text = WHITESPACE.matcher(text).replaceAll("");
    }
    if (ignoreCase) {
      text = text.toLowerCase(Locale.ROOT);
    }
    return text;
  }

  @Override
  public byte[] beforeStore(Object input) {
    return String.valueOf(input).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Object afterRestore(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
