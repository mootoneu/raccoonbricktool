package com.bricktools.web.dto;

import java.util.List;
import java.util.Map;

/** Response for the part information modal. */
public record PartView(String partNumber, String name, String size, String material, Boolean hasAlternates,
                       Integer stars, Boolean inMainParts, Integer categoryId, String categoryName,
                       Boolean categoryHidden, Integer defaultColorId, List<Map<String, Object>> colors) {

  public static PartView from(Map<String, Object> row) {
    return new PartView(
        Views.string(row, "partNumber"),
        Views.string(row, "name"),
        Views.string(row, "size"),
        Views.string(row, "material"),
        Views.bool(row, "hasAlternates"),
        Views.integer(row, "stars"),
        Views.bool(row, "inMainParts"),
        Views.integer(row, "categoryId"),
        Views.string(row, "categoryName"),
        Views.bool(row, "categoryHidden"),
        Views.integer(row, "defaultColorId"),
        Views.list(row, "colors"));
  }
}
