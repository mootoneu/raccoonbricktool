package com.bricktools.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {

  @Autowired
  MockMvc mvc;

  @Test
  void servesThePublicConfigurationAsAScript() throws Exception {
    mvc.perform(get("/config.js"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/javascript"))
        .andExpect(content().string(containsString("window.BrickToolsConfig")))
        .andExpect(content().string(containsString("934717")))
        .andExpect(content().string(not(containsString("apiKey"))))
        .andExpect(content().string(not(containsString("BRICKOWL_API_KEY"))));
  }
}
