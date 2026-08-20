package com.syaru.ae2craftingoptimizer.access;

/** AE2系CraftingCpuLogicを本来の完了通知順序で閉じる共通契約。 */
public interface ExactCraftingLogicAccess {
    void aco$finishExactJob(boolean successful);
}
