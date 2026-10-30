package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.google.common.collect.BiMap;
import com.syaru.ae2craftingoptimizer.access.CountedPatternBufferSlot;
import com.syaru.ae2craftingoptimizer.api.batch.CountedPatternBatchTarget;
import com.syaru.ae2craftingoptimizer.optimization.GTCountedInputPush;
import java.math.BigInteger;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine", remap = false)
public abstract class GTBufferCountedTargetMixin implements CountedPatternBatchTarget {
    @Shadow @Final private BiMap<IPatternDetails, ?> detailsSlotMap;

    @Override public long aco$countedExecutionLimit(IPatternDetails pattern, Map<AEKey, BigInteger> inputs, long offered) {
        // Issue #161: subclasses may change acceptance or retain inputs; require the audited provider.
        if (!getClass().getName().equals("com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine")) return -1;
        if (!GTCountedInputPush.supported(pattern)) return -1;
        var slot = detailsSlotMap.get(pattern);
        return slot instanceof CountedPatternBufferSlot counted ? counted.aco$countedHeadroom(inputs, offered) : -1;
    }
}
