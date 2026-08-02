package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.access.DelegatingMEInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.MekanismCachedRecipeAccess;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.client.NumberEntryWidgetAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mixinクラスを通常コードから直接ロードしてIllegalClassLoadErrorを起こさないための境界試験。 */
class MixinClassLoadBoundaryTest {
    @Test
    void accessorsExposeOnlyNonMixinRuntimeContracts() {
        assertTrue(DelegatingMEInventoryAccess.class
                .isAssignableFrom(DelegatingMEInventoryAccessor.class));
        assertTrue(ExtendedAePlusBigIntegerCellInventoryAccess.class
                .isAssignableFrom(ExtendedAePlusBigIntegerCellInventoryAccessor.class));
        assertTrue(NetworkStorageMountsAccess.class
                .isAssignableFrom(NetworkStorageMountsAccessor.class));
        assertTrue(MekanismCachedRecipeAccess.class
                .isAssignableFrom(MekanismCachedRecipeAccessor.class));
        assertTrue(NumberEntryWidgetAccess.class
                .isAssignableFrom(NumberEntryWidgetAccessor.class));
    }

    @Test
    void productionCodeOutsideMixinPackageDoesNotImportMixinTypes() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        String forbiddenImport = "import com.syaru.ae2craftingoptimizer.mixin.";
        List<Path> violations;
        try (var files = Files.walk(sourceRoot)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    // Mixin定義同士の参照はこの境界試験の対象外にする。
                    .filter(path -> !path.toString().replace('\\', '/').contains(
                            "/com/syaru/ae2craftingoptimizer/mixin/"))
                    .filter(path -> readUnchecked(path).contains(forbiddenImport))
                    .toList();
        }
        assertEquals(List.of(), violations,
                "通常コードが登録済みMixinクラスを直接importしています");
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("試験対象ソースを読み込めません: " + path, failure);
        }
    }
}
