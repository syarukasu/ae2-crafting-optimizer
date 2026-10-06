package com.syaru.ae2craftingoptimizer.engine;

import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import java.util.*;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** #218: actual AE2 calculation, real processing patterns, and the production VM capture. */
class NativeDemandAe2OracleTest {
    @BeforeAll static void bootstrap() throws Exception {
        ReusableByproductAe2OracleTest.bootstrap();
        com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes.initialize();
    }

    @Test @Timeout(30)
    void fundedEightOutputMaterialWorksDirectlyAndInsideSharedParents() throws Exception {
        var material = key(3);
        var raw = key(4);
        var producer = pattern(List.of(new GenericStack(raw, 1)), List.of(new GenericStack(material, 8)));
        var left = pattern(List.of(new GenericStack(material, 7)), List.of(new GenericStack(key(1), 1)));
        var right = pattern(List.of(new GenericStack(material, 9)), List.of(new GenericStack(key(2), 1)));
        var root = pattern(List.of(new GenericStack(key(1), 2), new GenericStack(key(2), 3),
                new GenericStack(material, 2)), List.of(new GenericStack(key(0), 1)));
        var recipes = Map.<AEKey, List<IPatternDetails>>of(key(0), List.of(root), key(1), List.of(left),
                key(2), List.of(right), material, List.of(producer));
        var service = mock(ICraftingService.class);
        when(service.getCraftingFor(any())).thenAnswer(call -> recipes.getOrDefault(call.getArgument(0), List.of()));
        for (long available : new long[]{0, 1, 2, 7, 8, 9, 43, 10000}) {
            var stock = Map.of(raw, 1_250_000_000L, material, available);
            var direct = ReusableByproductAe2OracleTest.oracleService(service, material, 1_000_000_000L,
                    stock, CalculationStrategy.REPORT_MISSING_ITEMS);
            org.junit.jupiter.api.Assertions.assertFalse(direct.simulation());
            var nested = ReusableByproductAe2OracleTest.oracleService(service, key(0), 237,
                    stock, CalculationStrategy.REPORT_MISSING_ITEMS);
            org.junit.jupiter.api.Assertions.assertFalse(nested.simulation());
        }
    }

    @Test @Timeout(60)
    void preparedAlternativeSharedDagsMatchActualAe2() throws Exception {
        var random = new Random(0x218FA15EL);
        for (int trial = 0; trial < 240; trial++) {
            int depth = 3 + random.nextInt(3);
            Map<AEKey, List<IPatternDetails>> recipes = new HashMap<>();
            for (int i = 0; i < depth; i++) {
                var producers = new ArrayList<IPatternDetails>();
                for (int route = 0; route < 1 + (i % 2); route++) {
                    var in = new ArrayList<GenericStack>();
                    in.add(new GenericStack(key(i + 1), 1 + random.nextInt(3)));
                    if (i + 2 <= depth) in.add(new GenericStack(key(i + 2), 1 + random.nextInt(3)));
                    producers.add(pattern(in, List.of(new GenericStack(key(i), 1 + random.nextInt(8)))));
                }
                recipes.put(key(i), producers);
            }
            Map<AEKey, Long> stock = new HashMap<>();
            stock.put(key(depth), trial % 3 == 0 ? 100_000L : (long) random.nextInt(31));
            for (int i = 1; i < depth; i++) stock.put(key(i), (long) random.nextInt(17));
            var service = mock(ICraftingService.class);
            when(service.getCraftingFor(any())).thenAnswer(call -> recipes.getOrDefault(call.getArgument(0), List.of()));
            try {
                ReusableByproductAe2OracleTest.oracleService(service, key(0), 1 + random.nextInt(6), stock,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            } catch (AssertionError failure) {
                throw new AssertionError("prepared alternative trial=" + trial + " stock=" + stock, failure);
            }
        }
    }

    @Test void seededNativeSharedDagsMatchActualAe2QuantitiesAndBytes() throws Exception {
        var random = new Random(0x218L);
        for (int trial = 0; trial < 160; trial++) {
            int depth = 3 + random.nextInt(5);
            Map<AEKey, List<IPatternDetails>> recipes = new HashMap<>();
            for (int i = 0; i < depth; i++) {
                var in = new ArrayList<GenericStack>();
                in.add(new GenericStack(key(i + 1), 1 + random.nextInt(3)));
                if (i + 2 <= depth) in.add(new GenericStack(key(i + 2), 1 + random.nextInt(3)));
                var out = new ArrayList<GenericStack>();
                out.add(new GenericStack(key(i), 1 + random.nextInt(3)));
                if (random.nextInt(4) == 0) out.add(new GenericStack(key(1000 + i), 2));
                var pattern = pattern(in, out);
                for (var stack : out) recipes.put(stack.what(), List.of(pattern));
            }
            Map<AEKey, Long> stock = new HashMap<>();
            if (trial % 3 != 0) stock.put(key(depth), trial % 3 == 1 ? 17L : 100_000L);
            if (trial % 5 == 0) stock.put(key(2), 7L);
            var service = mock(ICraftingService.class);
            when(service.getCraftingFor(any())).thenAnswer(call -> recipes.getOrDefault(call.getArgument(0), List.of()));
            ReusableByproductAe2OracleTest.oracleService(service, key(0), 1 + random.nextInt(12), stock, CalculationStrategy.REPORT_MISSING_ITEMS);
        }
    }

    private static AEKey key(int i) {
        var item = new ItemStack(Items.IRON_INGOT);
        item.getOrCreateTag().putInt("native_demand", i);
        return AEItemKey.of(item);
    }
    private static IPatternDetails pattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        var encoded = new ItemStack(Items.PAPER);
        var in = new ListTag(); inputs.forEach(stack -> in.add(GenericStack.writeTag(stack)));
        var out = new ListTag(); outputs.forEach(stack -> out.add(GenericStack.writeTag(stack)));
        encoded.getOrCreateTag().put("in", in); encoded.getOrCreateTag().put("out", out);
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }
}
