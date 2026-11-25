package com.bricktools.csv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A small read-modify-write helper for a single CSV table. Callers that update rows should hold the file's
 * {@link FileLocks} lock around the whole sequence so the read, the change and {@link #save()} are atomic with respect
 * to other writers of the same file.
 */
public final class CsvTable {

  private final Path file;
  private final List<String> headers;
  private final List<Map<String, String>> rows;

  private CsvTable(Path file, List<String> headers, List<Map<String, String>> rows) {
    this.file = file;
    this.headers = headers;
    this.rows = rows;
  }

  public static CsvTable load(Path file, List<String> headers) throws IOException {
    return new CsvTable(file, headers, Csv.read(file));
  }

  public List<Map<String, String>> rows() {
    return rows;
  }

  public Optional<Map<String, String>> find(Predicate<Map<String, String>> predicate) {
    return rows.stream().filter(predicate).findFirst();
  }

  public void add(Map<String, String> row) {
    rows.add(row);
  }

  public void removeIf(Predicate<Map<String, String>> predicate) {
    rows.removeIf(predicate);
  }

  public void save() throws IOException {
    Csv.write(file, headers, rows);
  }
}
