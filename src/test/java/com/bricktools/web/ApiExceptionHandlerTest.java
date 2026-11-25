package com.bricktools.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;
  private boolean additive;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    additive = logger.isAdditive();
    logger.setAdditive(false);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    appender.stop();
    logger.setAdditive(additive);
  }

  @Test
  void ioFailureReturnsGenericMessageAndLogsDetail() {
    ResponseEntity<Map<String, String>> response =
        handler.ioFailure(new IOException("C:\\secret\\data\\file.csv (No such file)"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("error", "Internal server error");
    assertThat(response.getBody().values()).noneMatch(value -> value.contains("C:\\"));

    assertThat(appender.list).anySatisfy(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getFormattedMessage()).contains("I/O failed");
      assertThat(event.getThrowableProxy().getMessage()).contains("C:\\secret");
    });
  }

  @Test
  void upstreamFailureReturnsGenericMessageAndLogsDetail() {
    ResponseEntity<Map<String, String>> response =
        handler.upstreamFailure(new RestClientException("https://internal.example/secret-token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody()).containsEntry("error", "External service error");

    assertThat(appender.list).anySatisfy(event -> {
      assertThat(event.getLevel()).isEqualTo(Level.ERROR);
      assertThat(event.getThrowableProxy().getMessage()).contains("internal.example");
    });
  }

  @Test
  void badRequestKeepsValidationMessage() {
    ResponseEntity<Map<String, String>> response = handler.badRequest(new IllegalArgumentException("Invalid drawer"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("error", "Invalid drawer");
  }
}
