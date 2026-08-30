package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Ae2CraftingShadowValidatorDiagnosticsTest {
    @AfterEach
    void resetDiagnostics() {
        Ae2CraftingShadowValidator.resetDiagnostics();
    }

    @Test
    void boundsUniqueShadowSkipKeys() {
        // 4,096件の上限を越える一意キーを投入し、世代増加時も索引が無制限に増えないことを確認する。
        for (int index = 0; index < 8_193; index++) {
            Ae2CraftingShadowValidator.rememberSkipKey("skip-" + index);
        }

        assertTrue(Ae2CraftingShadowValidator.loggedSkipKeyCount() <= 4_096);
    }
}
