package com.example.ai01.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class UtilityTest {

    @Test
    void writeDownRules() {

        var data = Map.of("LAYER-001", "controller may only call service",
                "LAYER-002", "service may only call repository",
                "LAYER-003", "repository must not depend on service",
                "NAME-001", "Controller classes must end with `Controller`",
                "NAME-002", "Service classes must end with `Service`",
                "NAME-003", "Repository classes must end with `Repository`",
                "DEP-001", "`HttpServletRequest` must not be used in the service layer",
                "DEP-002", "Field injection (`@Autowired` on fields) is forbidden; use constructor injection",
                "PATTERN-001", "All exceptions must be handled via `@ControllerAdvice`",
                "PATTERN-002", "All entities must be annotated with `@Entity` and `@Table`");

        Assertions.assertTrue(Utility.writeDownRules(data, "C:\\Users\\A\\OneDrive\\Documents\\agent\\md\\architecture-rules.md"));
    }

}