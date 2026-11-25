package com.rephelper.settlement;

import com.saponi.guardrails.SddArchitectureRules;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ArchitectureGuardrailsTest {

    @Test
    void serviceShouldComplyWithAllSddAndArchUnitGuardrails() {
        SddArchitectureRules.enforceAll(
                "com.rephelper.settlement",
                Path.of("docs/specs")
        );
    }
}
