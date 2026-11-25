package com.bricktools.brickwatch;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final Object writeLock = new Object();
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

    for (Map<String, String> row : Csv.read(pricesFile)) {
      if (key.equals(row.get("SetNumber"))) {
        return Map.of("setNumber", key, "legoPrice", new BigDecimal(row.get("LegoPrice")), "source", "stored");
      }
    }

    BigDecimal price = scrape(key);
    if (price == null) {
      throw new IllegalStateException("No official price found on BrickWatch for set " + key);
    }

    synchronized (writeLock) {
      List<Map<String, String>> rows = new ArrayList<>(Csv.read(pricesFile));
      Map<String, String> row = new LinkedHashMap<>();
      row.put("SetNumber", key);
      row.put("LegoPrice", price.toPlainString());
      rows.add(row);
      Csv.write(pricesFile, HEADERS, rows);
    }

    return Map.of("setNumber", key, "legoPrice", price, "source", "brickwatch");
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

  static BigDecimal parsePrice(String text) {
    String cleaned = text.replaceAll("[^0-9,.]", "").replace(',', '.');
    if (cleaned.isEmpty()) {
      return null;
    }
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
