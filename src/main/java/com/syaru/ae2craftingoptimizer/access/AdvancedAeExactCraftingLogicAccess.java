package com.syaru.ae2craftingoptimizer.access;

/** Advanced AE標準の完了・取消通知経路へExact Jobを戻すための最小Invoker契約。 */
public interface AdvancedAeExactCraftingLogicAccess {
    void aco$finishExactJob(boolean successful);
}
