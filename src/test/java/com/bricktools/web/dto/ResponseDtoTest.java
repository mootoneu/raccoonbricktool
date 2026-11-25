package com.bricktools.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResponseDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void storageViewKeepsTheApiKeys() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", "A");
    row.put("model", "Cabinet");
    row.put("drawersCount", 6);
    row.put("location", "Desk");
    row.put("description", "Bricks");
    row.put("labelSize", "60x10");
    row.put("order", 2);
    row.put("rowCount", 12L);

    JsonNode json = mapper.valueToTree(StorageView.from(row));

    assertThat(json.get("id").asText()).isEqualTo("A");
    assertThat(json.get("drawersCount").asInt()).isEqualTo(6);
    assertThat(json.get("rowCount").asLong()).isEqualTo(12);
    assertThat(json.get("order").asInt()).isEqualTo(2);
  }

  @Test
  void collectionRuleViewKeepsTheApiKeys() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("index", 3);
    row.put("parts", "3001");
    row.put("colors", "Any");
    row.put("storageId", "A");
    row.put("drawer", "a");
    row.put("amount", "2");

    JsonNode json = mapper.valueToTree(CollectionRuleView.from(row));

    assertThat(json.get("index").asInt()).isEqualTo(3);
    assertThat(json.get("parts").asText()).isEqualTo("3001");
    assertThat(json.get("drawer").asText()).isEqualTo("a");
  }

  @Test
  void partAndPurchaseViewsKeepNullFieldsAndLists() {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("partNumber", "3001");
    info.put("name", "Brick");
    info.put("defaultColorId", null);
    info.put("colors", List.of(Map.of("colorId", 5)));

    JsonNode part = mapper.valueToTree(PartView.from(info));
    assertThat(part.has("defaultColorId")).isTrue();
    assertThat(part.get("defaultColorId").isNull()).isTrue();
    assertThat(part.get("colors").isArray()).isTrue();

    Map<String, Object> purchase = new LinkedHashMap<>();
    purchase.put("id", 1);
    purchase.put("setNumber", "75192");
    purchase.put("price", 700.0);
    purchase.put("owned", true);
    purchase.put("theme", null);

    JsonNode json = mapper.valueToTree(PurchaseView.from(List.of(purchase))).get(0);
    assertThat(json.get("price").asDouble()).isEqualTo(700.0);
    assertThat(json.get("owned").asBoolean()).isTrue();
    assertThat(json.has("theme")).isTrue();
    assertThat(json.get("theme").isNull()).isTrue();
  }
}
