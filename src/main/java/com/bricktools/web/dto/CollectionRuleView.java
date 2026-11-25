package com.bricktools.web.dto;

import java.util.List;
import java.util.Map;

/** Response for one rule of the part collection. */
public record CollectionRuleView(Integer index, String parts, String colors, String storageId, String drawer,
                                 String amount) {

  public static List<CollectionRuleView> from(List<Map<String, Object>> rows) {
    return rows.stream().map(CollectionRuleView::from).toList();
  }

  public static CollectionRuleView from(Map<String, Object> row) {
    return new CollectionRuleView(
        Views.integer(row, "index"),
        Views.string(row, "parts"),
        Views.string(row, "colors"),
        Views.string(row, "storageId"),
        Views.string(row, "drawer"),
        Views.string(row, "amount"));
  }
}
