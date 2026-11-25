package com.bricktools.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards against secrets being shipped inside the application artifact.
 *
 * <p>Configuration secrets are supplied through the environment (see {@code application.yml}). A {@code .env} file must
 * never be placed under {@code src/main/resources}, because Maven copies everything in that directory to
 * {@code target/classes} and into the executable jar.
 */
class ClasspathSecretsTest {

  private static final Path RESOURCES = Path.of("src", "main", "resources");

  @Test
  void noDotEnvFilesUnderResources() throws IOException {
    List<Path> secrets;
    try (Stream<Path> paths = Files.walk(RESOURCES)) {
      secrets = paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(".env"))
          .toList();
    }
    assertThat(secrets).as("Secrets found in the classpath resources").isEmpty();
  }

  @Test
  void noDotEnvOnClasspath() {
    assertThat(getClass().getClassLoader().getResource(".env"))
        .as("A .env resource would be packaged into the jar")
        .isNull();
  }
}
