package com.bricktools.brickonomics;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.csv.Csv;
import com.bricktools.csv.FileLocks;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BrickonomicsService {

  static final String DEFAULT_BASE_URL = "https://brickonomics.net/set/";
  private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
  private static final String PRICE_LABEL = "Retail price:";
  private static final long MIN_DELAY_MS = 1000;
  private static final List<String> HEADERS = List.of("SetNumber", "LegoPrice");

  private final String baseUrl;
  private final Path pricesFile;
  private final Object throttleLock = new Object();
  private long nextRequestAt;

  @Autowired
  public BrickonomicsService(BrickToolsProperties props) {
    this(props, DEFAULT_BASE_URL);
  }

  BrickonomicsService(BrickToolsProperties props, String baseUrl) {
    this.baseUrl = baseUrl;
    this.pricesFile = props.dataPath().resolve("bw/setprices.csv");
  }

  public Map<String, Object> fetchPrice(String setNumber) throws IOException, InterruptedException {
    String key = setNumber == null ? "" : setNumber.trim();
    if (!key.matches("[A-Za-z0-9-]+")) {
      throw new IllegalArgumentException("Invalid set number: " + setNumber);
    }
    String number = key.replaceFirst("-\\d+$", "");

    BigDecimal price = scrape(number);
    if (price == null) {
      throw new IllegalStateException("No retail price found on Brickonomics for set " + number);
    }

    return FileLocks.call(pricesFile, () -> {
      List<Map<String, String>> rows = new ArrayList<>();
      for (Map<String, String> row : Csv.read(pricesFile)) {
        if (!number.equals(row.get("SetNumber"))) {
          rows.add(row);
        }
      }
      Map<String, String> row = new LinkedHashMap<>();
      row.put("SetNumber", number);
      row.put("LegoPrice", price.toPlainString());
      rows.add(row);
      Csv.write(pricesFile, HEADERS, rows);
      return Map.of("setNumber", number, "legoPrice", price, "source", "brickonomics");
    });
  }

  private BigDecimal scrape(String setNumber) throws IOException, InterruptedException {
    throttle();
    Document doc = Jsoup.connect(baseUrl + setNumber + "-1")
        .userAgent(USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", "fr-FR,fr;q=0.9")
        .timeout(15_000)
        .cookie("country", "fr")
        .cookie("currency", "EUR")
        .get();
    for (Element label : doc.select("b")) {
      Node next = label.nextSibling();
      if (PRICE_LABEL.equals(label.text().trim()) && next instanceof TextNode text) {
        return parsePrice(text.text());
      }
    }
    return null;
  }

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
      nextRequestAt = slot + MIN_DELAY_MS;
      return slot;
    }
  }

  static BigDecimal parsePrice(String text) {
    String cleaned = text.replaceAll("[^0-9.]", "");
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
