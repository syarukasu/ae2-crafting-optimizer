/**
 * prepare、機械側受理、AE2側会計、commit、復旧を分離したV2 Batch API。
 * Adapterは実際に所有した完全実行数だけを返し、部分受理や曖昧な結果では必ずFallbackする。
 */
package com.syaru.ae2craftingoptimizer.api.batch.v2;
