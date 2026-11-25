package com.bricktools.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guards against the 1,000-line god controller coming back. */
class ControllerSplitTest {

  @Test
  void endpointsAreSpreadAcrossSmallDomainControllers() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
    List<Class<?>> controllers = scanner.findCandidateComponents("com.bricktools.web").stream()
        .map(BeanDefinition::getBeanClassName)
        .<Class<?>>map(name -> {
          try {
            return Class.forName(name);
          } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
          }
        })
        .toList();

    assertThat(controllers).hasSizeGreaterThanOrEqualTo(10);
    int total = 0;
    for (Class<?> controller : controllers) {
      long mappings = java.util.Arrays.stream(controller.getDeclaredMethods())
          .filter(ControllerSplitTest::isEndpoint)
          .count();
      assertThat(mappings).as("endpoints in %s", controller.getSimpleName()).isLessThanOrEqualTo(15);
      total += (int) mappings;
    }
    assertThat(total).isEqualTo(65);
  }

  private static boolean isEndpoint(Method method) {
    return method.isAnnotationPresent(GetMapping.class)
        || method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || method.isAnnotationPresent(DeleteMapping.class)
        || method.isAnnotationPresent(RequestMapping.class);
  }
}
