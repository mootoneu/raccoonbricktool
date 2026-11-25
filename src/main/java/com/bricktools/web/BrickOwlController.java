package com.bricktools.web;

import com.bricktools.brickowl.BrickOwlService;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * BrickOwl statistics, updates, orders and parts.
 */
@Tag(name = "BrickOwl", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class BrickOwlController {

  private final BrickOwlService brickOwl;

  public BrickOwlController(BrickOwlService brickOwl) {
    this.brickOwl = brickOwl;
  }

  /**
   * Get statistics about BrickOwl integration.
   */
  @GetMapping(path = "/brickowl/stats", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get BrickOwl Stats",
      description = "Returns cached statistics from BrickOwl."
  )
  public Map<String, Object> brickOwlStats() throws IOException {
    return brickOwl.stats();
  }

  /**
   * Trigger a refresh of BrickOwl data.
   */
  @PostMapping(path = "/brickowl/update", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update BrickOwl Data",
      description = "Starts downloading the latest data from BrickOwl."
  )
  public Map<String, Object> brickOwlUpdate() throws IOException {
    return brickOwl.update();
  }

  /**
   * Get list of all BrickOwl orders.
   */
  @GetMapping(path = "/brickowl/orders", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List BrickOwl Orders",
      description = "Returns a list of all BrickOwl catalog orders."
  )
  public List<Map<String, String>> brickOwlOrders() throws IOException {
    return brickOwl.orders();
  }

  /**
   * Get the items in a specific BrickOwl order.
   */
  @GetMapping(path = "/brickowl/orders/{orderId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Order Items",
      description = "Returns all items for a specific BrickOwl order."
  )
  public List<Map<String, String>> brickOwlItems(@Parameter(name = "orderId", description = "BrickOwl order ID") @PathVariable String orderId) throws IOException {
    return brickOwl.items(orderId);
  }

  /**
   * Get all parts from BrickOwl.
   */
  @GetMapping(path = "/brickowl/parts", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List Parts",
      description = "Returns a list of all Lego parts tracked by BrickOwl."
  )
  public List<Map<String, Object>> brickOwlParts() throws IOException {
    return brickOwl.parts();
  }
}
