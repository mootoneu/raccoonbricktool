package com.bricktools.storage;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.isNumeric;
import static com.bricktools.util.Values.split;
import static com.bricktools.util.Values.toInt;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import com.bricktools.part.PartSize;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
 * Stores the physical part storages and the part collection rules, and expands the rules into
 * drawer contents. Data lives in data/bt/part_storage.csv and data/bt/part_collection.csv.
 */
@Service
public class PartStorageService {

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
  /** One ordering for storages everywhere (table, drawer selector and label export). */
  private static final Comparator<Map<String, String>> STORAGE_ORDER =
      Comparator.comparingInt((Map<String, String> row) -> orderOf(row))
          .thenComparing(row -> clean(row.get("Id")), String.CASE_INSENSITIVE_ORDER);

  private final Path storageFile;
  private final Path collectionFile;
  private final Path categoriesFile;
  private final Path rebrickableCategoriesFile;
  private final Path partsFile;
  private final Path colorsFile;
  private final Path partColorsFile;
  private final Path defaultColorsFile;
  private final Path mainPartsFile;

  private Catalog catalog;
  private long catalogStamp = Long.MIN_VALUE;

  public PartStorageService(BrickToolsProperties props) {
    Path data = props.dataPath();
    this.storageFile = data.resolve("bt/part_storage.csv");
    this.collectionFile = data.resolve("bt/part_collection.csv");
    this.categoriesFile = data.resolve("bt/part_categories.csv");
    this.rebrickableCategoriesFile = data.resolve("re/part_categories.csv");
    this.partsFile = data.resolve("re/parts.csv");
    this.colorsFile = data.resolve("re/colors.csv");
    this.partColorsFile = data.resolve("bt/part_color_url.csv");
    this.defaultColorsFile = data.resolve("bt/part_default_color.csv");
    this.mainPartsFile = data.resolve("bt/main_parts.csv");
  }

  // ============================================================================
  // Storages
  // ============================================================================

  public synchronized List<Map<String, Object>> storages() throws IOException {
    List<Map<String, String>> rows = loadStorages();
    rows.sort(STORAGE_ORDER);
    List<Map<String, String>> collection = loadCollection();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, String> row : rows) {
      Map<String, Object> entry = storageEntry(row);
      long rowCount = collection.stream()
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
  // Drawer expansion and search
  // ============================================================================

  /**
   * Returns one card per drawer of every storage. When a query is given, only the drawers that
   * contain at least one matching part are returned and their matching parts are resolved.
   */
  public synchronized List<Map<String, Object>> drawers(String query) throws IOException {
    Catalog data = catalog();
    String cleaned = clean(query);
    List<Map<String, String>> storages = loadStorages();
    List<Map<String, String>> collection = loadCollection();
    MatchContext context = cleaned.isEmpty() ? null : match(data, cleaned, collection);
    Map<String, List<Integer>> rowsByDrawer = indexRowsByDrawer(collection);
    storages.sort(STORAGE_ORDER);

    List<Map<String, Object>> result = new ArrayList<>();
    for (Map<String, String> storage : storages) {
      String storageId = clean(storage.get("Id"));
      for (String drawer : drawerLabels(storage, collection)) {
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

  // ============================================================================
  // Add parts to a storage / drawer
  // ============================================================================

  public synchronized int addParts(String storageId, String drawer, List<String> partNumbers)
      throws IOException {
    Catalog data = catalog();
    Map<String, String> storage = requireStorage(storageId);
    String drawerValue = clean(drawer);
    validateDrawer(storage, drawerValue);

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

  /**
   * Returns the default color of a part, or the first color it is available in.
   */
  public Integer defaultColorForPart(String partNumber) throws IOException {
    Catalog data = catalog();
    Catalog.PartInfo part = data.parts.get(clean(partNumber));
    if (part == null) {
      return null;
    }
    Catalog.ColorInfo color = Catalog.colorFor(data, part, null);
    return color == null ? null : toInt(color.id());
  }

  public String defaultColorNameForPart(String partNumber) throws IOException {
    Catalog data = catalog();
    Catalog.PartInfo part = data.parts.get(clean(partNumber));
    if (part == null) {
      return "";
    }
    Catalog.ColorInfo color = Catalog.colorFor(data, part, null);
    return color == null ? "" : color.name();
  }

  public String partName(String partNumber) throws IOException {
    Catalog.PartInfo part = catalog().parts.get(clean(partNumber));
    return part == null ? "" : part.name();
  }

  // ============================================================================
  // Catalog
  // ============================================================================

  private Catalog catalog() throws IOException {
    long stamp = catalogStamp();
    if (catalog != null && stamp == catalogStamp) {
      return catalog;
    }
    Catalog data = new Catalog();
    for (Map<String, String> row : Csv.read(rebrickableCategoriesFile)) {
      String id = clean(row.get("id"));
      if (!id.isEmpty()) {
        data.categoryNames.put(id, clean(row.get("name")));
      }
    }
    for (Map<String, String> row : Csv.read(categoriesFile)) {
      String id = clean(row.get("Id"));
      if (!id.isEmpty()) {
        data.categoryNames.put(id, clean(row.get("Name")));
      }
    }
    for (Map<String, String> row : Csv.read(colorsFile)) {
      String id = clean(row.get("id"));
      if (!id.isEmpty()) {
        data.colors.put(id, new Catalog.ColorInfo(id, clean(row.get("name")), clean(row.get("rgb"))));
      }
    }
    for (Map<String, String> row : Csv.read(defaultColorsFile)) {
      String partNumber = clean(row.get("PartNumber"));
      String colorId = clean(row.get("ColorId"));
      if (!partNumber.isEmpty() && !colorId.isEmpty()) {
        data.defaultColors.put(partNumber, colorId);
      }
    }
    for (Map<String, String> row : Csv.read(partsFile)) {
      String number = clean(row.get("part_num"));
      if (number.isEmpty()) {
        continue;
      }
      String categoryId = clean(row.get("part_cat_id"));
      String name = clean(row.get("name"));
      String categoryName = data.categoryNames.getOrDefault(categoryId, "");
      data.parts.put(number, new Catalog.PartInfo(number, name, categoryId, categoryName, decorated(number, name)));
    }
    for (Map<String, String> row : Csv.read(partColorsFile)) {
      String partNumber = clean(row.get("PartNumber"));
      String colorId = clean(row.get("ColorId"));
      if (partNumber.isEmpty() || colorId.isEmpty()) {
        continue;
      }
      data.partColorIds.computeIfAbsent(partNumber, key -> new ArrayList<>()).add(colorId);
    }
    data.sizes.putAll(PartSize.index(mainPartsFile));
    catalog = data;
    catalogStamp = stamp;
    return data;
  }

  private long catalogStamp() throws IOException {
    long stamp = 17;
    for (Path file : List.of(categoriesFile, rebrickableCategoriesFile, partsFile, colorsFile,
        partColorsFile, defaultColorsFile, mainPartsFile)) {
      if (Files.isRegularFile(file)) {
        stamp = stamp * 31 + Files.getLastModifiedTime(file).toMillis();
        stamp = stamp * 31 + Files.size(file);
      }
    }
    return stamp;
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
      Map<String, Object> entry = collectionEntry(indexes.get(i), row);
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

  // ============================================================================
  // Loading and validation
  // ============================================================================

  private List<Map<String, String>> loadStorages() throws IOException {
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

  private List<Map<String, String>> loadCollection() throws IOException {
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

  private Map<String, String> requireStorage(String storageId) throws IOException {
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
  private static void validateDrawer(Map<String, String> storage, String drawer) {
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
    Map<String, String> storage = requireStorage(storageId);
    validateDrawer(storage, drawer);
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

  private static Map<String, Object> collectionEntry(int index, Map<String, String> row) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("index", index);
    entry.put("parts", clean(row.get("Parts")));
    entry.put("colors", clean(row.get("Colors")));
    entry.put("storageId", clean(row.get("StorageId")));
    entry.put("drawer", clean(row.get("Drawer")));
    entry.put("amount", clean(row.get("Amount")));
    return entry;
  }

  private static List<String> drawerLabels(Map<String, String> storage,
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

  private static boolean decorated(String number, String name) {
    String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
    String lowerNumber = number == null ? "" : number.toLowerCase(Locale.ROOT);
    return lowerName.contains("print")
        || lowerName.contains("pattern")
        || lowerName.contains("sticker")
        || lowerNumber.matches(".*(pr|pat)\\d+.*");
  }



  private record MatchContext(Set<String> numbers, Map<String, String> colorByPart,
                              Map<String, Set<Map<String, String>>> explicitRows) {
  }
}
