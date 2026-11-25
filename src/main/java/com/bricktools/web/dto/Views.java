package com.bricktools.web.dto;

import java.util.List;
import java.util.Map;

/** Small null-safe accessors used by the response DTO mappers. */
final class Views {

  private Views() {}

  static String string(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value == null ? null : String.valueOf(value);
  }

  static Integer integer(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value instanceof Number number ? number.intValue() : null;
  }

  static Long longValue(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value instanceof Number number ? number.longValue() : null;
  }

  static Double decimal(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value instanceof Number number ? number.doubleValue() : null;
  }

  static Boolean bool(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value instanceof Boolean flag ? flag : null;
  }

  @SuppressWarnings("unchecked")
  static <T> List<T> list(Map<String, Object> source, String key) {
    Object value = source.get(key);
    return value instanceof List<?> items ? (List<T>) items : List.of();
  }
}
