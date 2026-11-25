package com.bricktools.storage;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.split;
import static com.bricktools.util.Values.toInt;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * CRUD for collection rules and adding parts to storages/drawers.
 */
@Service
public class CollectionRuleService {

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

  private final Path collectionFile;
  private final StorageService storageService;
  private final PartCatalogService catalogService;

  public CollectionRuleService(BrickToolsProperties props, StorageService storageService,
                               PartCatalogService catalogService) {
    this.collectionFile = props.dataPath().resolve("bt/part_collection.csv");
    this.storageService = storageService;
    this.catalogService = catalogService;
  }

  // ============================================================================
  // Collection rules
  // ============================================================================

  public synchronized List<Map<String, Object>> collectionRows() throws IOException {
    List<Map<String, String>> rows = loadCollection();
    List<Map<String, Object>> result = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      result.add(collectionEntry(i, rows.get(i)));
    }
    return result;
  }

  public synchronized Map<String, Object> createCollectionRow(PartCollectionRequest request)
      throws IOException {
    List<Map<String, String>> rows = loadCollection();
    Map<String, String> row = toCollectionRow(request);
    ensureUnique(rows, row, -1);
    rows.add(row);
    Csv.write(collectionFile, COLLECTION_HEADERS, rows);
    return collectionEntry(rows.size() - 1, row);
  }

  public synchronized Map<String, Object> updateCollectionRow(int index, PartCollectionRequest request)
      throws IOException {
    List<Map<String, String>> rows = loadCollection();
    if (index < 0 || index >= rows.size()) {
      throw new IllegalArgumentException("Unknown part collection row " + index);
    }
    Map<String, String> row = toCollectionRow(request);
    ensureUnique(rows, row, index);
    rows.set(index, row);
    Csv.write(collectionFile, COLLECTION_HEADERS, rows);
    return collectionEntry(index, row);
  }

  public synchronized void deleteCollectionRow(int index) throws IOException {
    List<Map<String, String>> rows = loadCollection();
    if (index < 0 || index >= rows.size()) {
      throw new IllegalArgumentException("Unknown part collection row " + index);
    }
    rows.remove(index);
    Csv.write(collectionFile, COLLECTION_HEADERS, rows);
  }

  // ============================================================================
  // Add parts to a storage / drawer
  // ============================================================================

  public synchronized int addParts(String storageId, String drawer, List<String> partNumbers)
      throws IOException {
    Catalog data = catalogService.catalog();
    Map<String, String> storage = storageService.requireStorage(storageId);
    String drawerValue = clean(drawer);
    storageService.validateDrawer(storage, drawerValue);

    List<String> numbers = cleanPartNumbers(partNumbers);
    if (numbers.isEmpty()) {
      throw new IllegalArgumentException("No part number provided");
    }
    List<Map<String, String>> rows = loadCollection();
    int added = 0;
    for (String partNumber : numbers) {
      if (!data.parts.containsKey(partNumber)) {
        throw new IllegalArgumentException("Unknown part number " + partNumber);
      }
      Map<String, String> existing = null;
      for (Map<String, String> row : rows) {
        if (clean(row.get("StorageId")).equalsIgnoreCase(clean(storage.get("Id")))
            && clean(row.get("Drawer")).equals(drawerValue)
            && normalizeParts(row.get("Parts")).equals(partNumber)
            && isAnyColors(row.get("Colors"))) {
          existing = row;
          break;
        }
      }
      if (existing != null) {
        // A blank amount still represents one part: incrementing nothing would silently lose the addition.
        Integer stored = toInt(existing.get("Amount"));
        int amount = stored == null || stored < 1 ? 1 : stored;
        existing.put("Amount", String.valueOf(amount + 1));
        added++;
        continue;
      }
      Map<String, String> row = new LinkedHashMap<>();
      row.put("Parts", partNumber);
      row.put("Colors", "Any");
      row.put("StorageId", clean(storage.get("Id")));
      row.put("Drawer", drawerValue);
      row.put("Amount", "1");
      rows.add(row);
      added++;
    }
    Csv.write(collectionFile, COLLECTION_HEADERS, rows);
    return added;
  }

  List<Map<String, String>> loadCollection() throws IOException {
    return load(collectionFile);
  }

  private static List<Map<String, String>> load(Path file) throws IOException {
    List<Map<String, String>> rows = new ArrayList<>();
    for (Map<String, String> row : Csv.read(file)) {
      Map<String, String> cleaned = new LinkedHashMap<>();
      row.forEach((column, value) -> cleaned.put(column, clean(value)));
      rows.add(cleaned);
    }
    return rows;
  }

  private Map<String, String> toCollectionRow(PartCollectionRequest request) throws IOException {
    String parts = normalizeParts(request == null ? null : request.parts());
    String colors = normalizeColors(request == null ? null : request.colors());
    String storageId = clean(request == null ? null : request.storageId());
    String drawer = clean(request == null ? null : request.drawer());
    String amount = clean(request == null ? null : request.amount());
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("Parts are required");
    }
    if (colors.isEmpty()) {
      throw new IllegalArgumentException("Colors are required");
    }
    if (storageId.isEmpty()) {
      throw new IllegalArgumentException("Storage is required");
    }
    if (amount.isEmpty()) {
      throw new IllegalArgumentException("Amount is required");
    }
    Map<String, String> storage = storageService.requireStorage(storageId);
    storageService.validateDrawer(storage, drawer);
    Map<String, String> row = new LinkedHashMap<>();
    row.put("Parts", parts);
    row.put("Colors", colors);
    row.put("StorageId", clean(storage.get("Id")));
    row.put("Drawer", drawer);
    row.put("Amount", amount);
    return row;
  }

  private static void ensureUnique(List<Map<String, String>> rows, Map<String, String> row, int index) {
    for (int i = 0; i < rows.size(); i++) {
      if (i == index) {
        continue;
      }
      Map<String, String> other = rows.get(i);
      if (clean(other.get("StorageId")).equalsIgnoreCase(row.get("StorageId"))
          && clean(other.get("Drawer")).equals(row.get("Drawer"))
          && normalizeParts(other.get("Parts")).equals(row.get("Parts"))
          && normalizeColors(other.get("Colors")).equals(row.get("Colors"))) {
        throw new IllegalArgumentException(
            "A row with the same parts and colors already exists in this drawer");
      }
    }
  }

  static Map<String, Object> collectionEntry(int index, Map<String, String> row) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("index", index);
    entry.put("parts", clean(row.get("Parts")));
    entry.put("colors", clean(row.get("Colors")));
    entry.put("storageId", clean(row.get("StorageId")));
    entry.put("drawer", clean(row.get("Drawer")));
    entry.put("amount", clean(row.get("Amount")));
    return entry;
  }

  private static List<String> cleanPartNumbers(List<String> partNumbers) {
    LinkedHashSet<String> numbers = new LinkedHashSet<>();
    if (partNumbers != null) {
      for (String value : partNumbers) {
        String number = clean(value);
        if (!number.isEmpty()) {
          numbers.add(number);
        }
      }
    }
    return new ArrayList<>(numbers);
  }

  private static String normalizeParts(String value) {
    return String.join(";", split(value));
  }

  private static String normalizeColors(String value) {
    List<String> tokens = new ArrayList<>();
    for (String token : split(value)) {
      tokens.add(canonicalKeyword(token));
    }
    return String.join(";", tokens);
  }

  static String canonicalKeyword(String token) {
    if (token.equalsIgnoreCase("Any")) {
      return "Any";
    }
    if (token.equalsIgnoreCase("Other")) {
      return "Other";
    }
    if (token.equalsIgnoreCase("Decorated")) {
      return "Decorated";
    }
    return token;
  }

  private static boolean isAnyColors(String value) {
    return split(value).stream().anyMatch(token -> token.equalsIgnoreCase("Any"));
  }
}
