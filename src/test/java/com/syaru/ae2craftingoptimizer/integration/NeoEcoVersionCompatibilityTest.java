package com.syaru.ae2craftingoptimizer.integration;

import static com.syaru.ae2craftingoptimizer.integration.NeoEcoVersionCompatibility.ExecutionProfile.NEO_ECO_20_3;
import static com.syaru.ae2craftingoptimizer.integration.NeoEcoVersionCompatibility.ExecutionProfile.NEO_ECO_20_4;
import static com.syaru.ae2craftingoptimizer.integration.NeoEcoVersionCompatibility.ExecutionProfile.NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NeoEcoVersionCompatibilityTest {
    @Test
    void recognizesBothSupportedMinorLines() {
        assertEquals(NEO_ECO_20_3, NeoEcoVersionCompatibility.executionProfile("20.3.0"));
        assertEquals(NEO_ECO_20_3, NeoEcoVersionCompatibility.executionProfile("20.3.9"));
        assertEquals(NEO_ECO_20_4, NeoEcoVersionCompatibility.executionProfile("20.4.0"));
        assertEquals(NEO_ECO_20_4, NeoEcoVersionCompatibility.executionProfile("20.4.7-beta"));
    }

    @Test
    void rejectsAbsentAndUnverifiedApiLines() {
        assertEquals(NONE, NeoEcoVersionCompatibility.executionProfile(null));
        assertEquals(NONE, NeoEcoVersionCompatibility.executionProfile(""));
        assertEquals(NONE, NeoEcoVersionCompatibility.executionProfile("20.2.3"));
        assertEquals(NONE, NeoEcoVersionCompatibility.executionProfile("20.5.0"));
        assertEquals(NONE, NeoEcoVersionCompatibility.executionProfile("21.1.1"));
    }
}
