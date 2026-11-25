package com.bricktools.web;

import com.bricktools.storage.AddToContainerRequest;
import com.bricktools.storage.ContainerService;
import com.bricktools.storage.PartCollectionRequest;
import com.bricktools.storage.PartStorageRequest;
import com.bricktools.storage.PartStorageService;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Physical storages, collection rules, drawers and container additions.
 */
@Tag(name = "Part storage", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class PartStorageController {

  private final PartStorageService partStorages;
  private final ContainerService containers;

  public PartStorageController(PartStorageService partStorages, ContainerService containers) {
    this.partStorages = partStorages;
    this.containers = containers;
  }

  // ==========================================================================
  // Part storage and collection
  // ==========================================================================

  @GetMapping(path = "/part-storage", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List part storages",
      description = "Returns every physical part storage stored in data/bt/part_storage.csv, "
          + "sorted by Order and then Id."
  )
  public List<Map<String, Object>> partStorages() throws IOException {
    return partStorages.storages();
  }

  @PostMapping(path = "/part-storage", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create part storage",
      description = "Adds a physical part storage (furniture, box, cabinet, drawers, etc.)."
  )
  public Map<String, Object> createPartStorage(@RequestBody PartStorageRequest request)
      throws IOException {
    return partStorages.createStorage(request);
  }

  @PutMapping(path = "/part-storage/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update part storage",
      description = "Updates the order, model, drawer count, location, description, and label size of a part storage."
  )
  public Map<String, Object> updatePartStorage(@PathVariable String id,
                                               @RequestBody PartStorageRequest request)
      throws IOException {
    return partStorages.updateStorage(id, request);
  }

  @GetMapping(path = "/part-collection", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List part collection rows",
      description = "Returns every rule of data/bt/part_collection.csv with its file index."
  )
  public List<Map<String, Object>> partCollection() throws IOException {
    return partStorages.collectionRows();
  }

  @PostMapping(path = "/part-collection", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create part collection row",
      description = "Declares that a set of parts and colors is stored in a storage drawer."
  )
  public Map<String, Object> createPartCollectionRow(@RequestBody PartCollectionRequest request)
      throws IOException {
    return partStorages.createCollectionRow(request);
  }

  @PutMapping(path = "/part-collection/{index}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update part collection row",
      description = "Replaces the part collection rule at the given file index."
  )
  public Map<String, Object> updatePartCollectionRow(@PathVariable int index,
                                                     @RequestBody PartCollectionRequest request)
      throws IOException {
    return partStorages.updateCollectionRow(index, request);
  }

  @DeleteMapping(path = "/part-collection/{index}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete part collection row",
      description = "Removes the part collection rule at the given file index."
  )
  public void deletePartCollectionRow(@PathVariable int index) throws IOException {
    partStorages.deleteCollectionRow(index);
  }

  @GetMapping(path = "/part-collection/drawers", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List storage drawers",
      description = "Returns one card per storage drawer with its resolved contents. When a query "
          + "is given, only drawers containing a matching part are returned."
  )
  public List<Map<String, Object>> partCollectionDrawers(
      @Parameter(name = "query", description = "Part number, name, color, category, or tag")
      @RequestParam(required = false) String query)
      throws IOException {
    return partStorages.drawers(query);
  }

  @PostMapping(path = "/part-collection/add", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Add parts to a container",
      description = "Adds parts to a part storage, a storage drawer, a PaB purchase, or a BrickOwl order."
  )
  public Map<String, Object> addPartsToContainer(@RequestBody AddToContainerRequest request)
      throws IOException {
    return containers.addParts(request);
  }
}
