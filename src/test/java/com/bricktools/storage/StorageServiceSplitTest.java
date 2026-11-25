package com.bricktools.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards against the storage god-service returning. */
class StorageServiceSplitTest {

  private static final Path SOURCE = Path.of("src", "main", "java", "com", "bricktools", "storage");

  @Test
  void storageResponsibilitiesLiveInFocusedServices() throws IOException {
    for (String name : new String[]{"StorageService", "CollectionRuleService", "DrawerService", "PartCatalogService"}) {
      assertThat(lineCount(name + ".java"))
          .as("lines in %s", name)
          .isLessThan(400);
    }
    // Compatibility facade only delegates; it must stay tiny.
    assertThat(lineCount("PartStorageService.java")).isLessThan(100);
  }

  private static int lineCount(String file) throws IOException {
    try (var lines = Files.lines(SOURCE.resolve(file))) {
      return (int) lines.count();
    }
  }
}
