package com.bricktools.web;

import com.bricktools.purchase.PurchaseRequest;
import com.bricktools.purchase.PurchaseService;
import com.bricktools.purchase.SetInventoryRequest;
import com.bricktools.purchase.SetInventoryService;
import com.bricktools.web.dto.PurchaseView;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Set purchases and their stored inventories.
 */
@Tag(name = "Purchases", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class PurchaseController {

  private final PurchaseService purchases;
  private final SetInventoryService setInventories;

  public PurchaseController(PurchaseService purchases, SetInventoryService setInventories) {
    this.purchases = purchases;
    this.setInventories = setInventories;
  }

  /**
   * Get all purchases (owned sets).
   */
  @GetMapping(path = "/purchases", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List Purchases",
      description = "Returns all set purchases recorded in the database."
  )
  public List<PurchaseView> purchases() throws IOException {
    return PurchaseView.from(purchases.list());
  }

  /**
   * Get available options for purchase creation (family members, etc.).
   */
  @GetMapping(path = "/purchases/options", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Purchase Options",
      description = "Returns the dropdown options used when creating a new purchase record."
  )
  public Map<String, List<String>> purchaseOptions() throws IOException {
    return purchases.options();
  }

  /**
   * Create a new purchase record for a set.
   */
  @PostMapping(path = "/purchases", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create Purchase",
      description = "Records the purchase of a Lego set."
  )
  @ApiResponse(responseCode = "201", description = "Purchase created")
  public Map<String, Integer> createPurchase(@RequestBody PurchaseRequest request) throws IOException {
    return Map.of("id", purchases.create(request));
  }

  /**
   * Update an existing purchase record.
   */
  @PutMapping(path = "/purchases/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update Purchase",
      description = "Updates information for an existing purchase record."
  )
  @ApiResponse(responseCode = "204", description = "Purchase updated")
  public void updatePurchase(@Parameter(name = "id", description = "Purchase ID") @PathVariable int id, @RequestBody PurchaseRequest request) throws IOException {
    purchases.update(id, request);
  }

  /**
   * Get the parts of a set with their expected and actual quantities.
   */
  @GetMapping(path = "/sets/{setNumber}/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Set Inventory",
      description = "Returns the parts of a set, joined with their expected quantity from the "
          + "Rebrickable inventory and their stored actual quantity."
  )
  public List<Map<String, Object>> setInventory(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber)
      throws IOException {
    return setInventories.list(setNumber);
  }

  /**
   * Update the actual quantity of one part of a set.
   */
  @PutMapping(path = "/sets/{setNumber}/inventory/{partNumber}/{colorId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update Set Inventory Part",
      description = "Creates or updates the actual quantity of a part in the stored inventory of a set."
  )
  @ApiResponse(responseCode = "204", description = "Actual quantity updated")
  public void updateSetInventoryPart(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber,
      @Parameter(name = "partNumber", description = "Lego part number") @PathVariable String partNumber,
      @Parameter(name = "colorId", description = "Lego color ID") @PathVariable int colorId,
      @RequestBody SetInventoryRequest request) throws IOException {
    setInventories.updateActual(setNumber, partNumber, colorId, request == null ? null : request.actualQut());
  }

  /**
   * Reset the actual quantity of every stored part of a set to 0.
   */
  @PostMapping(path = "/sets/{setNumber}/inventory/clear", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Clear Set Inventory Actual Quantities",
      description = "Sets the actual quantity of every stored part of a set to 0."
  )
  public Map<String, Integer> clearSetInventory(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber)
      throws IOException {
    return Map.of("updated", setInventories.clear(setNumber));
  }

  /**
   * Set the actual quantity of every expected part of a set to its expected quantity.
   */
  @PostMapping(path = "/sets/{setNumber}/inventory/fill", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Fill Set Inventory Actual Quantities",
      description = "Sets the actual quantity of every expected part of a set to its expected quantity."
  )
  public Map<String, Integer> fillSetInventory(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber)
      throws IOException {
    return Map.of("updated", setInventories.fill(setNumber));
  }

  /**
   * Remove the stored inventory rows of a set.
   */
  @DeleteMapping(path = "/sets/{setNumber}/inventory")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Delete Set Inventory",
      description = "Removes every stored actual quantity of a set from data/bt/SetInventory.csv."
  )
  @ApiResponse(responseCode = "204", description = "Set inventory deleted")
  public void deleteSetInventory(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber)
      throws IOException {
    setInventories.delete(setNumber);
  }
}
