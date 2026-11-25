package com.bricktools.storage;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.toInt;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import com.bricktools.part.PartSize;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Loads and caches the part, color, category and size catalogue.
 */
@Service
public class PartCatalogService {

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

  private final Path categoriesFile;
  private final Path rebrickableCategoriesFile;
  private final Path partsFile;
  private final Path colorsFile;
  private final Path partColorsFile;
  private final Path defaultColorsFile;
  private final Path mainPartsFile;
  private Catalog catalog;
  private long catalogStamp;

  public PartCatalogService(BrickToolsProperties props) {
    Path data = props.dataPath();
    this.categoriesFile = data.resolve("bt/part_categories.csv");
    this.rebrickableCategoriesFile = data.resolve("re/part_categories.csv");
    this.partsFile = data.resolve("re/parts.csv");
    this.colorsFile = data.resolve("re/colors.csv");
    this.partColorsFile = data.resolve("bt/part_color_url.csv");
    this.defaultColorsFile = data.resolve("bt/part_default_color.csv");
    this.mainPartsFile = data.resolve("bt/main_parts.csv");
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

  public Catalog catalog() throws IOException {
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

  private static boolean decorated(String number, String name) {
    String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
    String lowerNumber = number == null ? "" : number.toLowerCase(Locale.ROOT);
    return lowerName.contains("print")
        || lowerName.contains("pattern")
        || lowerName.contains("sticker")
        || lowerNumber.matches(".*(pr|pat)\\d+.*");
  }
}
