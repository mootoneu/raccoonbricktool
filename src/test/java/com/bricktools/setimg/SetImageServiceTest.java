package com.bricktools.setimg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bricktools.config.BrickToolsProperties;
import com.bricktools.support.TestHttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetImageServiceTest {

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

  @TempDir
  Path dir;

  private TestHttpServer server;
  private final AtomicInteger jpegRequests = new AtomicInteger();

  @BeforeEach
  void setUp() throws IOException {
    server = new TestHttpServer();
    server.handle("/sets/75192-1.jpg", exchange -> {
      jpegRequests.incrementAndGet();
      send(exchange, 200, JPEG);
    });
    server.handle("/sets/31387-1.png", exchange -> send(exchange, 200, PNG));
    server.handle("/sets/missing-1.jpg", exchange -> send(exchange, 404, new byte[0]));

    Files.createDirectories(dir.resolve("re"));
    Files.writeString(dir.resolve("re/sets.csv"), String.join("\n",
        "set_num,name,year,theme_id,num_parts,img_url",
        "75192-1,Millennium Falcon,2017,171,7541," + server.baseUrl() + "/sets/75192-1.jpg",
        "31387-1,Pirate Ship,2026,1,1074," + server.baseUrl() + "/sets/31387-1.png",
        "99999-1,No picture,2020,1,10,",
        "missing-1,Broken,2020,1,10," + server.baseUrl() + "/sets/missing-1.jpg") + "\n");
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  private SetImageService service() {
    return new SetImageService(new BrickToolsProperties(
        dir.toString(),
        new BrickToolsProperties.Rebrickable(""),
        new BrickToolsProperties.BrickWatch("", 0),
        new BrickToolsProperties.BrickOwl("", "", "")));
  }

  private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
    exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  @Test
  void downloadsImageOnceUnderTheSetNumber() throws Exception {
    SetImageService service = service();

    Optional<Path> first = service.ensure("75192");
    Optional<Path> second = service.ensure("75192");

    assertThat(first).contains(dir.resolve("bt/setsimg/75192.jpg"));
    assertThat(second).isEqualTo(first);
    assertThat(Files.readAllBytes(dir.resolve("bt/setsimg/75192.jpg"))).isEqualTo(JPEG);
    assertThat(jpegRequests.get()).isEqualTo(1);
  }

  @Test
  void keepsTheExtensionOfTheSourceImage() throws Exception {
    assertThat(service().ensure("31387")).contains(dir.resolve("bt/setsimg/31387.png"));
    assertThat(service().find("31387")).contains(dir.resolve("bt/setsimg/31387.png"));
  }

  @Test
  void returnsEmptyWhenSetHasNoImageUrlOrIsUnknown() throws Exception {
    assertThat(service().ensure("99999")).isEmpty();
    assertThat(service().ensure("00000")).isEmpty();
    assertThat(Files.exists(dir.resolve("bt/setsimg/99999.jpg"))).isFalse();
  }

  @Test
  void failedDownloadStoresNothingAndCanBeRetried() throws Exception {
    assertThatThrownBy(() -> service().ensure("missing"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("404");
    assertThat(service().find("missing")).isEmpty();
    assertThat(Files.exists(dir.resolve("bt/setsimg/missing.part"))).isFalse();
  }

  @Test
  void findReturnsStoredImageAndIgnoresPartialDownloads() throws Exception {
    Files.createDirectories(dir.resolve("bt/setsimg"));
    Files.write(dir.resolve("bt/setsimg/12345.part"), JPEG);

    assertThat(service().find("12345")).isEmpty();
    assertThat(service().available()).isEmpty();

    service().ensure("75192");
    assertThat(service().available()).containsExactly("75192");
  }

  @Test
  void findIsNotBlockedWhileAnotherSetDownloads() throws Exception {
    CountDownLatch downloadStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    server.handle("/sets/60000-1.jpg", exchange -> {
      downloadStarted.countDown();
      try {
        release.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      send(exchange, 200, JPEG);
    });
    Files.writeString(dir.resolve("re/sets.csv"),
        "\n60000-1,Slow,2020,1,10," + server.baseUrl() + "/sets/60000-1.jpg\n",
        StandardOpenOption.APPEND);
    Files.createDirectories(dir.resolve("bt/setsimg"));
    Path stored = dir.resolve("bt/setsimg/77777.jpg");
    Files.write(stored, JPEG);

    SetImageService service = service();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> download = executor.submit(() -> {
        try {
          service.ensure("60000");
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      });
      assertThat(downloadStarted.await(5, TimeUnit.SECONDS)).isTrue();

      long start = System.nanoTime();
      Optional<Path> found = service.find("77777");
      long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      assertThat(elapsedMs).isLessThan(500);
      assertThat(found).contains(stored);
      release.countDown();
      download.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void contentTypeFollowsExtension() {
    SetImageService service = service();

    assertThat(service.contentType(dir.resolve("a.jpg"))).isEqualTo("image/jpeg");
    assertThat(service.contentType(dir.resolve("a.png"))).isEqualTo("image/png");
    assertThat(service.contentType(dir.resolve("a.bin"))).isEqualTo("application/octet-stream");
  }

  @Test
  void rejectsUnsafeSetNumbers() throws Exception {
    assertThat(service().find("../75192")).isEmpty();
    assertThatThrownBy(() -> service().ensure("../75192"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
