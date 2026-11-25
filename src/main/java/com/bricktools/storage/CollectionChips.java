package com.bricktools.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the chip view models shown for the parts, categories, tags and colors of a collection row. */
public final class CollectionChips {

  private CollectionChips() {}

  public static List<Map<String, Object>> partChips(Catalog data, String value) {
    List<Map<String, Object>> chips = new ArrayList<>();
    for (String token : PartStorageService.split(value)) {
      if (token.startsWith("§") || token.startsWith("#")) {
        continue;
      }
      Catalog.PartInfo part = data.parts.get(token);
      Map<String, Object> chip = new LinkedHashMap<>();
      chip.put("partNumber", token);
      chip.put("name", part == null ? "" : part.name());
      chip.put("size", data.sizes.getOrDefault(token, ""));
      chip.put("categoryName", part == null ? "" : part.categoryName());
      Catalog.ColorInfo color = part == null ? null : Catalog.colorFor(data, part, null);
      chip.put("colorId", color == null ? null : PartStorageService.toInt(color.id()));
      chips.add(chip);
    }
    return chips;
  }

  public static List<Map<String, Object>> categoryChips(Catalog data, String value) {
    List<Map<String, Object>> chips = new ArrayList<>();
    for (String token : PartStorageService.split(value)) {
      if (!token.startsWith("§")) {
        continue;
      }
      String category = PartStorageService.clean(token.substring(1));
      String id = category;
      String name = data.categoryNames.getOrDefault(category, "");
      if (name.isEmpty()) {
        for (Map.Entry<String, String> entry : data.categoryNames.entrySet()) {
          if (entry.getValue().equalsIgnoreCase(category)) {
            id = entry.getKey();
            name = entry.getValue();
            break;
          }
        }
      }
      Map<String, Object> chip = new LinkedHashMap<>();
      chip.put("id", PartStorageService.toInt(id));
      chip.put("value", category);
      chip.put("name", name.isEmpty() ? category : name);
      chips.add(chip);
    }
    return chips;
  }

  public static List<String> tagChips(String value) {
    List<String> chips = new ArrayList<>();
    for (String token : PartStorageService.split(value)) {
      if (token.startsWith("#")) {
        String tag = PartStorageService.clean(token.substring(1));
        if (!tag.isEmpty()) {
          chips.add(tag);
        }
      }
    }
    return chips;
  }

  public static List<Map<String, Object>> colorChips(Catalog data, String value) {
    List<Map<String, Object>> chips = new ArrayList<>();
    for (String token : PartStorageService.split(value)) {
      Map<String, Object> chip = new LinkedHashMap<>();
      if (PartStorageService.isNumeric(token)) {
        Catalog.ColorInfo color = data.colors.get(token);
        chip.put("id", PartStorageService.toInt(token));
        chip.put("name", color == null ? token : color.name());
        chip.put("rgb", color == null ? "" : color.rgb());
        chip.put("keyword", false);
      } else {
        chip.put("id", null);
        chip.put("name", PartStorageService.canonicalKeyword(token));
        chip.put("rgb", "");
        chip.put("keyword", true);
      }
      chips.add(chip);
    }
    return chips;
  }
}
