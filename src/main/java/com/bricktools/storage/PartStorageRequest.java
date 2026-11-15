package com.bricktools.storage;

/**
 * Request used to create or update a physical part storage.
 */
public record PartStorageRequest(String id, String model, Integer drawersCount, String location,
                                 String description, Integer order, String labelSize) {

  /**
   * Creates a request without an explicit label size, so the storage keeps the default one.
   */
  public PartStorageRequest(String id, String model, Integer drawersCount, String location,
                            String description, Integer order) {
    this(id, model, drawersCount, location, description, order, null);
  }
}
