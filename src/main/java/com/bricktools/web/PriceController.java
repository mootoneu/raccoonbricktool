package com.bricktools.web;

import com.bricktools.brickonomics.BrickonomicsService;
import com.bricktools.brickwatch.BrickWatchService;
import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Official LEGO price lookups.
 */
@Tag(name = "Prices", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class PriceController {

  private final BrickWatchService brickWatch;
  private final BrickonomicsService brickonomics;

  public PriceController(BrickWatchService brickWatch, BrickonomicsService brickonomics) {
    this.brickWatch = brickWatch;
    this.brickonomics = brickonomics;
  }

  /**
   * Fetch the official Lego price for a set from BrickWatch.
   */
  @GetMapping(path = "/brickwatch/sets/{setNumber}/price", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Set Price from BrickWatch",
      description = "Fetches the official Lego price for a specific set number."
  )
  public Map<String, Object> setPrice(@PathVariable(name = "setNumber") String setNumber) throws IOException, InterruptedException {
    return brickWatch.price(setNumber);
  }

  /**
   * Fetch the official Lego price for a set from Brickonomics.
   */
  @PostMapping(path = "/brickonomics/sets/{setNumber}/price", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Set Price from Brickonomics",
      description = "Fetches the official Lego price for a specific set number from brickonomics.com."
  )
  public Map<String, Object> fetchBrickonomicsPrice(@PathVariable(name = "setNumber") String setNumber)
      throws IOException, InterruptedException {
    return brickonomics.fetchPrice(setNumber);
  }
}
