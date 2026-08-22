package io.github.micfabian.snapito.comparison;

import java.util.Map;
import java.util.Optional;

final class HtmlEntities {
  private static final Map<String, String> NAMED = Map.ofEntries(
    Map.entry("nbsp", " "),
    Map.entry("copy", "©"),
    Map.entry("reg", "®"),
    Map.entry("trade", "™"),
    Map.entry("hellip", "…"),
    Map.entry("mdash", "—"),
    Map.entry("ndash", "–"),
    Map.entry("lsquo", "‘"),
    Map.entry("rsquo", "’"),
    Map.entry("ldquo", "“"),
    Map.entry("rdquo", "”"),
    Map.entry("bull", "•"),
    Map.entry("middot", "·"),
    Map.entry("deg", "°"),
    Map.entry("plusmn", "±"),
    Map.entry("times", "×"),
    Map.entry("divide", "÷"),
    Map.entry("frac12", "½"),
    Map.entry("frac14", "¼"),
    Map.entry("frac34", "¾"),
    Map.entry("sup2", "²"),
    Map.entry("sup3", "³"),
    Map.entry("micro", "µ"),
    Map.entry("para", "¶"),
    Map.entry("sect", "§"),
    Map.entry("dagger", "†"),
    Map.entry("Dagger", "‡"),
    Map.entry("permil", "‰"),
    Map.entry("euro", "€"),
    Map.entry("pound", "£"),
    Map.entry("yen", "¥"),
    Map.entry("cent", "¢"),
    Map.entry("curren", "¤"),
    Map.entry("laquo", "«"),
    Map.entry("raquo", "»"),
    Map.entry("larr", "←"),
    Map.entry("uarr", "↑"),
    Map.entry("rarr", "→"),
    Map.entry("darr", "↓"),
    Map.entry("harr", "↔"),
    Map.entry("infin", "∞"),
    Map.entry("ne", "≠"),
    Map.entry("le", "≤"),
    Map.entry("ge", "≥"),
    Map.entry("minus", "−"),
    Map.entry("shy", "­"),
    Map.entry("ensp", " "),
    Map.entry("emsp", " "),
    Map.entry("thinsp", " "));

  private HtmlEntities() {
  }

  static Optional<String> resolve(String name) {
    return Optional.ofNullable(NAMED.get(name));
  }
}
