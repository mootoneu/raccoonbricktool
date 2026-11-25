package com.bricktools.storage;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.isNumeric;
import static com.bricktools.util.Values.split;
import static com.bricktools.util.Values.toInt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Expands storages into drawers and searches their resolved contents.
 */
@Service
public class DrawerService {

  private static final List<String> STORAGE_HEADERS =
      List.of("Id", "Model", "DrawersCount", "Location", "Description", "LabelSize", "Order");
  private static final List<String> COLLECTION_HEADERS =
      List.of("Parts", "Colors", "StorageId", "Drawer", "Amount");
  private static final String DEFAULT_LABEL_SIZE = "60x10";
  private static final Pattern LABEL_SIZE_PATTERN = Pattern.compile(
      "(\\d+(?:[.,]\\d+)?)\\s*[x\\u00d7*]\\s*(\\d+(?:[.,]\\d+)?)");
  private static final int MATCH_LIMIT = 3000;
  private static final int DRAWER_MATCH_LIMIT = 200;
  private static final int MAX_DRAWERS = 52;

  private final StorageService storageService;
  private final CollectionRuleService collectionRules;
  private final PartCatalogService catalogService;

  public DrawerService(StorageService storageService, CollectionRuleService collectionRules,
                       PartCatalogService catalogService) {
    this.storageService = storageService;
    this.collectionRules = collectionRules;
    this.catalogService = catalogService;
  }

  // ============================================================================
  // Drawer expansion and search
  // ============================================================================

  /**
   * Returns one card per drawer of every storage. When a query is given, only the drawers that
   * contain at least one matching part are returned and their matching parts are resolved.
   */
  public synchronized List<Map<String, Object>> drawers(String query) throws IOException {
    Catalog data = catalogService.catalog();
    String cleaned = clean(query);
    List<Map<String, String>> storages = storageService.loadStorages();
    List<Map<String, String>> collection = collectionRules.loadCollection();
    MatchContext context = cleaned.isEmpty() ? null : match(data, cleaned, collection);
    Map<String, List<Integer>> rowsByDrawer = indexRowsByDrawer(collection);
    storages.sort(StorageService.STORAGE_ORDER);

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, String> storage : storages) {
      String storageId = clean(storage.get("Id"));
      for (String drawer : storageService.drawerLabels(storage, collection)) {
        List<Integer> indexes = rowsByDrawer.getOrDefault(drawerKey(storageId, drawer), List.of());
        List<Map<String, String>> rows = new ArrayList<>(indexes.size());
        for (int index : indexes) {
          rows.add(collection.get(index));
        }

        List<Map<String, Object>> matched = new ArrayList<>();
        if (context != null) {
          matched = matchedParts(data, rows, context);
          if (matched.isEmpty()) {
            continue;
          }
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("storageId", storageId);
        card.put("storageModel", clean(storage.get("Model")));
        card.put("storageLocation", clean(storage.get("Location")));
        card.put("drawersCount", toInt(storage.get("DrawersCount")));
        card.put("drawer", drawer);
        card.put("rows", resolvedRows(data, collection, indexes, rows));
        card.put("matchedParts", matched);
        result.add(card);
      }
    }
    return result;
  }

  /** Groups collection rows by storage and drawer once per request instead of rescanning them for every drawer. */
  private static Map<String, List<Integer>> indexRowsByDrawer(List<Map<String, String>> collection) {
    Map<String, List<Integer>> index = new HashMap<>();
    for (int i = 0; i < collection.size(); i++) {
      Map<String, String> row = collection.get(i);
      String key = drawerKey(clean(row.get("StorageId")), clean(row.get("Drawer")));
      index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
    }
    return index;
  }

  private static String drawerKey(String storageId, String drawer) {
    return storageId.toLowerCase(Locale.ROOT) + "\u0000" + drawer;
  }

  /**
   * Finds the parts matching the query and indexes, for each of them, the collection rows that explicitly list a
   * color. Building this once avoids rescanning every collection row for every drawer and every candidate part.
   */
  private MatchContext match(Catalog data, String query, List<Map<String, String>> allRows) {
    String needle = query.toLowerCase(Locale.ROOT);
    Set<String> colorIds = new LinkedHashSet<>();
    for (Catalog.ColorInfo color : data.colors.values()) {
      if (color.id().equals(query) || color.name().toLowerCase(Locale.ROOT).contains(needle)) {
        colorIds.add(color.id());
      }
    }

    Map<String, String> colorByPart = new HashMap<>();
    LinkedHashSet<String> numbers = new LinkedHashSet<>();
    for (Catalog.PartInfo part : data.parts.values()) {
      if (numbers.size() >= MATCH_LIMIT) {
        break;
      }
      if (part.number().toLowerCase(Locale.ROOT).contains(needle)
          || part.name().toLowerCase(Locale.ROOT).contains(needle)
          || part.categoryName().toLowerCase(Locale.ROOT).contains(needle)) {
        numbers.add(part.number());
      }
    }
    if (!colorIds.isEmpty()) {
      for (Map.Entry<String, List<String>> entry : data.partColorIds.entrySet()) {
        if (numbers.size() >= MATCH_LIMIT) {
          break;
        }
        for (String colorId : entry.getValue()) {
          if (colorIds.contains(colorId)) {
            numbers.add(entry.getKey());
            colorByPart.putIfAbsent(entry.getKey(), colorId);
            break;
          }
        }
      }
    }
    Map<String, Set<Map<String, String>>> explicitRows = new HashMap<>();
    for (String number : numbers) {
      Catalog.PartInfo part = data.parts.get(number);
      if (part == null || part.decorated()) {
        continue;
      }
      Set<Map<String, String>> matching = Collections.newSetFromMap(new IdentityHashMap<>());
      for (Map<String, String> row : allRows) {
        if (partsMatch(data, row.get("Parts"), part) && hasNumericColor(row.get("Colors"))) {
          matching.add(row);
        }
      }
      explicitRows.put(number, matching);
    }
    return new MatchContext(numbers, colorByPart, explicitRows);
  }

  private static boolean hasNumericColor(String value) {
    for (String token : split(value)) {
      if (isNumeric(token)) {
        return true;
      }
    }
    return false;
  }

  private List<Map<String, Object>> matchedParts(Catalog data, List<Map<String, String>> rows, MatchContext context) {
    List<Map<String, Object>> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String number : context.numbers()) {
      if (result.size() >= DRAWER_MATCH_LIMIT) {
        break;
      }
      Catalog.PartInfo part = data.parts.get(number);
      if (part == null) {
        continue;
      }
      for (Map<String, String> row : rows) {
        if (!matchesRow(data, row, part, context.colorByPart().get(number), context)) {
          continue;
        }
        String queryColor = context.colorByPart().get(number);
        Catalog.ColorInfo color = Catalog.colorFor(data, part, queryColor);
        String key = number + "\u0000" + (color == null ? "" : color.id());
        if (seen.add(key)) {
          result.add(partEntry(data, part, color));
        }
        break;
      }
    }
    return result;
  }

  private boolean matchesRow(Catalog data, Map<String, String> row, Catalog.PartInfo part, String requiredColor,
                             MatchContext context) {
    if (!partsMatch(data, row.get("Parts"), part)) {
      return false;
    }
    if (requiredColor != null && !colorsOf(data, part).contains(requiredColor)) {
      return false;
    }
    return colorsMatch(data, row, part, context);
  }

  private boolean partsMatch(Catalog data, String value, Catalog.PartInfo part) {
    List<String> tokens = split(value);
    if (tokens.isEmpty()) {
      return false;
    }
    for (String token : tokens) {
      if (token.startsWith("§")) {
        String category = clean(token.substring(1));
        if (!category.isEmpty() && categoryMatches(data, category, part.categoryId())) {
          return true;
        }
      } else if (token.startsWith("#")) {
        String tag = clean(token.substring(1)).toLowerCase(Locale.ROOT);
        if (!tag.isEmpty()
            && (part.name().toLowerCase(Locale.ROOT).contains(tag)
                || part.number().toLowerCase(Locale.ROOT).equals(tag))) {
          return true;
        }
      } else if (token.equalsIgnoreCase(part.number())) {
        return true;
      }
    }
    return false;
  }

  private boolean categoryMatches(Catalog data, String category, String categoryId) {
    if (category.equals(categoryId)) {
      return true;
    }
    return data.categoryNames.entrySet().stream()
        .anyMatch(entry -> entry.getKey().equals(categoryId)
            && entry.getValue().equalsIgnoreCase(category));
  }

  private boolean colorsMatch(Catalog data, Map<String, String> row, Catalog.PartInfo part, MatchContext context) {
    List<String> tokens = split(row.get("Colors"));
    if (tokens.isEmpty()) {
      return false;
    }
    for (String token : tokens) {
      if (token.equalsIgnoreCase("Any")) {
        return true;
      }
      if (token.equalsIgnoreCase("Decorated")) {
        if (part.decorated()) {
          return true;
        }
      } else if (token.equalsIgnoreCase("Other")) {
        if (!part.decorated() && !explicitlyListed(part, row, context)) {
          return true;
        }
      } else if (colorsOf(data, part).contains(token)) {
        return true;
      }
    }
    return false;
  }

  /** Uses the per-request index instead of rescanning the whole collection for every candidate part and row. */
  private boolean explicitlyListed(Catalog.PartInfo part, Map<String, String> current, MatchContext context) {
    for (Map<String, String> row : context.explicitRows().getOrDefault(part.number(), Set.of())) {
      if (row != current) {
        return true;
      }
    }
    return false;
  }

  private List<String> colorsOf(Catalog data, Catalog.PartInfo part) {
    return data.partColorIds.getOrDefault(part.number(), List.of());
  }

  private List<Map<String, Object>> resolvedRows(Catalog data, List<Map<String, String>> allRows,
                                                 List<Integer> indexes, List<Map<String, String>> rows) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      Map<String, String> row = rows.get(i);
      Map<String, Object> entry = collectionRules.collectionEntry(indexes.get(i), row);
      entry.put("partChips", CollectionChips.partChips(data, row.get("Parts")));
      entry.put("categoryChips", CollectionChips.categoryChips(data, row.get("Parts")));
      entry.put("tagChips", CollectionChips.tagChips(row.get("Parts")));
      entry.put("colorChips", CollectionChips.colorChips(data, row.get("Colors")));
      result.add(entry);
    }
    return result;
  }

  private Map<String, Object> partEntry(Catalog data, Catalog.PartInfo part, Catalog.ColorInfo color) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("partNumber", part.number());
    entry.put("name", part.name());
    entry.put("size", data.sizes.getOrDefault(part.number(), ""));
    entry.put("categoryId", toInt(part.categoryId()));
    entry.put("categoryName", part.categoryName());
    entry.put("colorId", color == null ? null : toInt(color.id()));
    entry.put("colorName", color == null ? "" : color.name());
    entry.put("colorRgb", color == null ? "" : color.rgb());
    return entry;
  }

  private record MatchContext(Set<String> numbers, Map<String, String> colorByPart,
                              Map<String, Set<Map<String, String>>> explicitRows) {
  }
}
