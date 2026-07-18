package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.v2.NativeBatchReceiptStore;
import com.syaru.ae2craftingoptimizer.integration.AdvancedAePatternProviderAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class PatternProviderReceiptResolver {
    private PatternProviderReceiptResolver() {
    }

    @Nullable
    public static NativeBatchReceiptStore fromContext(PatternBatchContext context) {
        return context.provider() instanceof NativeBatchReceiptStore store ? store : null;
    }

    @Nullable
    public static NativeBatchReceiptStore fromRecord(ServerLevel level, CompoundTag adapterData) {
        if (!validMetadata(adapterData)) {
            return null;
        }
        BlockPos providerPos = BlockPos.of(adapterData.getLong("providerPos"));
        if (!level.isLoaded(providerPos)) {
            return null;
        }
        Direction providerSide = Direction.from3DDataValue(adapterData.getByte("providerSide"));
        BlockEntity host = level.getBlockEntity(providerPos);
        if (host == null) {
            return null;
        }

        NativeBatchReceiptStore blockStore = logicStore(host);
        if (blockStore != null) {
            return blockStore;
        }
        if (host instanceof IPartHost partHost) {
            IPart part = partHost.getPart(providerSide);
            return logicStore(part);
        }
        return null;
    }

    public static boolean validMetadata(CompoundTag data) {
        int side = data.getByte("providerSide");
        return data.contains("providerPos", Tag.TAG_LONG)
                && data.contains("providerSide", Tag.TAG_BYTE)
                && side >= 0
                && side < Direction.values().length;
    }

    @Nullable
    private static NativeBatchReceiptStore logicStore(@Nullable Object host) {
        if (host == null) {
            return null;
        }
        Object logic;
        if (host instanceof PatternProviderLogicHost providerHost) {
            logic = providerHost.getLogic();
        } else if (ModList.get().isLoaded("advanced_ae")) {
            return AdvancedAePatternProviderAccess.receiptStore(host);
        } else {
            return null;
        }
        return logic instanceof NativeBatchReceiptStore store ? store : null;
    }
}
