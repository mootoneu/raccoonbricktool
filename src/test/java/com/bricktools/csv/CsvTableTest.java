package com.bricktools.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTableTest {

  @TempDir
  Path dir;

  private static final List<String> HEADERS = List.of("SetNumber", "LegoPrice");

  @Test
  void findsAddsRemovesAndSavesRows() throws IOException {
    Path file = dir.resolve("bw/setprices.csv");
    Csv.write(file, HEADERS, List.of(Map.of("SetNumber", "1", "LegoPrice", "10")));

    CsvTable table = CsvTable.load(file, HEADERS);
    assertThat(table.find(row -> "1".equals(row.get("SetNumber")))).isPresent();
    assertThat(table.find(row -> "2".equals(row.get("SetNumber")))).isEmpty();

    table.removeIf(row -> "1".equals(row.get("SetNumber")));
    table.add(Map.of("SetNumber", "2", "LegoPrice", "20"));
    table.save();

    assertThat(Csv.read(file)).containsExactly(Map.of("SetNumber", "2", "LegoPrice", "20"));
  }

  @Test
  void loadsMissingFileAsAnEmptyTable() throws IOException {
    CsvTable table = CsvTable.load(dir.resolve("absent.csv"), HEADERS);

    assertThat(table.rows()).isEmpty();
    assertThat(table.find(row -> true)).isEmpty();
  }
}
