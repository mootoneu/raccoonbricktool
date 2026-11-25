package com.bricktools.util;

import static com.bricktools.util.Values.clean;
import static com.bricktools.util.Values.intOrZero;
import static com.bricktools.util.Values.isNumeric;
import static com.bricktools.util.Values.split;
import static com.bricktools.util.Values.toBoolean;
import static com.bricktools.util.Values.toInt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ValuesTest {

  @Test
  void cleansNullAndWhitespace() {
    assertThat(clean(null)).isEmpty();
    assertThat(clean("  abc  ")).isEqualTo("abc");
  }

  @Test
  void parsesIntegers() {
    assertThat(toInt(" 42 ")).isEqualTo(42);
    assertThat(toInt("x")).isNull();
    assertThat(toInt(null)).isNull();
    assertThat(intOrZero("x")).isZero();
    assertThat(intOrZero("7")).isEqualTo(7);
  }

  @Test
  void recognizesNumbersAndBooleans() {
    assertThat(isNumeric("-12")).isTrue();
    assertThat(isNumeric("12a")).isFalse();
    assertThat(isNumeric(null)).isFalse();
    assertThat(toBoolean("TRUE")).isTrue();
    assertThat(toBoolean("yes")).isFalse();
  }

  @Test
  void splitsSemicolonLists() {
    assertThat(split(" a ; ;b; ")).isEqualTo(List.of("a", "b"));
    assertThat(split(null)).isEmpty();
  }
}
