package com.bricktools.part;

import static com.bricktools.util.Values.clean;

import com.bricktools.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the size of a LEGO part from its name and category. A size is written as
 * {@code <width>x<length>x<height>}. When the name only gives two dimensions, the height defaults to
 * {@code 1/3} for plates and tiles and to {@code 1} for the other categories.
 */
public final class PartSize {

  private static final String DIMENSION = "(?:\\d+\\s+\\d+/\\d+|\\d+/\\d+|\\d+(?:\\.\\d+)?)";
  private static final Pattern SIZE = Pattern.compile(
      "(" + DIMENSION + ")\\s*[x×]\\s*(" + DIMENSION + ")"
          + "(?:\\s*[x×]\\s*(" + DIMENSION + "))?",
      Pattern.CASE_INSENSITIVE);

  private PartSize() {}

  /**
   * Returns the size found in the part name, or an empty string when the name contains none.
   *
   * @param name part name, for example {@code Brick 2 x 4}
   * @param categoryGroup part category group, for example {@code Brick}, {@code Plate} or {@code Tile}
   */
  public static String parse(String name, String categoryGroup) {
    if (name == null || name.isBlank()) {
      return "";
    }
    Matcher matcher = SIZE.matcher(name);
    if (!matcher.find()) {
      return "";
    }
    String width = dimension(matcher.group(1));
    String length = dimension(matcher.group(2));
    String height = matcher.group(3) == null ? defaultHeight(categoryGroup) : dimension(matcher.group(3));
    return width + "x" + length + "x" + height;
  }

  /**
   * Returns the default height of a part category: {@code 1/3} for plates and tiles, {@code 1} for
   * every other category.
   */
  public static String defaultHeight(String categoryGroup) {
    return isFlat(categoryGroup) ? "1/3" : "1";
  }

  /**
   * Returns the stored sizes of the main parts, keyed by part number. Parts without a size are
   * omitted.
   */
  public static Map<String, String> index(Path mainPartsFile) throws IOException {
    Map<String, String> sizes = new HashMap<>();
    for (Map<String, String> row : Csv.read(mainPartsFile)) {
      String partNumber = clean(row.get("PartNumber"));
      String size = clean(row.get("Size"));
      if (!partNumber.isEmpty() && !size.isEmpty()) {
        sizes.put(partNumber, size);
      }
    }
    return sizes;
  }

  private static boolean isFlat(String categoryGroup) {
    String group = clean(categoryGroup).toLowerCase(Locale.ROOT);
    if (group.endsWith("s")) {
      group = group.substring(0, group.length() - 1);
    }
    return group.equals("plate") || group.equals("tile");
  }

  private static String dimension(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }

}
