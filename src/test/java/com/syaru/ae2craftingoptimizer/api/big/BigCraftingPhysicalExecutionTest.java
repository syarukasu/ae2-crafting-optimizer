package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.api.vector.*;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPhysicalExecutionTest {
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void initializeRegistryAndConfig() throws Exception {
        SharedConstants.tryDetectVersion();
        // Only registries are needed for the real AEItemKey NBT codec, not Forge networking.
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        BuiltInRegistries.bootStrap();
        var builder = new RegistryBuilder<AEKeyType>()
                .setName(ResourceLocation.fromNamespaceAndPath("ae2", "physical_execution_test_keys"))
                .disableSaving().disableSync();
        var create = RegistryBuilder.class.getDeclaredMethod("create");
        create.setAccessible(true);
        var keyTypes = (IForgeRegistry<AEKeyType>) create.invoke(builder);
        AEKeyTypesInternal.setRegistry(() -> keyTypes);
        AEKeyTypes.register(AEKeyType.items());
        var field = ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        ForgeConfigSpec spec = (ForgeConfigSpec) field.get(null);
        CommentedConfig defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        spec.setConfig(defaults);
    }

    @Test
    void savesWidePendingCountsWithoutClaimingUnreceivedOutputs() {
        BigInteger amount = BigInteger.TEN.pow(40);
        var input = AEItemKey.of(Items.OAK_LOG);
        var output = AEItemKey.of(Items.OAK_PLANKS);
        var definition = AEItemKey.of(Items.PAPER);
        UUID job = UUID.randomUUID();
        var plan = new PreparedVectorBatch(UUID.randomUUID(), job, VectorResourceMode.NETWORK_STORAGE,
                output, amount, amount, 1,
                List.of(new ExactStack(input, amount)), List.of(new ExactStack(output, amount)), List.of(),
                List.of("one"), List.of(new ExactCraftingStep("one", 1, amount,
                        List.of(new ExactCraftingInputSlot(input, 1L)))), "test-fingerprint", 1, 1);
        var transaction = PhysicalCraftingTreeTransaction.create(plan, Map.of("one",
                new PhysicalCraftingTreeTransaction.PatternAccountingIdentity("one", definition,
                        Map.of(input, amount), Map.of(output, amount))));
        var execution = new BigCraftingPhysicalExecution(amount, transaction);
        var restored = BigCraftingPhysicalExecution.load(execution.save());
        assertEquals(job, restored.jobId());
        assertEquals(amount, restored.reservedBytes());
        assertEquals(Map.of(output, amount), restored.pendingItems());
        assertTrue(restored.waitingItems().isEmpty());
        assertTrue(restored.storedItems().isEmpty());
        assertEquals(amount, restored.remainingOutput());
        assertNotEquals("COMPLETE", restored.state());
        restored.requestCancellation();
        assertEquals(restored.save(), BigCraftingPhysicalExecution.load(restored.save()).save());
    }

    @Test
    void rejectsAnUnknownPersistedExecutionSchema() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 999);
        assertThrows(IllegalArgumentException.class, () -> BigCraftingPhysicalExecution.load(tag));
    }
}
