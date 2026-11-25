package com.bricktools.storage;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.toInt;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * CRUD for physical part storages and drawer naming/validation.
 */
@Service
public class StorageService {

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
  public static final Comparator<Map<String, String>> STORAGE_ORDER =
      Comparator.comparingInt((Map<String, String> row) -> orderOf(row))
          .thenComparing(row -> clean(row.get("Id")), String.CASE_INSENSITIVE_ORDER);

  private final Path storageFile;
  private final Path collectionFile;

  public StorageService(BrickToolsProperties props) {
    this.storageFile = props.dataPath().resolve("bt/part_storage.csv");
    this.collectionFile = props.dataPath().resolve("bt/part_collection.csv");
  }

  // ============================================================================
  // Storages
  // ============================================================================

  public synchronized List<Map<String, Object>> storages() throws IOException {
    List<Map<String, String>> rows = loadStorages();
    rows.sort(STORAGE_ORDER);
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, String> row : rows) {
      Map<String, Object> entry = storageEntry(row);
      long rowCount = Csv.read(collectionFile).stream()
          .filter(item -> clean(item.get("StorageId")).equalsIgnoreCase(clean(row.get("Id"))))
          .count();
      entry.put("rowCount", rowCount);
      result.add(entry);
    }
    return result;
  }

  public synchronized Map<String, Object> createStorage(PartStorageRequest request) throws IOException {
    List<Map<String, String>> rows = loadStorages();
    String id = clean(request == null ? null : request.id());
    if (id.isEmpty()) {
      throw new IllegalArgumentException("Storage id is required");
    }
    if (rows.stream().anyMatch(row -> clean(row.get("Id")).equalsIgnoreCase(id))) {
      throw new IllegalArgumentException("Storage id " + id + " already exists");
    }
    Map<String, String> row = toStorageRow(id, request, 0);
    rows.add(row);
    Csv.write(storageFile, STORAGE_HEADERS, rows);
    return storageEntry(row);
  }

  public synchronized Map<String, Object> updateStorage(String id, PartStorageRequest request)
      throws IOException {
    String key = clean(id);
    List<Map<String, String>> rows = loadStorages();
    for (int i = 0; i < rows.size(); i++) {
      if (clean(rows.get(i).get("Id")).equalsIgnoreCase(key)) {
        Map<String, String> row = toStorageRow(clean(rows.get(i).get("Id")), request,
            orderOf(rows.get(i)));
        rows.set(i, row);
        Csv.write(storageFile, STORAGE_HEADERS, rows);
        return storageEntry(row);
      }
    }
    throw new IllegalArgumentException("Unknown storage id " + key);
  }

  // ============================================================================
  // Loading and validation
  // ============================================================================

  List<Map<String, String>> loadStorages() throws IOException {
    List<Map<String, String>> rows = load(storageFile);
    for (Map<String, String> row : rows) {
      // Files written before the Order column existed default to 0.
      if (clean(row.get("Order")).isEmpty()) {
        row.put("Order", "0");
      }
      // Files written before the LabelSize column existed default to 60x10.
      if (clean(row.get("LabelSize")).isEmpty()) {
        row.put("LabelSize", DEFAULT_LABEL_SIZE);
      }
    }
    return rows;
  }

  private static int orderOf(Map<String, String> row) {
    Integer value = toInt(row.get("Order"));
    return value == null ? 0 : value;
  }

  Map<String, String> requireStorage(String storageId) throws IOException {
    String key = clean(storageId);
    if (key.isEmpty()) {
      throw new IllegalArgumentException("Storage id is required");
    }
    for (Map<String, String> row : loadStorages()) {
      if (clean(row.get("Id")).equalsIgnoreCase(key)) {
        return row;
      }
    }
    throw new IllegalArgumentException("Unknown storage id " + key);
  }

  /**
   * A storage with drawers accepts one of its generated labels, or an empty drawer for parts
   * that are not assigned to a specific drawer. A storage without drawers only accepts the
   * empty drawer.
   */
  static void validateDrawer(Map<String, String> storage, String drawer) {
    String storageId = clean(storage.get("Id"));
    Integer count = toInt(storage.get("DrawersCount"));
    if (count == null || count <= 0) {
      if (!drawer.isEmpty()) {
        throw new IllegalArgumentException("Storage " + storageId + " has no drawer");
      }
      return;
    }
    if (!drawer.isEmpty() && !validDrawerLabels(count).contains(drawer)) {
      throw new IllegalArgumentException("Drawer " + drawer + " is not valid for storage " + storageId
          + "; use a-z for the first 26 drawers and A-Z for the next 26");
    }
  }

  private static Set<String> validDrawerLabels(int count) {
    Set<String> labels = new LinkedHashSet<>();
    int limit = Math.min(count, MAX_DRAWERS);
    for (int i = 0; i < limit; i++) {
      labels.add(drawerLabel(i));
    }
    return labels;
  }

  private static Map<String, String> toStorageRow(String id, PartStorageRequest request,
                                                  int defaultOrder) {
    int order = request == null || request.order() == null ? defaultOrder : request.order();
    int drawers = request == null || request.drawersCount() == null ? 0 : request.drawersCount();
    if (drawers < 0) {
      throw new IllegalArgumentException("Drawers count must not be negative");
    }
    if (drawers > MAX_DRAWERS) {
      throw new IllegalArgumentException("Drawers count must not exceed " + MAX_DRAWERS);
    }
    Map<String, String> row = new LinkedHashMap<>();
    row.put("Id", id);
    row.put("Model", clean(request == null ? null : request.model()));
    row.put("DrawersCount", String.valueOf(drawers));
    row.put("Location", clean(request == null ? null : request.location()));
    row.put("Description", clean(request == null ? null : request.description()));
    row.put("LabelSize", normalizeLabelSize(request == null ? null : request.labelSize()));
    row.put("Order", String.valueOf(order));
    return row;
  }

  /**
   * Normalizes a label size to the {@code <width>x<height>} format, in millimeters. A blank value
   * keeps the default size.
   */
  private static String normalizeLabelSize(String value) {
    String size = clean(value).toLowerCase(Locale.ROOT);
    if (size.isEmpty()) {
      return DEFAULT_LABEL_SIZE;
    }
    Matcher matcher = LABEL_SIZE_PATTERN.matcher(size);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Label size must use the <width>x<height> format in millimeters, for example "
              + DEFAULT_LABEL_SIZE);
    }
    double width = Double.parseDouble(matcher.group(1).replace(',', '.'));
    double height = Double.parseDouble(matcher.group(2).replace(',', '.'));
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Label size must be greater than zero");
    }
    return normalizedDimension(matcher.group(1)) + "x" + normalizedDimension(matcher.group(2));
  }

  private static String normalizedDimension(String value) {
    return value.replace(',', '.').replaceAll("\\.0+$", "");
  }

  private static Map<String, Object> storageEntry(Map<String, String> row) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("id", clean(row.get("Id")));
    entry.put("model", clean(row.get("Model")));
    entry.put("drawersCount", toInt(row.get("DrawersCount")));
    entry.put("location", clean(row.get("Location")));
    entry.put("description", clean(row.get("Description")));
    entry.put("labelSize", defaultLabelSize(row.get("LabelSize")));
    entry.put("order", orderOf(row));
    return entry;
  }

  private static String defaultLabelSize(String value) {
    String size = clean(value);
    return size.isEmpty() ? DEFAULT_LABEL_SIZE : size;
  }

  static List<String> drawerLabels(Map<String, String> storage,
                                           List<Map<String, String>> collection) {
    LinkedHashSet<String> labels = new LinkedHashSet<>();
    Integer count = toInt(storage.get("DrawersCount"));
    if (count != null && count > 0) {
      int limit = Math.min(count, MAX_DRAWERS);
      for (int i = 0; i < limit; i++) {
        labels.add(drawerLabel(i));
      }
    } else {
      labels.add("");
    }
    String storageId = clean(storage.get("Id"));
    for (Map<String, String> row : collection) {
      if (clean(row.get("StorageId")).equalsIgnoreCase(storageId)) {
        labels.add(clean(row.get("Drawer")));
      }
    }
    return new ArrayList<>(labels);
  }

  private static String drawerLabel(int index) {
    if (index < 0 || index >= MAX_DRAWERS) {
      throw new IllegalArgumentException("Drawer index out of range: " + index);
    }
    if (index < 26) {
      return String.valueOf((char) ('a' + index));
    }
    return String.valueOf((char) ('A' + index - 26));
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
}
