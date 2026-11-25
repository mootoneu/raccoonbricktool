package com.bricktools.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifies that the application is not exposed on all network interfaces by default. */
@SpringBootTest
class ServerAddressTest {

  @Value("${server.address}")
  private String address;

  @Test
  void bindsToLoopbackByDefault() {
    assertThat(address).isEqualTo("127.0.0.1");
  }
}
