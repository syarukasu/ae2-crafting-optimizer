package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import com.ae2vm.addon.nativeengine.NativeVmResult;
import com.ae2vm.addon.nativeengine.VmQuantities;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GraphPlanRevisionTest {
    @BeforeAll static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void wideAdapterKeepsOrderStartRevisionWhenNetworkChangesDuringCalculation() {
        var owner=new VmBigIntegerAccounting(); var grid=mock(IGrid.class); var level=mock(Level.class);
        var context=owner.orderContext(grid,level);
        long patterns=ProviderPatternGenerationTracker.generation(),recipes=RecipeGenerationTracker.generation();
        ProviderPatternGenerationTracker.nativeIndexChanged(); RecipeGenerationTracker.invalidate();
        var root=AEItemKey.of(Items.DIAMOND); var raw=AEItemKey.of(Items.IRON_INGOT);
        var amount=BigInteger.ONE.shiftLeft(64); var pattern=mock(IPatternDetails.class);
        var quantities=new VmQuantities(root,amount,Map.of("graph-0",amount),Map.of(raw,amount),Map.of(),Map.of(),
                amount,Map.of(),false,1,1,BigInteger.ZERO);
        var result=owner.wideResult(grid,level,new NativeVmResult(quantities,Map.of("graph-0",pattern),amount,true),context);
        var exact=(VmCalculatedCraftingPlan)Ae2CraftingPlanSidecars.metadata(result).orElseThrow();
        assertEquals(patterns,exact.patternGeneration()); assertEquals(recipes,exact.recipeGeneration());
        assertEquals(amount,exact.exactView().orElseThrow().exactRequestedAmount());
    }
}
