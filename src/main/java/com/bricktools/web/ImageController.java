package com.bricktools.web;

import com.bricktools.other.OtherImageService;
import com.bricktools.part.PartImageService;
import com.bricktools.setimg.SetImageBackfill;
import com.bricktools.setimg.SetImageService;
import com.bricktools.theme.ThemeImageService;
import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Image endpoints for sets, themes, vendors, people and parts.
 */
@Tag(name = "Images", description = "Endpoints exposed by the Brick Tools application")
@RestController
@RequestMapping("/api")
public class ImageController {

  private final SetImageService setImages;
  private final SetImageBackfill backfill;
  private final ThemeImageService themeImages;
  private final OtherImageService otherImages;
  private final PartImageService partImages;

  public ImageController(SetImageService setImages, SetImageBackfill backfill, ThemeImageService themeImages, OtherImageService otherImages, PartImageService partImages) {
    this.setImages = setImages;
    this.backfill = backfill;
    this.themeImages = themeImages;
    this.otherImages = otherImages;
    this.partImages = partImages;
  }

  /**
   * Start the set image backfill process.
   */
  @PostMapping(path = "/setimg/backfill", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Start Set Image Backfill",
      description = "Initiates a background job to download missing set images. Returns progress information."
  )
  @ApiResponse(responseCode = "202", description = "Backfill started")
  @ApiResponse(responseCode = "409", description = "A backfill is already running")
  public ResponseEntity<SetImageBackfill.Progress> startSetImageBackfill() throws IOException {
    HttpStatus status = backfill.start() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(backfill.progress());
  }

  /**
   * Get the current progress of the set image backfill job.
   */
  @GetMapping(path = "/setimg/backfill", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get Backfill Status",
      description = "Returns the current progress of the background image backfill job."
  )
  public SetImageBackfill.Progress setImageBackfillStatus() {
    return backfill.progress();
  }

  /**
   * Download a set image for the given set number.
   */
  @GetMapping(path = "/setimg/{setNumber}", produces = "image/*")
  @Operation(
      summary = "Get Set Image",
      description = "Returns the image for a Lego set."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "Set image not found")
  public ResponseEntity<Resource> setImage(
      @Parameter(name = "setNumber", description = "Lego set number") @PathVariable String setNumber)
      throws IOException {
    return ImageResponse.of(setImages.find(setNumber), setImages::contentType, setImages::resource);
  }

  /**
   * Download a theme image by its internal name.
   */
  @GetMapping(path = "/themeimg/{imageName:.+}", produces = "image/*")
  @Operation(
      summary = "Get Theme Image",
      description = "Returns the image for a Lego theme."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "Theme image not found")
  public ResponseEntity<Resource> themeImage(
      @Parameter(name = "imageName", description = "Internal name of the theme image") @PathVariable String imageName)
      throws IOException {
    return ImageResponse.of(themeImages.find(imageName), themeImages::contentType, themeImages::resource);
  }

  /**
   * Download a vendor image by its internal name.
   */
  @GetMapping(path = "/vendorimg/{imageName:.+}", produces = "image/*")
  @Operation(
      summary = "Get Vendor Image",
      description = "Returns the logo/image for a set vendor."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "Vendor image not found")
  public ResponseEntity<Resource> vendorImage(
      @Parameter(name = "imageName", description = "Internal name of the vendor image") @PathVariable String imageName)
      throws IOException {
    return ImageResponse.of(otherImages.find("vendor", imageName), otherImages::contentType, otherImages::resource);
  }

  /**
   * Download a people image by its internal name.
   */
  @GetMapping(path = "/peopleimg/{imageName:.+}", produces = "image/*")
  @Operation(
      summary = "Get People Image",
      description = "Returns the image for a person (family member, friend, etc.)."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "People image not found")
  public ResponseEntity<Resource> peopleImage(
      @Parameter(name = "imageName", description = "Internal name of the people image") @PathVariable String imageName)
      throws IOException {
    return ImageResponse.of(otherImages.find("people", imageName), otherImages::contentType, otherImages::resource);
  }

  @GetMapping(path = "/parts/{partNumber}/image", produces = "image/*")
  @Operation(
      summary = "Get Part Image",
      description = "Returns the image of a part color (or of the first available color when colorId "
          + "is omitted), downloading it from the Rebrickable CDN into data/other/partimg/{colorId}/ "
          + "when it is not stored yet. When the part/color pair has no image URL, another color of "
          + "the part is used; when the part has no image at all, the missing part image is returned."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "Part image and missing image not found")
  public ResponseEntity<Resource> partImage(
      @Parameter(name = "partNumber", description = "Lego part number") @PathVariable String partNumber,
      @Parameter(name = "colorId", description = "Lego color ID; the first available color is used when omitted")
      @RequestParam(required = false) Integer colorId)
      throws IOException, InterruptedException {
    return ImageResponse.of(partImages.ensureWithFallback(partNumber, colorId),
        partImages::contentType, partImages::resource);
  }

  @GetMapping(path = "/parts/items/{itemNumber}/image", produces = "image/*")
  @Operation(
      summary = "Get Part Image by Item Number",
      description = "Returns the image matching a Lego item number (element id), using the part "
          + "and color stored in data/bt/part_color_url.csv."
  )
  @ApiResponse(responseCode = "200", description = "Image returned")
  @ApiResponse(responseCode = "404", description = "Part image not found")
  public ResponseEntity<Resource> partImageByItemNumber(
      @Parameter(name = "itemNumber", description = "Lego item number (element id)")
      @PathVariable String itemNumber)
      throws IOException, InterruptedException {
    return ImageResponse.of(partImages.ensureByItemNumber(itemNumber), partImages::contentType, partImages::resource);
  }
}
