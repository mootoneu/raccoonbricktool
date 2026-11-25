package com.bricktools.web;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Shared response builder for image endpoints: a typed 200 response, or 404 when no image exists. */
public final class ImageResponse {

  private ImageResponse() {}

  public static ResponseEntity<Resource> of(Optional<Path> image,
                                            Function<Path, String> contentType,
                                            Function<Path, Resource> resource) {
    return image
        .map(path -> ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType.apply(path)))
            .body(resource.apply(path)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
