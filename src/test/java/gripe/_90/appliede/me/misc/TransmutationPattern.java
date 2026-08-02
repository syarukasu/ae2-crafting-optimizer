package gripe._90.appliede.me.misc;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/** AppliedE実装コードをコピーせず、クラス名境界だけを試すテスト専用スタブ。 */
public final class TransmutationPattern implements IPatternDetails {
    @Override
    public AEItemKey getDefinition() {
        return null;
    }

    @Override
    public IInput[] getInputs() {
        return new IInput[0];
    }

    @Override
    public List<GenericStack> getOutputs() {
        return List.of();
    }
}
