package com.bricktools.web;

import com.bricktools.wishlist.NewSetService;
import com.bricktools.wishlist.WishlistRequest;
import com.bricktools.wishlist.WishlistService;
import java.io.IOException;
import java.time.LocalDate;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * New set catalogue and wishlist endpoints.
 */
@Tag(name = "Wishlist", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class WishlistController {

  private final NewSetService newSets;
  private final WishlistService wishlist;

  public WishlistController(NewSetService newSets, WishlistService wishlist) {
    this.newSets = newSets;
    this.wishlist = wishlist;
  }

  /**
   * Get recently released sets (catalog).
   */
  @GetMapping(path = "/newsets", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List New Sets",
      description = "Returns sets released in a specific year or the current year."
  )
  public Map<String, Object> newSets(@Parameter(name = "year", description = "Release year (optional)") @RequestParam(required = false) Integer year) throws IOException {
    return newSets.catalog(year == null ? LocalDate.now().getYear() : year);
  }

  /**
   * Get all wishlisted sets.
   */
  @GetMapping(path = "/wishlist", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List Wishlist",
      description = "Returns all items currently in the wishlist."
  )
  public List<Map<String, Object>> wishlistEntries() throws IOException {
    return wishlist.list();
  }

  /**
   * Get available options for wishlist creation (family members, priorities, etc.).
   */
  @GetMapping(path = "/wishlist/options", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Wishlist Options",
      description = "Returns the dropdown options used when creating a new wishlist entry."
  )
  public Map<String, Object> wishlistOptions() throws IOException {
    return wishlist.options();
  }

  /**
   * Add a new set to the wishlist.
   */
  @PostMapping(path = "/wishlist", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Add to Wishlist",
      description = "Adds a Lego set to the personal wishlist with ownership and priority information."
  )
  @ApiResponse(responseCode = "201", description = "Wishlist entry created")
  public Map<String, Integer> createWishlistEntry(@RequestBody WishlistRequest request) throws IOException {
    return Map.of("id", wishlist.create(request));
  }

  /**
   * Update an existing wishlist entry.
   */
  @PutMapping(path = "/wishlist/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Update Wishlist Entry",
      description = "Updates information for an existing wishlist item."
  )
  @ApiResponse(responseCode = "204", description = "Wishlist entry updated")
  public void updateWishlistEntry(@Parameter(name = "id", description = "Wishlist entry ID") @PathVariable int id, @RequestBody WishlistRequest request) throws IOException {
    wishlist.update(id, request);
  }

  /**
   * Remove a set from the wishlist.
   */
  @DeleteMapping(path = "/wishlist/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "Remove from Wishlist",
      description = "Removes a Lego set from the personal wishlist."
  )
  @ApiResponse(responseCode = "204", description = "Wishlist entry removed")
  public void deleteWishlistEntry(@Parameter(name = "id", description = "Wishlist entry ID") @PathVariable int id) throws IOException {
    wishlist.delete(id);
  }
}
