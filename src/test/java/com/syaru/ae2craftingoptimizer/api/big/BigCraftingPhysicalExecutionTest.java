package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.core.definitions.AEItems;
import appeng.items.misc.MissingContentItem;
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
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.LoadingModList;
import java.nio.file.Path;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess;
import org.junit.jupiter.api.AfterAll;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPhysicalExecutionTest {
    @BeforeAll
    static void initializeRegistryAndConfig() throws Exception {
        com.syaru.ae2craftingoptimizer.TestRegistries.initialize();
        var keyTypes = new RegistryBuilder<AEKeyType>(ResourceKey.createRegistryKey(
                ResourceLocation.fromNamespaceAndPath("ae2", "physical_execution_test_keys")))
                .disableRegistrationCheck().create();
        AEKeyTypesInternal.setRegistry(keyTypes);
        ACORegistryAccess.install(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        AEKeyTypes.register(AEKeyType.items());
        var field = ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        ModConfigSpec spec = (ModConfigSpec) field.get(null);
        CommentedConfig defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        // NeoForgeのsealedな設定ラッパーをメモリ内で用意し、実configへ書き込まない。
        var loadedConfig = Class.forName("net.neoforged.fml.config.LoadedConfig")
                .getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
        loadedConfig.setAccessible(true);
        spec.acceptConfig((IConfigSpec.ILoadedConfig) loadedConfig.newInstance(defaults, null, null));
    }

    @AfterAll
    static void releaseRegistryProvider() {
        ACORegistryAccess.clear();
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
