package com.bricktools.web.dto;

import java.util.List;
import java.util.Map;

/** Response for a set purchase with its derived image and price information. */
public record PurchaseView(
    Integer id,
    String setNumber,
    String name,
    Integer numParts,
    Integer year,
    String theme,
    String themeImageUrl,
    String themeColor,
    String vendorImageUrl,
    String broughtByImageUrl,
    String broughtByColor,
    String ownerImageUrl,
    String ownerColor,
    Double officialPrice,
    Double price,
    Double promo,
    String purchaseDate,
    String vendor,
    String broughtBy,
    String owner,
    Integer partsMissing,
    Double complete,
    Boolean owned,
    String reason,
    String imageUrl) {

  public static List<PurchaseView> from(List<Map<String, Object>> rows) {
    return rows.stream().map(PurchaseView::from).toList();
  }

  public static PurchaseView from(Map<String, Object> row) {
    return new PurchaseView(
        Views.integer(row, "id"),
        Views.string(row, "setNumber"),
        Views.string(row, "name"),
        Views.integer(row, "numParts"),
        Views.integer(row, "year"),
        Views.string(row, "theme"),
        Views.string(row, "themeImageUrl"),
        Views.string(row, "themeColor"),
        Views.string(row, "vendorImageUrl"),
        Views.string(row, "broughtByImageUrl"),
        Views.string(row, "broughtByColor"),
        Views.string(row, "ownerImageUrl"),
        Views.string(row, "ownerColor"),
        Views.decimal(row, "officialPrice"),
        Views.decimal(row, "price"),
        Views.decimal(row, "promo"),
        Views.string(row, "purchaseDate"),
        Views.string(row, "vendor"),
        Views.string(row, "broughtBy"),
        Views.string(row, "owner"),
        Views.integer(row, "partsMissing"),
        Views.decimal(row, "complete"),
        Views.bool(row, "owned"),
        Views.string(row, "reason"),
        Views.string(row, "imageUrl"));
  }
}
