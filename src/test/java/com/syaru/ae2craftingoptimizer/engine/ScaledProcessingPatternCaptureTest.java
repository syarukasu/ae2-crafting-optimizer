package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScaledProcessingPatternCaptureTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        ReusableByproductAe2OracleTest.bootstrap();
        TestKeyTypes.initialize();
    }

    @Test
    void capturesActualOptionalJarWithoutLosingScaleFluidQuantumOrBinding() throws Exception {
        try (var loader = loader()) {
            AEKey water = AEFluidKey.of(Fluids.WATER), result = AEItemKey.of(Items.DIAMOND);
            var original = pattern(new GenericStack(water, 2000), new GenericStack(result, 3));
            var scaled = wrapper(loader, original, 37);
            var captured = Ae2CompiledPatternFactory.capture(scaled, null);
            assertNotNull(captured);
            assertSame(scaled, captured.details());
            var compiled = captured.compile("scaled");
            assertEquals(74000, compiled.inputs().get(0).alternatives().get(0).amount());
            assertEquals(original.getInputs()[0].getPossibleInputs()[0].amount(),
                    compiled.inputs().get(0).templateAmount());
            assertEquals(111, compiled.outputAmount(result));
            assertNotEquals(captured.fingerprint(), Ae2CompiledPatternFactory.capture(wrapper(loader, original, 38), null)
                    .fingerprint());
        }
    }

    @Test
    void rejectsPositiveWrappedOverflowBeforeCapturingInputsOrOutputs() throws Exception {
        try (var loader = loader()) {
            long amount = Long.MAX_VALUE / 2 + 1;
            AEKey raw = AEItemKey.of(Items.COAL), result = AEItemKey.of(Items.DIAMOND);
            var input = wrapper(loader, pattern(new GenericStack(raw, amount), new GenericStack(result, 1)), 5);
            var output = wrapper(loader, pattern(new GenericStack(raw, 1), new GenericStack(result, amount)), 5);
            assertTrue(input.getInputs()[0].getMultiplier() > 0, "the addon's overflow can remain positive");
            assertTrue(output.getOutputs()[0].amount() > 0);
            assertThrows(CountOverflowException.class, () -> Ae2CompiledPatternFactory.capture(input, null));
            assertThrows(CountOverflowException.class, () -> Ae2CompiledPatternFactory.capture(output, null));
        }
    }

    @Test
    void refusesWrapperAroundChangedInputSemantics() throws Exception {
        try (var loader = loader()) {
            var original = pattern(new GenericStack(AEItemKey.of(Items.COAL), 1),
                    new GenericStack(AEItemKey.of(Items.DIAMOND), 1));
            var changed = new AEProcessingPattern(original.getDefinition()) {
                @Override public IInput[] getInputs() { throw new AssertionError("must not read dynamic inputs"); }
            };
            var reasons = new ArrayList<String>();
            assertNull(Ae2CompiledPatternFactory.capture(wrapper(loader, changed, 2), null, reasons::add));
            assertEquals(java.util.List.of("unsupported_input_domain"), reasons);
        }
    }

    private static URLClassLoader loader() throws Exception {
        String file = System.getProperty("aco.eaepPatternJar");
        assumeTrue(file != null, "ExtendedAE Plus 1.5.5 contract JAR was not supplied");
        Path path = Path.of(file);
        assertTrue(Files.isRegularFile(path));
        return new URLClassLoader(new java.net.URL[] {path.toUri().toURL()},
                ScaledProcessingPatternCaptureTest.class.getClassLoader());
    }

    private static IPatternDetails wrapper(ClassLoader loader, AEProcessingPattern original, long scale) throws Exception {
        return (IPatternDetails) loader.loadClass("com.extendedae_plus.api.crafting.ScaledProcessingPattern")
                .getConstructor(AEProcessingPattern.class, long.class).newInstance(original, scale);
    }

    private static AEProcessingPattern pattern(GenericStack input, GenericStack output) {
        ItemStack definition = new ItemStack(Items.PAPER);
        ListTag inputs = new ListTag(), outputs = new ListTag();
        inputs.add(GenericStack.writeTag(input));
        outputs.add(GenericStack.writeTag(output));
        definition.getOrCreateTag().put("in", inputs);
        definition.getOrCreateTag().put("out", outputs);
        return new AEProcessingPattern(AEItemKey.of(definition));
    }
}
