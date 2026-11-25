package com.bricktools.web;

import com.bricktools.part.MainPartStarsBulkRequest;
import com.bricktools.part.MainPartStarsRequest;
import com.bricktools.part.PartDefaultColorRequest;
import com.bricktools.part.PartImageService;
import com.bricktools.part.PartService;
import com.bricktools.web.dto.PartView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Part categories, main parts, part details and default colors.
 */
@Tag(name = "Parts", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class PartController {

  private final PartService partService;
  private final PartImageService partImages;

  public PartController(PartService partService, PartImageService partImages) {
    this.partService = partService;
    this.partImages = partImages;
  }

  @GetMapping(path = "/parts/categories", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List part categories",
      description = "Returns the visible Brick Tools part categories grouped by category group."
  )
  public List<Map<String, Object>> partCategories() throws IOException {
    return partService.categories();
  }

  @GetMapping(path = "/parts/categories/{categoryId}/main-parts", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List main parts",
      description = "Returns the main parts of a Brick Tools part category."
  )
  public List<Map<String, Object>> mainParts(
      @Parameter(name = "categoryId", description = "Part category ID") @PathVariable int categoryId)
      throws IOException {
    return partService.mainParts(categoryId);
  }

  @GetMapping(path = "/parts/main-parts/search", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Search main parts",
      description = "Searches the main parts by part number or name across all categories. "
          + "Returns at most 500 parts with the total number of matches."
  )
  public Map<String, Object> searchMainParts(
      @Parameter(name = "query", description = "Part number or name fragment")
      @RequestParam String query) throws IOException {
    return partService.searchMainParts(query);
  }

  @PostMapping(path = "/parts/main-parts/generate", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate main parts",
      description = "Rebuilds data/bt/main_parts.csv from the Rebrickable parts and the Brick Tools part categories."
  )
  public Map<String, Object> generateMainParts() throws IOException {
    return partService.generateMainParts();
  }

  @PutMapping(path = "/parts/main-parts/{partNumber}/stars")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update main part stars",
      description = "Sets the star rating (0 to 5) of a main part."
  )
  @ApiResponse(responseCode = "204", description = "Main part stars updated")
  public void updateMainPartStars(
      @Parameter(name = "partNumber", description = "Main part number") @PathVariable String partNumber,
      @RequestBody MainPartStarsRequest request) throws IOException {
    partService.updateStars(partNumber, request.stars());
  }

  @PutMapping(path = "/parts/main-parts/stars", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Update several main part stars",
      description = "Sets the star rating (0 to 5) of several main parts at once. Unknown part "
          + "numbers are ignored."
  )
  @ApiResponse(responseCode = "200", description = "Number of updated main parts returned")
  public Map<String, Object> updateMainPartStars(
      @RequestBody MainPartStarsBulkRequest request) throws IOException {
    int updated = partService.updateStars(request.partNumbers(), request.stars());
    return Map.of("updated", updated);
  }

  @GetMapping(path = "/parts/main-parts/{partNumber}/alternates", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List main part alternates",
      description = "Returns the part relationships and related parts of a main part."
  )
  public Map<String, Object> mainPartAlternates(
      @Parameter(name = "partNumber", description = "Main part number") @PathVariable String partNumber)
      throws IOException {
    return partService.alternates(partNumber);
  }

  @PostMapping(path = "/parts/part-color-urls/generate", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate part color URLs",
      description = "Rebuilds data/bt/part_color_url.csv from the Rebrickable inventory parts. "
          + "The first inventory row of each part/color pair with an image URL is kept."
  )
  public Map<String, Object> generatePartColorUrls() throws IOException {
    return partImages.generatePartColorUrls();
  }

  @GetMapping(path = "/parts/{partNumber}/info", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Part Information",
      description = "Returns the name, material, alternates, stars and available colors of a part. "
          + "The available colors are the part/color pairs of data/bt/part_color_url.csv and the "
          + "default color is the one used for the part image."
  )
  @ApiResponse(responseCode = "200", description = "Part information returned")
  @ApiResponse(responseCode = "404", description = "Part not found")
  public ResponseEntity<PartView> partInfo(
      @Parameter(name = "partNumber", description = "Lego part number") @PathVariable String partNumber)
      throws IOException {
    Map<String, Object> info = partService.info(partNumber);
    if (info == null) {
      return ResponseEntity.notFound().build();
    }
    int defaultColor = partImages.defaultColor(partNumber);
    info.put("defaultColorId", defaultColor < 0 ? null : defaultColor);
    info.put("colors", partImages.colorsForPart(partNumber));
    return ResponseEntity.ok(PartView.from(info));
  }

  @PutMapping(path = "/parts/{partNumber}/default-color")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Set the default part color",
      description = "Stores the color used by default for the part image in "
          + "data/bt/part_default_color.csv. The color must be available in "
          + "data/bt/part_color_url.csv."
  )
  @ApiResponse(responseCode = "204", description = "Default color updated")
  @ApiResponse(responseCode = "400", description = "The color is not available for the part")
  public void updatePartDefaultColor(
      @Parameter(name = "partNumber", description = "Lego part number") @PathVariable String partNumber,
      @RequestBody PartDefaultColorRequest request) throws IOException {
    if (request == null || request.colorId() == null) {
      throw new IllegalArgumentException("Color id is required");
    }
    partImages.setDefaultColor(partNumber, request.colorId());
  }
}
