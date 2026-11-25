package com.bricktools.util;

import java.util.ArrayList;
import java.util.List;

/** Small parsing helpers shared by the services that read CSV values. */
public final class Values {

  private Values() {}

  public static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  public static Integer toInt(String value) {
    String text = clean(value);
    if (text.isEmpty()) {
      return null;
    }
    try {
      return Integer.valueOf(text);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static int intOrZero(String value) {
    Integer parsed = toInt(value);
    return parsed == null ? 0 : parsed;
  }

  public static boolean isNumeric(String value) {
    return clean(value).matches("-?\\d+");
  }

  public static boolean toBoolean(String value) {
    return "true".equalsIgnoreCase(clean(value));
  }

  /** Splits a semicolon-separated list, trimming tokens and dropping empty ones. */
  public static List<String> split(String value) {
    List<String> tokens = new ArrayList<>();
    if (value == null) {
      return tokens;
    }
    for (String token : value.split(";")) {
      String cleaned = clean(token);
      if (!cleaned.isEmpty()) {
        tokens.add(cleaned);
      }
    }
    return tokens;
  }
}
