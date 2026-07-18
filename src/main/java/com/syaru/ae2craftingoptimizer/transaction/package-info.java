/**
 * Native Batchの状態遷移と永続Journal、再起動後の再照合を管理する。
 * 完了を証明できない取引は推測で再実行せず隔離し、アイテム複製と消失の両方を防ぐ。
 */
package com.syaru.ae2craftingoptimizer.transaction;
