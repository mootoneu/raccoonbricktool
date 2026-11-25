package com.bricktools.web;

import com.bricktools.le.PabPartPriceService;
import com.bricktools.pab.PabPartRequest;
import com.bricktools.pab.PabPurchaseRequest;
import com.bricktools.pab.PabService;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Pick a Brick purchases, parts and part prices.
 */
@Tag(name = "Pick a Brick", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class PabController {

  private final PabService pab;
  private final PabPartPriceService pabPartPrices;

  public PabController(PabService pab, PabPartPriceService pabPartPrices) {
    this.pab = pab;
    this.pabPartPrices = pabPartPrices;
  }

  @GetMapping(path = "/pab/purchases", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List PaB purchases",
      description = "Returns the Pick a Brick purchases with the total quantity of their parts."
  )
  public List<Map<String, Object>> pabPurchases() throws IOException {
    return pab.purchases();
  }

  @PostMapping(path = "/pab/purchases", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create PaB purchase",
      description = "Records a Pick a Brick purchase made in a Lego store."
  )
  @ApiResponse(responseCode = "201", description = "PaB purchase created")
  public Map<String, Integer> createPabPurchase(@RequestBody PabPurchaseRequest request) throws IOException {
    return Map.of("id", pab.create(request));
  }

  @PutMapping(path = "/pab/purchases/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update PaB purchase",
      description = "Updates an existing Pick a Brick purchase."
  )
  @ApiResponse(responseCode = "204", description = "PaB purchase updated")
  public void updatePabPurchase(
      @Parameter(name = "id", description = "PaB purchase ID") @PathVariable int id,
      @RequestBody PabPurchaseRequest request) throws IOException {
    pab.update(id, request);
  }

  @GetMapping(path = "/pab/purchases/{purchaseId}/parts", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List PaB parts",
      description = "Returns the parts of a Pick a Brick purchase with their name, category and color."
  )
  public List<Map<String, Object>> pabParts(
      @Parameter(name = "purchaseId", description = "PaB purchase ID") @PathVariable int purchaseId)
      throws IOException {
    return pab.parts(purchaseId);
  }

  @GetMapping(path = "/pab/parts", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List all PaB parts",
      description = "Returns every part of every Pick a Brick purchase, with its name, category and color."
  )
  public List<Map<String, Object>> pabParts() throws IOException {
    return pab.allParts();
  }

  @PostMapping(path = "/pab/purchases/{purchaseId}/parts", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create PaB part",
      description = "Adds a part to an existing Pick a Brick purchase."
  )
  @ApiResponse(responseCode = "201", description = "PaB part created")
  public Map<String, Integer> createPabPart(
      @Parameter(name = "purchaseId", description = "PaB purchase ID") @PathVariable int purchaseId,
      @RequestBody PabPartRequest request) throws IOException {
    return Map.of("id", pab.createPart(purchaseId, request));
  }

  @PutMapping(path = "/pab/parts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update PaB part",
      description = "Updates an existing part of a Pick a Brick purchase."
  )
  @ApiResponse(responseCode = "204", description = "PaB part updated")
  public void updatePabPart(
      @Parameter(name = "id", description = "PaB part ID") @PathVariable int id,
      @RequestBody PabPartRequest request) throws IOException {
    pab.updatePart(id, request);
  }

  @GetMapping(path = "/le/pab-part-prices", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List PaB part prices",
      description = "Returns every Pick a Brick part price stored in data/le/pab_part_prices.csv."
  )
  public List<Map<String, Object>> pabPartPrices() throws IOException {
    return pabPartPrices.prices();
  }

  @GetMapping(path = "/le/pab-part-prices/{designId}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get PaB part price per colorIds by design ID",
      description = "Returns the Pick a Brick price per colorIds of a LEGO design ID from "
          + "data/le/pab_part_prices.csv. Prices are formatted in euros."
  )
  @ApiResponse(responseCode = "200", description = "Prices returned")
  @ApiResponse(responseCode = "404", description = "No price found for the design ID")
  public ResponseEntity<Map<Integer, String>> pabPartPrices(
      @Parameter(name = "designId", description = "LEGO design ID (part ID)") @PathVariable String designId)
      throws IOException {
    Map<Integer, String> prices = pabPartPrices.prices(designId);
    if (prices.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(prices);
  }
}
