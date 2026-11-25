package com.bricktools.web;

import com.bricktools.color.ColorService;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Color catalogue and external color mappings.
 */
@Tag(name = "Colors", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class ColorController {

  private final ColorService colors;

  public ColorController(ColorService colors) {
    this.colors = colors;
  }

  /**
   * Get all available colors.
   */
  @GetMapping(path = "/colors", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List Colors",
      description = "Returns all Lego colors tracked by the application."
  )
  public List<Map<String, Object>> colors() throws IOException {
    return colors.colors();
  }

  /**
   * Get color mappings between different identifier systems.
   */
  @GetMapping(path = "/color-mappings", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Color Mappings",
      description = "Returns mappings that link Lego color IDs to external identifiers (BrickOwl, BrickLink, LDraw, etc.)."
  )
  public List<Map<String, Object>> colorMappings() throws IOException {
    return colors.mappings();
  }
}
