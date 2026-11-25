package com.bricktools.rebrickable;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.notification.NotificationService;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RebrickableService {

  private static final Logger log = LoggerFactory.getLogger(RebrickableService.class);
  private static final String LAST_DOWNLOAD = "rebrickable.lastDownload";
  private static final String STALE_NOTIFICATION = "rebrickable-stale";

  private final BrickToolsProperties props;
  private final NotificationService notifications;
  private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  private final Path stateFile;
  private final Path dataDir;
  private final AtomicBoolean updating = new AtomicBoolean();
  private final Object stateLock = new Object();

  public RebrickableService(BrickToolsProperties props, NotificationService notifications) {
    this.props = props;
    this.notifications = notifications;
    this.stateFile = props.dataPath().resolve("bt/state.properties");
    this.dataDir = props.dataPath().resolve("re");
  }

  /** Reads only the small state file, so polling stays responsive while an update is downloading. */
  public Map<String, Object> status() throws IOException {
    LocalDate last = lastDownload();
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("lastDownload", last == null ? null : last.toString());
    status.put("canUpdate", !LocalDate.now().equals(last));
    status.put("stale", isStale(last));
    return status;
  }

  /**
   * Downloads and extracts the archive. Only a short flag guards against concurrent updates: the network and file work
   * is deliberately performed without holding a service-wide lock so that {@link #status()} and
   * {@link #checkStaleness()} are never blocked by a slow download.
   */
  public LocalDate update() throws IOException, InterruptedException {
    if (!updating.compareAndSet(false, true)) {
      throw new IllegalStateException("A Rebrickable update is already running");
    }
    try {
      LocalDate today = LocalDate.now();
      if (today.equals(lastDownload())) {
        throw new IllegalStateException("Rebrickable data has already been downloaded today");
      }
      Path zip = Files.createTempFile("rebrickable", ".zip");
      try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(props.rebrickable().downloadUrl())).build();
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(zip));
        if (response.statusCode() != 200) {
          throw new IOException("Rebrickable download failed with HTTP " + response.statusCode());
        }
        extract(zip);
      } finally {
        Files.deleteIfExists(zip);
      }
      setLastDownload(today);
      checkStaleness();
      return today;
    } finally {
      updating.set(false);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  @Scheduled(cron = "0 0 9 * * *")
  public void checkStaleness() {
    try {
      if (isStale(lastDownload())) {
        notifications.raise(STALE_NOTIFICATION,
            "Rebrickable data is older than a month. Update it from the Administration page.",
            "/admin.html#rebrickable");
      } else {
        notifications.dismiss(STALE_NOTIFICATION);
      }
    } catch (IOException e) {
      log.warn("Unable to check the age of the Rebrickable data", e);
    }
  }

  private static boolean isStale(LocalDate last) {
    return last == null || last.plusMonths(1).isBefore(LocalDate.now());
  }

  /**
   * Extracts into a staging directory next to the data directory first. A truncated or corrupt archive therefore
   * cannot leave a mix of old and new CSV files: the live files are only replaced after extraction completes.
   */
  private void extract(Path zip) throws IOException {
    Path staging = Files.createTempDirectory(dataDir.toAbsolutePath().getParent(), "rebrickable-");
    try {
      try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
          if (entry.isDirectory()) {
            continue;
          }
          String name = Path.of(entry.getName()).getFileName().toString();
          if (name.endsWith(".csv")) {
            Files.copy(in, staging.resolve(name));
          }
        }
      }
      Files.createDirectories(dataDir);
      try (Stream<Path> files = Files.list(staging)) {
        for (Path file : files.toList()) {
          moveAtomically(file, dataDir.resolve(file.getFileName()));
        }
      }
    } finally {
      deleteRecursively(staging);
    }
  }

  private static void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private LocalDate lastDownload() throws IOException {
    synchronized (stateLock) {
      String value = loadState().getProperty(LAST_DOWNLOAD);
      return value == null ? null : LocalDate.parse(value);
    }
  }

  private void setLastDownload(LocalDate date) throws IOException {
    synchronized (stateLock) {
      Properties state = loadState();
      state.setProperty(LAST_DOWNLOAD, date.toString());
      Files.createDirectories(stateFile.toAbsolutePath().getParent());
      try (Writer writer = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
        state.store(writer, "Brick Tools state");
      }
    }
  }

  private Properties loadState() throws IOException {
    Properties state = new Properties();
    if (Files.exists(stateFile)) {
      try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
        state.load(reader);
      }
    }
    return state;
  }
}
