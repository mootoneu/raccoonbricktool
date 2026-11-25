package com.bricktools.web;

import com.bricktools.config.BrickToolsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the non-secret part of the configuration as a classic script loaded before common.js. */
@RestController
public class ConfigController {

  private final BrickToolsProperties props;
  private final ObjectMapper json = new ObjectMapper();

  public ConfigController(BrickToolsProperties props) {
    this.props = props;
  }

  @GetMapping(path = "/config.js", produces = "application/javascript")
  public String configScript() throws JsonProcessingException {
    String userId = props.brickowl() == null || props.brickowl().userId() == null
        ? "" : props.brickowl().userId().trim();
    return "window.BrickToolsConfig = "
        + json.writeValueAsString(Map.of("brickowlUserId", userId)) + ";\n";
  }
}
