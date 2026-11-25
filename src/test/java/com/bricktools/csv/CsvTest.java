package com.bricktools.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTest {

  @TempDir
  Path dir;

  @Test
  void roundTripsValuesContainingSeparatorsAndQuotes() throws IOException {
    Path file = dir.resolve("nested/data.csv");
    Map<String, String> row = Map.of("A", "hello, world", "B", "say \"hi\"");

    Csv.write(file, List.of("A", "B"), List.of(row));

    assertThat(Csv.read(file)).containsExactly(row);
  }

  @Test
  void missingFileReadsAsEmptyList() throws IOException {
    assertThat(Csv.read(dir.resolve("absent.csv"))).isEmpty();
  }

  @Test
  void missingValuesAreWrittenAsEmptyStrings() throws IOException {
    Path file = dir.resolve("partial.csv");

    Csv.write(file, List.of("A", "B"), List.of(Map.of("A", "1")));

    assertThat(Csv.read(file)).containsExactly(Map.of("A", "1", "B", ""));
  }

  @Test
  void headerIsWrittenEvenWithoutRows() throws IOException {
    Path file = dir.resolve("empty.csv");

    Csv.write(file, List.of("Id", "Name"), List.of());

    assertThat(Files.readString(file).trim()).isEqualTo("Id,Name");
    assertThat(Csv.read(file)).isEmpty();
  }

  @Test
  void concurrentWritesLeaveNoTemporaryFilesAndKeepCompleteContent() throws Exception {
    Path file = dir.resolve("shared.csv");
    ExecutorService executor = Executors.newFixedThreadPool(4);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        int worker = i;
        futures.add(executor.submit(() -> {
          start.await();
          List<Map<String, String>> rows = new ArrayList<>();
          for (int j = 0; j < 100; j++) {
            rows.add(Map.of("Id", worker + "-" + j));
          }
          Csv.write(file, List.of("Id"), rows);
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(20, TimeUnit.SECONDS);
      }

      assertThat(Csv.read(file)).hasSize(100);
      try (Stream<Path> paths = Files.list(dir)) {
        assertThat(paths).noneMatch(path -> path.toString().endsWith(".tmp"));
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
