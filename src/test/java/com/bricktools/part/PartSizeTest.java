package com.bricktools.part;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PartSizeTest {

  @TempDir
  Path dir;

  @Test
  void parsesThreeDimensionsFromTheName() {
    assertThat(PartSize.parse("Brick 1 x 6 x 5", "Brick")).isEqualTo("1x6x5");
    assertThat(PartSize.parse("Aero Tube 32 x 3 x 2 1/3", "Other")).isEqualTo("32x3x2 1/3");
    assertThat(PartSize.parse("Technic Beam 1 x 2 x 0.5", "Technic")).isEqualTo("1x2x0.5");
  }

  @Test
  void defaultsTheMissingHeightFromTheCategory() {
    assertThat(PartSize.parse("Brick 2 x 4", "Brick")).isEqualTo("2x4x1");
    assertThat(PartSize.parse("Plate 2 x 4", "Plate")).isEqualTo("2x4x1/3");
    assertThat(PartSize.parse("Tile 2 x 4", "Tile")).isEqualTo("2x4x1/3");
    assertThat(PartSize.parse("Baseplate 32 x 32", "Plate")).isEqualTo("32x32x1/3");
    assertThat(PartSize.parse("Brick 2 x 4", "Tiles")).isEqualTo("2x4x1/3");
  }

  @Test
  void leavesTheSizeBlankWhenTheNameHasNone() {
    assertThat(PartSize.parse("Duplo Brick", "Brick")).isEmpty();
    assertThat(PartSize.parse("", "Brick")).isEmpty();
    assertThat(PartSize.parse(null, "Brick")).isEmpty();
  }

  @Test
  void readsTheSizeOfPartsWrittenByTheGenerator() {
    assertThat(PartSize.parse("1 x 6 x 2 with Shutters", "Brick")).isEqualTo("1x6x2");
    assertThat(PartSize.parse("Bar 1 x 8 with Brick 1 x 2 Curved", "Other")).isEqualTo("1x8x1");
  }

  @Test
  void indexReturnsTheStoredSizesByPartNumber() throws IOException {
    Files.writeString(dir.resolve("main_parts.csv"),
        "PartNumber,Name,Size,CategoryId,Material,HasAlternates,Stars\n"
            + "3001,Brick 2 x 4,2x4x1,11,Plastic,false,0\n"
            + "3002,Brick, ,11,Plastic,false,0\n");

    Map<String, String> sizes = PartSize.index(dir.resolve("main_parts.csv"));

    assertThat(sizes).containsOnly(Map.entry("3001", "2x4x1"));
    assertThat(PartSize.index(dir.resolve("missing.csv"))).isEmpty();
  }
}
