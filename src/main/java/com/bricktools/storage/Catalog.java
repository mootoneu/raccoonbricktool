package com.bricktools.storage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory catalogue of parts, colors and sizes loaded from the reference CSV files. */
public final class Catalog {

  final Map<String, PartInfo> parts = new LinkedHashMap<>();
  final Map<String, String> categoryNames = new LinkedHashMap<>();
  final Map<String, ColorInfo> colors = new LinkedHashMap<>();
  final Map<String, List<String>> partColorIds = new HashMap<>();
  final Map<String, String> defaultColors = new HashMap<>();
  final Map<String, String> sizes = new HashMap<>();

  public record PartInfo(String number, String name, String categoryId, String categoryName, boolean decorated) {
  }

  public record ColorInfo(String id, String name, String rgb) {
  }

  /** Returns the default color of a part, or the requested color when it is available. */
  static ColorInfo colorFor(Catalog data, PartInfo part, String requiredColor) {
    List<String> colorIds = data.partColorIds.getOrDefault(part.number(), List.of());
    String selected = requiredColor;
    if (selected == null || !colorIds.contains(selected)) {
      selected = data.defaultColors.get(part.number());
    }
    if (selected == null || (!colorIds.isEmpty() && !colorIds.contains(selected))) {
      selected = colorIds.isEmpty() ? null : colorIds.get(0);
    }
    return selected == null ? null : data.colors.get(selected);
  }
}
