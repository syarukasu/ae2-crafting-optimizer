package com.syaru.ae2craftingoptimizer.access;

/**
 * Advanced AEの時間Trackerへ、会計と独立したVector表示進捗を渡すPort。
 */
public interface AqeVectorElapsedTimeDisplay {
    void aco$setVectorDisplay(
            long startItemCount,
            long remainingItemCount,
            float progress);

    void aco$clearVectorDisplay();
}
