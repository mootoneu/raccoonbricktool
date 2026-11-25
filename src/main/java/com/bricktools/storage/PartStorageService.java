package com.bricktools.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade over the storage, collection-rule, drawer and catalogue services.
 * New code should depend on the focused services directly.
 */
@Service
public class PartStorageService {

  private final StorageService storages;
  private final CollectionRuleService rules;
  private final DrawerService drawers;
  private final PartCatalogService catalog;

  public PartStorageService(StorageService storages, CollectionRuleService rules,
                            DrawerService drawers, PartCatalogService catalog) {
    this.storages = storages;
    this.rules = rules;
    this.drawers = drawers;
    this.catalog = catalog;
  }

  public List<Map<String, Object>> storages() throws IOException { return storages.storages(); }
  public Map<String, Object> createStorage(PartStorageRequest request) throws IOException { return storages.createStorage(request); }
  public Map<String, Object> updateStorage(String id, PartStorageRequest request) throws IOException { return storages.updateStorage(id, request); }
  public List<Map<String, Object>> collectionRows() throws IOException { return rules.collectionRows(); }
  public Map<String, Object> createCollectionRow(PartCollectionRequest request) throws IOException { return rules.createCollectionRow(request); }
  public Map<String, Object> updateCollectionRow(int index, PartCollectionRequest request) throws IOException { return rules.updateCollectionRow(index, request); }
  public void deleteCollectionRow(int index) throws IOException { rules.deleteCollectionRow(index); }
  public List<Map<String, Object>> drawers(String query) throws IOException { return drawers.drawers(query); }
  public int addParts(String storageId, String drawer, List<String> partNumbers) throws IOException {
    return rules.addParts(storageId, drawer, partNumbers);
  }
  public Integer defaultColorForPart(String partNumber) throws IOException { return catalog.defaultColorForPart(partNumber); }
  public String defaultColorNameForPart(String partNumber) throws IOException { return catalog.defaultColorNameForPart(partNumber); }
  public String partName(String partNumber) throws IOException { return catalog.partName(partNumber); }
}
