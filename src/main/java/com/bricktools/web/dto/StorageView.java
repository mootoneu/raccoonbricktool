package com.bricktools.web.dto;

import java.util.List;
import java.util.Map;

/** Response for a physical part storage. */
public record StorageView(String id, String model, Integer drawersCount, String location, String description,
                          String labelSize, Integer order, Long rowCount) {

  public static List<StorageView> from(List<Map<String, Object>> rows) {
    return rows.stream().map(StorageView::from).toList();
  }

  public static StorageView from(Map<String, Object> row) {
    return new StorageView(
        Views.string(row, "id"),
        Views.string(row, "model"),
        Views.integer(row, "drawersCount"),
        Views.string(row, "location"),
        Views.string(row, "description"),
        Views.string(row, "labelSize"),
        Views.integer(row, "order"),
        Views.longValue(row, "rowCount"));
  }
}
