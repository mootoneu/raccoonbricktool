package com.bricktools.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class ImageResponseTest {

  @Test
  void returnsTheImageWithItsContentType() {
    Path image = Path.of("picture.png");

    ResponseEntity<?> response = ImageResponse.of(Optional.of(image),
        path -> "image/png", FileSystemResource::new);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    assertThat(response.getBody()).isInstanceOf(FileSystemResource.class);
  }

  @Test
  void returnsNotFoundWhenThereIsNoImage() {
    ResponseEntity<?> response = ImageResponse.of(Optional.empty(),
        path -> "image/png", FileSystemResource::new);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNull();
  }
}
