package com.bricktools.brickwatch;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.CsvTable;
import com.bricktools.csv.FileLocks;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Service
public class BrickWatchService {

  private static final String USER_AGENT = "Mozilla/5.0 (compatible; BrickTools/0.1)";
  private static final String TABLE_MARKER = "Information sur le prix";
  private static final String PRICE_LABEL = "Lego prix conseill";
  private static final int MAX_ATTEMPTS = 3;
  private static final List<String> HEADERS = List.of("SetNumber", "LegoPrice");

  private final BrickToolsProperties props;
  private final Path pricesFile;
  private final Object throttleLock = new Object();
  private long nextRequestAt;

  public BrickWatchService(BrickToolsProperties props) {
    this.props = props;
    this.pricesFile = props.dataPath().resolve("bw/setprices.csv");
  }

  public Map<String, Object> price(String setNumber) throws IOException, InterruptedException {
    String key = setNumber == null ? "" : setNumber.trim();
    if (!key.matches("[A-Za-z0-9-]+")) {
      throw new IllegalArgumentException("Invalid set number: " + setNumber);
    }

    Optional<Map<String, String>> cached = CsvTable.load(pricesFile, HEADERS)
        .find(row -> key.equals(row.get("SetNumber")));
    if (cached.isPresent()) {
      return stored(key, cached.get());
    }

    BigDecimal price = scrape(key);
    if (price == null) {
      throw new IllegalStateException("No official price found on BrickWatch for set " + key);
    }

    return FileLocks.call(pricesFile, () -> {
      CsvTable table = CsvTable.load(pricesFile, HEADERS);
      Optional<Map<String, String>> stored = table.find(row -> key.equals(row.get("SetNumber")));
      if (stored.isPresent()) {
        return stored(key, stored.get());
      }
      Map<String, String> row = new LinkedHashMap<>();
      row.put("SetNumber", key);
      row.put("LegoPrice", price.toPlainString());
      table.add(row);
      table.save();
      return Map.of("setNumber", key, "legoPrice", price, "source", "brickwatch");
    });
  }

  private static Map<String, Object> stored(String key, Map<String, String> row) {
    return Map.of("setNumber", key, "legoPrice", new BigDecimal(row.get("LegoPrice")), "source", "stored");
  }

  private BigDecimal scrape(String setNumber) throws IOException, InterruptedException {
    throttle();
    Document doc = fetch(props.brickwatch().baseUrl() + setNumber);
    if (doc.getElementsContainingOwnText(TABLE_MARKER).isEmpty()) {
      return null;
    }
    for (Element row : doc.select("tr")) {
      Elements cells = row.select("td, th");
      if (cells.size() > 1 && cells.first().text().trim().startsWith(PRICE_LABEL)) {
        return parsePrice(cells.last().text());
      }
    }
    return null;
  }

  /**
   * BrickWatch answers the first visit with a JavaScript verification page that sets session cookies and then
   * redirects to the real page. Jsoup does not run JavaScript, so the cookies are kept and the request is repeated.
   */
  private Document fetch(String url) throws IOException {
    Map<String, String> cookies = new HashMap<>();
    Document doc = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Connection.Response response = Jsoup.connect(url)
          .userAgent(USER_AGENT)
          .timeout(15_000)
          .cookies(cookies)
          .execute();
      cookies.putAll(response.cookies());
      doc = response.parse();
      if (!doc.getElementsContainingOwnText(TABLE_MARKER).isEmpty()) {
        return doc;
      }
    }
    return doc;
  }

  /** Reserves a start time for the next request, then sleeps outside the lock. */
  private void throttle() throws InterruptedException {
    long slot = reserveRequestSlot();
    long wait = slot - System.currentTimeMillis();
    if (wait > 0) {
      Thread.sleep(wait);
    }
  }

  private long reserveRequestSlot() {
    synchronized (throttleLock) {
      long now = System.currentTimeMillis();
      long slot = Math.max(now, nextRequestAt);
      nextRequestAt = slot + props.brickwatch().minDelayMs();
      return slot;
    }
  }

  /**
   * Parses a price written with either French ({@code 1.299,99}) or English ({@code 1,299.99}) separators. When both
   * separators occur, the rightmost one is the decimal separator and the other one is a thousands separator.
   */
  static BigDecimal parsePrice(String text) {
    if (text == null) {
      return null;
    }
    String cleaned = text.replaceAll("[^0-9,.]", "");
    if (cleaned.isEmpty()) {
      return null;
    }
    int dot = cleaned.lastIndexOf('.');
    int comma = cleaned.lastIndexOf(',');
    if (dot >= 0 && comma >= 0) {
      cleaned = comma > dot
          ? cleaned.replace(".", "").replace(',', '.')
          : cleaned.replace(",", "");
    } else if (comma >= 0) {
      cleaned = cleaned.replace(',', '.');
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
