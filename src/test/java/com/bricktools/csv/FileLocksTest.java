package com.bricktools.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLocksTest {

  @TempDir
  Path dir;

  @Test
  void concurrentReadModifyWriteKeepsAllRows() throws Exception {
    Path file = dir.resolve("bw/setprices.csv");
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        int worker = i;
        futures.add(executor.submit(() -> {
          start.await();
          for (int j = 0; j < 50; j++) {
            String id = worker + "-" + j;
            FileLocks.call(file, () -> {
              List<Map<String, String>> rows = new ArrayList<>(Csv.read(file));
              rows.add(Map.of("Id", id));
              Csv.write(file, List.of("Id"), rows);
              return null;
            });
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }

      assertThat(Csv.read(file)).hasSize(100);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void locksArePerFileSoUnrelatedWritesDoNotBlockEachOther() throws Exception {
    Path locked = dir.resolve("locked.csv");
    Path other = dir.resolve("other.csv");
    ReentrantLock lock = FileLocks.forFile(locked);
    lock.lock();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> write = executor.submit(() -> {
        Csv.write(other, List.of("Id"), List.of(Map.of("Id", "1")));
        return null;
      });

      write.get(5, TimeUnit.SECONDS);
      assertThat(Csv.read(other)).hasSize(1);
    } finally {
      lock.unlock();
      executor.shutdownNow();
    }
  }
}
